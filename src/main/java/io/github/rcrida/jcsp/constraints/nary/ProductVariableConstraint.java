package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The variable-target sibling of {@link ProductConstraint}: {@code v1 * v2 * ... * vn <op> target},
 * where {@link #target} is itself a variable rather than a fixed bound. Mirrors {@link
 * CountVariableConstraint}/{@link MaxVariableConstraint}'s own shape (a real {@link #operator}
 * field, {@link #target} narrowed alongside the factors) and reuses {@link ProductConstraint}'s
 * exact interval-arithmetic narrowing, just with {@code target}'s current bounds standing in for
 * the fixed {@code bound}. Extends {@link NaryConstraint} directly rather than {@link
 * UniformNaryConstraint}, whose {@code isSatisfiedBy} is {@code final} -- same reason every other
 * variable-target sibling in this package does.
 * <p>
 * Added specifically for XCSP3's {@code sum} with variable {@code coeffVars} ({@code
 * Σ list[i] * coeffVars[i] <op> condition}), which decomposes into one {@link
 * #ProductVariableConstraint} per position (a fresh auxiliary variable holding {@code
 * list[i] * coeffVars[i]}) plus a plain {@link SumVariableConstraint}/{@code sumConstraint} over
 * the auxiliaries -- see {@code Xcsp3CallbackHandler#buildCtrSum(String, org.xcsp.parser.entries.XVariables.XVarInteger[],
 * org.xcsp.parser.entries.XVariables.XVarInteger[], org.xcsp.common.Condition)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ProductVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @Getter @NonNull private final Set<Variable<N>> factors;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final Variable<N> target;

    public static <N extends Number> ProductVariableConstraint<N> of(
            @NonNull Set<Variable<N>> factors, @NonNull Operator operator, @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(factors);
        allVars.add(target);
        return ProductVariableConstraint.<N>builder()
                .variables(allVars)
                .factors(Set.copyOf(factors))
                .operator(operator)
                .target(target)
                .build();
    }

    /** Optimistically satisfied for a partial assignment -- only evaluated once every variable is assigned. */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        double product = factors.stream()
                .mapToDouble(v -> assignment.getValue(v).orElseThrow().doubleValue())
                .reduce(1.0, (a, b) -> a * b);
        double targetValue = assignment.getValue(target).orElseThrow().doubleValue();
        return operator.compare(product, targetValue);
    }

    /**
     * {@link ProductConstraint#propagate}'s narrowing — {@link ProductPropagation}'s signed interval
     * arithmetic for every operator it handles ({@code EQ}/{@code LEQ}/{@code GEQ}), with the
     * strictly-positive-factor restriction applying only to {@code LEQ}/{@code GEQ} — generalised
     * two ways: {@code target}'s <em>current</em> bounds stand in for the
     * fixed {@code bound} when narrowing the factors, and -- unlike {@link ProductConstraint},
     * which has no target to narrow -- {@link #target} itself is also narrowed to {@code
     * [productMin, productMax]}, the same "{@code leqLike} raises the lower bound, {@code geqLike}
     * lowers the upper bound" shape {@link CountVariableConstraint#propagate}/{@link
     * MaxVariableConstraint#propagate} already use. That range is never inverted (by the same
     * cross-bound algebra those two classes rely on: the early infeasibility checks below already
     * guarantee {@code productMin <= tHi} and {@code tLo <= productMax}), but -- learned the hard
     * way on {@link GlobalCardinalityVariableConstraint} -- a non-inverted <em>range</em> doesn't
     * guarantee {@code target}'s own (possibly gappy) discrete domain contains a value inside it,
     * so the narrowed result's emptiness is checked explicitly rather than assumed away.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        List<Variable<N>> vars = new ArrayList<>(factors);
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

        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        if (leqLike && productMin > tHi) return Optional.empty();
        if (geqLike && productMax < tLo) return Optional.empty();

        if (operator == Operator.EQ && targetDomain instanceof DiscreteDomain<N> discreteTarget
                && ProductPropagation.eqCoverageEligible(vars, domains)) {
            return eqCoverage(vars, discreteTarget, domains);
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));

            // EQ narrows from both sides at once: factor == target / (product of the others), which
            // is sound for any signs once the divisor is known not to straddle zero.
            if (operator == Operator.EQ) {
                double[] others = ProductPropagation.rangeExcluding(mins, maxs, i);
                if (ProductPropagation.straddlesZero(others)) continue;
                double[] allowed = ProductPropagation.divide(tLo, tHi, others[0], others[1]);
                Optional<Domain<N>> narrowed = NumericBounds.narrow(dom,
                        Math.max(mins[i], allowed[0]), Math.min(maxs[i], allowed[1]));
                if (narrowed.isPresent()) {
                    if (narrowed.get().isEmpty()) return Optional.empty();
                    updated.put(vars.get(i), narrowed.get());
                }
                continue;
            }

            // LEQ/GEQ stay gated on every factor being positive. Their one-sided clips read the
            // complementary product's single favourable extreme, which only bounds a factor that
            // cannot itself be negative -- otherwise the clip would cut values that do satisfy.
            if (!allPositive) continue;

            // Upper-bound pass: product <= target's max -- clip each factor's max to tHi / othersMinProduct.
            if (leqLike) {
                double newMax = tHi * mins[i] / productMin;
                if (newMax < maxs[i]) {
                    // mins[i] > 0 and tHi >= productMin guarantee newMax >= mins[i]; narrow returns present.
                    dom = (Domain<N>) NumericBounds.narrow(dom, mins[i], newMax).orElseThrow();
                    updated.put(vars.get(i), dom);
                }
            }

            // Lower-bound pass: product >= target's min -- raise each factor's min to tLo / othersMaxProduct.
            if (geqLike) {
                double newMin = tLo * maxs[i] / productMax;
                if (newMin > mins[i]) {
                    // newMin > mins[i] guarantees narrow returns present, and the result is never
                    // empty: tLo <= productMax (the guard above) puts newMin at or below maxs[i], so
                    // the domain's own maximum always survives. Only EQ could empty it, by running
                    // the clip above first and leaving dom narrower than maxs[i] -- and EQ now
                    // returns before reaching here.
                    updated.put(vars.get(i), NumericBounds.narrow(dom, newMin, maxs[i]).orElseThrow());
                }
            }
        }

        double newTLo = leqLike ? Math.max(tLo, productMin) : tLo;
        double newTHi = geqLike ? Math.min(tHi, productMax) : tHi;
        Optional<Domain<N>> narrowedTarget = NumericBounds.narrow(targetDomain, newTLo, newTHi);
        if (narrowedTarget.isPresent()) {
            if (narrowedTarget.get().isEmpty()) return Optional.empty();
            updated.put(target, narrowedTarget.get());
        }

        return Optional.of(updated);
    }

    /**
     * Exact GAC for {@code product == target} over enumerable factor domains: keeps each factor
     * value that participates in some combination whose product the target's domain admits, and
     * narrows {@code target} to the products actually reachable.
     * <p>
     * Reaches what no bounds pass can when a factor's domain excludes zero but its hull does not --
     * {@code {-1, 1}} being the motivating case, where dividing by the complementary factor's
     * {@code [-1, 1]} hull is unbounded and every interval rule gives up.
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> eqCoverage(List<Variable<N>> vars,
                                                              DiscreteDomain<N> targetDomain,
                                                              Map<Variable<?>, Domain<?>> domains) {
        Set<Object> targetValues = new HashSet<>(targetDomain.toList());
        ProductPropagation.Coverage<N> coverage = ProductPropagation.eqCoverage(vars, domains,
                candidate -> containsNumerically(targetValues, candidate));
        if (coverage == null) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = ProductPropagation.updates();
        for (int i = 0; i < vars.size(); i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            ProductPropagation.putIfNarrowed(updated, vars.get(i),
                    ProductPropagation.retain(dom, coverage.supportedFactorValues().get(i)));
        }
        // Never empty: the sweep only reports products this target's own value set accepted, so a
        // non-null coverage guarantees at least one target value keeps support.
        Set<N> keepTargets = new LinkedHashSet<>();
        for (N value : targetDomain.toList()) {
            if (coverage.reachableProducts().contains(value.doubleValue())) keepTargets.add(value);
        }
        ProductPropagation.putIfNarrowed(updated, target, ProductPropagation.retain(targetDomain, keepTargets));
        return Optional.of(updated);
    }

    /** Whether any of {@code values} equals {@code candidate} once widened to {@code double}. */
    private static boolean containsNumerically(Set<Object> values, double candidate) {
        for (Object value : values) {
            if (((Number) value).doubleValue() == candidate) return true;
        }
        return false;
    }

    /**
     * Mirrors {@link ProductConstraint#explainInfeasible}: the product's violation depends on the
     * combined product of every factor (plus, here, {@link #target}'s own bounds), not any single
     * variable in isolation, so {@link RangeNogoodConstraint#fromCurrentBounds} is tried first over
     * every variable including {@link #target}, falling back to {@link
     * Propagatable#allSingletonReason}'s fully collective ground reason only when it can't safely
     * cite some variable's domain as a range.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        String varProduct = factors.stream().map(Object::toString).sorted().collect(Collectors.joining(" * "));
        return varProduct + " " + operator.symbol + " " + target;
    }
}
