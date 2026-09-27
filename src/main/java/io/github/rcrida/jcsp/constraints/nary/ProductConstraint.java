package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the product of a set of numeric variables to a fixed bound:
 * {@code v1 * v2 * ... * vn op bound}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned.
 * <p>
 * Propagation runs for EQ, LEQ, and GEQ, over {@link ProductPropagation}'s signed interval
 * arithmetic, so the whole-product feasibility check is correct whatever the factor signs.
 * <p>
 * EQ narrows each factor to {@code bound / (product of the others)} — sound for any signs, skipped
 * for a factor whose complementary product {@link ProductPropagation#straddlesZero} (the quotient
 * is then unbounded) — and, when the factors are enumerable and few enough, instead runs
 * {@link ProductPropagation#eqCoverage} for exact GAC via {@link #eqCoverage}. LEQ/GEQ stay
 * restricted to strictly positive factor minimums: their one-sided clips read the complementary
 * product's single favourable extreme, which only bounds a factor that cannot itself be negative.
 * Upper-bound pass (LEQ): clips each variable's maximum to {@code bound * min(var) / productMin}.
 * Lower-bound pass (GEQ): raises each variable's minimum to {@code bound * max(var) / productMax}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ProductConstraint<N extends Number> extends UniformNaryConstraint<N> implements Propagatable {

    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull private final N bound;
    @NonNull private final Operator operator;

    public static <N extends Number> ProductConstraint<N> of(
            @NonNull Set<Variable<N>> variables,
            @NonNull Operator operator,
            @NonNull N bound) {
        return ProductConstraint.<N>builder()
                .variables(variables)
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<N> values) {
        if (values.size() < getVariables().size()) return true;
        double product = values.stream().mapToDouble(Number::doubleValue).reduce(1.0, (a, b) -> a * b);
        return operator.compare(product, bound.doubleValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        boolean allPositive = true;
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            mins[i] = NumericBounds.min(dom);
            maxs[i] = NumericBounds.max(dom);
            allPositive &= mins[i] > 0;
        }

        double[] product = ProductPropagation.range(mins, maxs);
        double productMin = product[0], productMax = product[1];
        double k = bound.doubleValue();

        if ((operator == Operator.EQ  && (k < productMin || k > productMax)) ||
            (operator == Operator.LEQ && k < productMin) ||
            (operator == Operator.GEQ && k > productMax)) return Optional.empty();

        if (operator == Operator.EQ && ProductPropagation.eqCoverageEligible(vars, domains)) {
            return eqCoverage(vars, k, domains);
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));

            // EQ narrows from both sides at once: factor == k / (product of the others), sound for
            // any signs once the divisor is known not to straddle zero.
            if (operator == Operator.EQ) {
                double[] others = ProductPropagation.rangeExcluding(mins, maxs, i);
                if (ProductPropagation.straddlesZero(others)) continue;
                double[] allowed = ProductPropagation.divide(k, k, others[0], others[1]);
                Optional<Domain<N>> narrowed = NumericBounds.narrow(dom,
                        Math.max(mins[i], allowed[0]), Math.min(maxs[i], allowed[1]));
                if (narrowed.isPresent()) {
                    if (narrowed.get().isEmpty()) return Optional.empty();
                    updated.put(vars.get(i), narrowed.get());
                }
                continue;
            }

            // LEQ/GEQ stay gated on every factor being positive: their one-sided clips read the
            // complementary product's single favourable extreme, which only bounds a factor that
            // cannot itself be negative.
            if (!allPositive) continue;

            // Upper-bound pass: product ≤ k — clip each variable's max to k / othersMinProduct
            if (operator == Operator.LEQ) {
                double newMax = k * mins[i] / productMin;
                if (newMax < maxs[i]) {
                    // mins[i] > 0 and k >= productMin guarantee newMax >= mins[i]; narrow returns present
                    dom = (Domain<N>) NumericBounds.narrow(dom, mins[i], newMax).orElseThrow();
                    updated.put(vars.get(i), dom);
                }
            }

            // Lower-bound pass: product ≥ k — raise each variable's min to k / othersMaxProduct
            if (operator == Operator.GEQ) {
                double newMin = k * maxs[i] / productMax;
                if (newMin > mins[i]) {
                    // newMin > mins[i] guarantees narrow returns present, and never empty: k <=
                    // productMax (the guard above) puts newMin at or below maxs[i], so the domain's
                    // own maximum survives. Only EQ could empty it, by running the clip above first
                    // and leaving dom narrower than maxs[i] -- and EQ now returns before here.
                    updated.put(vars.get(i), (Domain<N>) NumericBounds.narrow(dom, newMin, maxs[i]).orElseThrow());
                }
            }
        }
        return Optional.of(updated);
    }

    /**
     * Exact GAC for {@code product == bound} over enumerable factor domains -- {@link
     * ProductVariableConstraint#eqCoverage}'s constant-bound counterpart, reaching what no interval
     * rule can when a factor's domain excludes zero but its hull does not.
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> eqCoverage(List<Variable<N>> vars, double bound,
                                                              Map<Variable<?>, Domain<?>> domains) {
        ProductPropagation.Coverage<N> coverage =
                ProductPropagation.eqCoverage(vars, domains, candidate -> candidate == bound);
        if (coverage == null) return Optional.empty();
        Map<Variable<?>, Domain<?>> updated = ProductPropagation.updates();
        for (int i = 0; i < vars.size(); i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            ProductPropagation.putIfNarrowed(updated, vars.get(i),
                    ProductPropagation.retain(dom, coverage.supportedFactorValues().get(i)));
        }
        return Optional.of(updated);
    }

    /**
     * On infeasibility, the product's violation depends on the combined product of every
     * variable, not any single variable in isolation — like {@link SumBoundConstraint}/
     * {@link LinearBoundConstraint} and unlike {@link MaxConstraint}/{@link MinConstraint}, a product
     * has no monotonic "one value alone already breaks the bound" case (a single large factor
     * says nothing about the bound without knowing the other factors too). Neither the
     * {@code productMin}/{@code productMax} bound check nor the discrete-gap corner case in the
     * lower-bound pass ({@code raised.isEmpty()}) requires any variable to be singleton, so
     * {@link RangeNogoodConstraint#fromCurrentBounds} is tried first, falling back to
     * {@link Propagatable#allSingletonReason}'s fully collective ground reason only when it can't
     * safely cite some variable's domain as a range.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        String varProduct = getVariables().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(" * "));
        return varProduct + " " + operator.symbol + " " + bound;
    }
}
