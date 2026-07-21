package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared bounds extraction and narrowing for {@code double}-based propagation.
 * Handles both {@link BoundedDomain} domains (e.g. {@link io.github.rcrida.jcsp.domains.IntervalDomain}),
 * narrowed via {@link BoundedDomain#withBounds}, and plain enumerable domains, narrowed by deleting
 * out-of-range values.
 */
public final class NumericBounds {
    private NumericBounds() {}

    public static <N extends Number> double min(Domain<N> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return bounded.getMin().doubleValue();
        return ((DiscreteDomain<N>) domain).stream().mapToDouble(Number::doubleValue).min().orElseThrow();
    }

    public static <N extends Number> double max(Domain<N> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return bounded.getMax().doubleValue();
        return ((DiscreteDomain<N>) domain).stream().mapToDouble(Number::doubleValue).max().orElseThrow();
    }

    /**
     * Narrows {@code domain} to {@code [newMin, newMax]}.
     *
     * @return {@link Optional#empty()} if the domain is unchanged, otherwise the narrowed
     *         domain (which may itself be {@link Domain#isEmpty() empty}, signalling infeasibility)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <N extends Number> Optional<Domain<N>> narrow(Domain<N> domain, double newMin, double newMax) {
        if (domain instanceof BoundedDomain<?> bounded) {
            double curMin = bounded.getMin().doubleValue();
            double curMax = bounded.getMax().doubleValue();
            double lo = Math.max(curMin, newMin);
            double hi = Math.min(curMax, newMax);
            if (lo == curMin && hi == curMax) return Optional.empty();
            BoundedDomain raw = bounded;
            return Optional.of((Domain<N>) raw.withBounds(lo, hi));
        }

        DiscreteDomain<N> discrete = (DiscreteDomain<N>) domain;
        DiscreteDomain.Builder<N> builder = null;
        for (N val : discrete.toList()) {
            double v = val.doubleValue();
            if (v < newMin || v > newMax) {
                if (builder == null) builder = discrete.toBuilder();
                builder.delete(val);
            }
        }
        return builder == null ? Optional.empty() : Optional.of(builder.build());
    }

    /**
     * Bounds-consistency propagation for {@code sum(coefficients[i]*variables[i]) <op> target},
     * used by {@link io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint} (all
     * coefficients {@code 1.0}) and {@link io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint}
     * (real per-variable coefficients). Generalises the same per-term contribution/feasibility/
     * narrowing shape those constraints' bound-comparing siblings ({@code SumBoundConstraint},
     * {@code LinearBoundConstraint}) already implement, treating {@code target} as one additional
     * term with coefficient {@code -1.0} and comparing the combined total against the constant
     * {@code 0} — {@code sum(vars) <op> target} rearranges to {@code sum(vars) - target <op> 0}
     * for every operator (subtraction never flips inequality direction, unlike multiplying by a
     * negative). Working entirely in {@code double} (via {@link #min}/{@link #max}/{@link #narrow})
     * rather than through a typed coefficient map is what makes this fully generic over {@code N}:
     * unlike a {@code Map<Variable<N>, N>}, a raw {@code double} coefficient never needs an
     * {@code N}-typed constant synthesized for it.
     * <p>
     * Only {@link Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} propagate (matching
     * every other propagator in this codebase); other operators return a no-op update.
     *
     * @return {@link Optional#empty()} if infeasible, otherwise the (possibly empty) map of
     *         variables — from {@code variables} and/or {@code target} — whose domains were narrowed
     */
    @SuppressWarnings("unchecked")
    public static <N extends Number> Optional<Map<Variable<?>, Domain<?>>> propagateWeightedSumVsTarget(
            List<Variable<N>> variables, double[] coefficients, Variable<N> target,
            Operator operator, Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }

        int n = variables.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(variables.get(i));
            double domMin = min(dom), domMax = max(dom);
            mins[i] = coefficients[i] >= 0 ? coefficients[i] * domMin : coefficients[i] * domMax;
            maxs[i] = coefficients[i] >= 0 ? coefficients[i] * domMax : coefficients[i] * domMin;
        }

        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double targetMin = min(targetDomain), targetMax = max(targetDomain);
        // target is the (n+1)th term, coefficient -1
        double targetMinContrib = -targetMax;
        double targetMaxContrib = -targetMin;

        double totalMin = targetMinContrib, totalMax = targetMaxContrib;
        for (int i = 0; i < n; i++) { totalMin += mins[i]; totalMax += maxs[i]; }

        if ((operator == Operator.EQ && (0 < totalMin || 0 > totalMax)) ||
            (operator == Operator.LEQ && 0 < totalMin) ||
            (operator == Operator.GEQ && 0 > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (coefficients[i] == 0) continue;
            double restMin = totalMin - mins[i];
            double restMax = totalMax - maxs[i];
            double newMin, newMax;
            if (coefficients[i] > 0) {
                newMax = (operator != Operator.GEQ) ? (0 - restMin) / coefficients[i] : Double.POSITIVE_INFINITY;
                newMin = (operator != Operator.LEQ) ? (0 - restMax) / coefficients[i] : Double.NEGATIVE_INFINITY;
            } else {
                newMin = (operator != Operator.GEQ) ? (0 - restMin) / coefficients[i] : Double.NEGATIVE_INFINITY;
                newMax = (operator != Operator.LEQ) ? (0 - restMax) / coefficients[i] : Double.POSITIVE_INFINITY;
            }
            Domain<N> dom = (Domain<N>) domains.get(variables.get(i));
            var pruned = narrow(dom, newMin, newMax);
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(variables.get(i), pruned.get());
            }
        }

        {
            double restMin = totalMin - targetMinContrib;
            double restMax = totalMax - targetMaxContrib;
            double newMin = (operator != Operator.GEQ) ? (0 - restMin) / -1.0 : Double.NEGATIVE_INFINITY;
            double newMax = (operator != Operator.LEQ) ? (0 - restMax) / -1.0 : Double.POSITIVE_INFINITY;
            var pruned = narrow(targetDomain, newMin, newMax);
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(target, pruned.get());
            }
        }

        return Optional.of(updated);
    }
}
