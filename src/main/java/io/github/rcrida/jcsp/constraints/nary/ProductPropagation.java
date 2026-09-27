package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoublePredicate;

/**
 * Product-constraint propagation shared by {@link ProductConstraint} (constant bound) and
 * {@link ProductVariableConstraint} (variable target), which previously duplicated the same
 * interval algebra and the same restriction to strictly positive factors.
 * <p>
 * Two passes, in increasing strength. {@link #range} is signed interval multiplication, correct for
 * any combination of factor signs, where the earlier {@code product of the mins}/{@code product of
 * the maxes} shortcut was only valid when every factor was positive and so gave up entirely
 * otherwise. {@link #eqCoverage} is genuine generalized arc consistency for {@code EQ} by
 * enumerating the factors' actual values, for the case interval reasoning cannot help at all: a
 * domain whose <em>hull</em> straddles zero even though the domain itself excludes it. {@code
 * LowAutocorrelation-015}'s 105 {@code eq(y, mul(x1,x2))} constraints over {@code {-1, 1}} are
 * exactly that shape -- dividing by the complementary factor's hull {@code [-1,1]} is unbounded, so
 * no bounds pass can narrow anything, while enumerating four combinations settles it. Mirrors
 * {@link ExtremumPropagation}'s own EQ-coverage fallback, added for the same gapped-domain reason,
 * and {@code SubsetSumCoveragePropagation}'s {@code eligible}-then-compute shape.
 */
final class ProductPropagation {

    /**
     * Ceiling on the factor combinations {@link #eqCoverage} will enumerate. Coverage is exact GAC
     * but costs the product of the factors' domain sizes, so above this the caller falls back to
     * {@link #range}'s bounds reasoning.
     */
    static final long MAX_COVERAGE_COMBINATIONS = 50_000;

    private ProductPropagation() {
    }

    /**
     * {@code {lo, hi}} of the product of the factor intervals {@code [mins[i], maxs[i]]}, folding
     * one factor at a time and keeping the extremes of the four corner products -- exact interval
     * multiplication, so correct whatever the signs. Reduces to {@code {product of mins, product of
     * maxes}} when every factor is positive, which is what the two callers relied on before.
     */
    static double[] range(double[] mins, double[] maxs) {
        return rangeExcluding(mins, maxs, -1);
    }

    /**
     * {@link #range} over every factor except {@code skip} -- the complementary product a single
     * factor is divided by to narrow it. {@code skip} of {@code -1} excludes nothing.
     */
    static double[] rangeExcluding(double[] mins, double[] maxs, int skip) {
        double lo = 1.0;
        double hi = 1.0;
        for (int i = 0; i < mins.length; i++) {
            if (i == skip) continue;
            double a = lo * mins[i];
            double b = lo * maxs[i];
            double c = hi * mins[i];
            double d = hi * maxs[i];
            lo = Math.min(Math.min(a, b), Math.min(c, d));
            hi = Math.max(Math.max(a, b), Math.max(c, d));
        }
        return new double[]{lo, hi};
    }

    /** Whether {@code range} contains zero, which makes dividing by it unbounded and so unusable. */
    static boolean straddlesZero(double[] range) {
        return range[0] <= 0.0 && range[1] >= 0.0;
    }

    /**
     * {@code {lo, hi}} of {@code [numeratorLo, numeratorHi] / [denominatorLo, denominatorHi]}, by
     * the same corner argument {@link #range} uses. The caller must have rejected a denominator
     * that {@link #straddlesZero}, since the quotient is then unbounded.
     */
    static double[] divide(double numeratorLo, double numeratorHi, double denominatorLo, double denominatorHi) {
        double a = numeratorLo / denominatorLo;
        double b = numeratorLo / denominatorHi;
        double c = numeratorHi / denominatorLo;
        double d = numeratorHi / denominatorHi;
        return new double[]{Math.min(Math.min(a, b), Math.min(c, d)), Math.max(Math.max(a, b), Math.max(c, d))};
    }

    /**
     * Whether {@link #eqCoverage} can run: every factor needs an enumerable domain, and the
     * combinations they span must stay under {@link #MAX_COVERAGE_COMBINATIONS}.
     */
    static <N extends Number> boolean eqCoverageEligible(@NonNull List<Variable<N>> factors,
                                                         @NonNull Map<Variable<?>, Domain<?>> domains) {
        long combinations = 1;
        for (Variable<N> factor : factors) {
            if (!(domains.get(factor) instanceof DiscreteDomain<?> discrete)) return false;
            combinations *= discrete.size();
            if (combinations > MAX_COVERAGE_COMBINATIONS) return false;
        }
        return true;
    }

    /**
     * Exact GAC for {@code product == target}: enumerates every combination of the factors' actual
     * values, keeps each factor value that participates in at least one combination whose product
     * {@code targetAccepts}, and reports which products are reachable so a variable target can be
     * narrowed to them too.
     * <p>
     * {@code null} when no combination is acceptable at all, i.e. the constraint is infeasible.
     */
    static <N extends Number> @Nullable Coverage<N> eqCoverage(@NonNull List<Variable<N>> factors,
                                                               @NonNull Map<Variable<?>, Domain<?>> domains,
                                                               @NonNull DoublePredicate targetAccepts) {
        List<List<N>> values = new ArrayList<>(factors.size());
        for (Variable<N> factor : factors) {
            values.add(((DiscreteDomain<N>) domains.get(factor)).toList());
        }
        List<Set<N>> supported = new ArrayList<>(factors.size());
        for (int i = 0; i < factors.size(); i++) {
            supported.add(new LinkedHashSet<>());
        }
        Set<Double> reachable = new LinkedHashSet<>();
        enumerate(values, 0, 1.0, new Object[factors.size()], targetAccepts, supported, reachable);
        return reachable.isEmpty() ? null : new Coverage<>(supported, reachable);
    }

    @SuppressWarnings("unchecked")
    private static <N extends Number> void enumerate(List<List<N>> values, int index, double product,
                                                     Object[] chosen, DoublePredicate targetAccepts,
                                                     List<Set<N>> supported, Set<Double> reachable) {
        if (index == values.size()) {
            if (!targetAccepts.test(product)) return;
            reachable.add(product);
            for (int i = 0; i < chosen.length; i++) {
                supported.get(i).add((N) chosen[i]);
            }
            return;
        }
        for (N value : values.get(index)) {
            chosen[index] = value;
            enumerate(values, index + 1, product * value.doubleValue(), chosen, targetAccepts, supported, reachable);
        }
    }

    /**
     * The factor values that kept support and the products those combinations reach -- everything
     * {@link #eqCoverage}'s callers need to build their own narrowing map.
     */
    record Coverage<N extends Number>(@NonNull List<Set<N>> supportedFactorValues,
                                      @NonNull Set<Double> reachableProducts) {
    }

    /**
     * {@code domain} with every value {@code keep} does not contain removed, or {@code null} when
     * nothing was removed -- so a caller can skip publishing an unchanged domain.
     */
    static <N extends Number> @Nullable Domain<N> retain(@NonNull DiscreteDomain<N> domain,
                                                         @NonNull Set<N> keep) {
        DiscreteDomain.Builder<N> builder = null;
        for (N value : domain.toList()) {
            if (keep.contains(value)) continue;
            if (builder == null) builder = domain.toBuilder();
            builder.delete(value);
        }
        return builder == null ? null : builder.build();
    }

    /** Adds {@code variable -> domain} to {@code updated} when {@code domain} is non-null. */
    static <N extends Number> void putIfNarrowed(@NonNull Map<Variable<?>, Domain<?>> updated,
                                                 @NonNull Variable<N> variable, @Nullable Domain<N> domain) {
        if (domain != null) updated.put(variable, domain);
    }

    /** A fresh map for a propagator to accumulate its narrowings into. */
    static Map<Variable<?>, Domain<?>> updates() {
        return new LinkedHashMap<>();
    }
}
