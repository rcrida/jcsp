package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bounds propagation for {@code Σ coefficients[i]*vars[i] <op> bound}, shared by {@link
 * LinearBoundConstraint} (real per-variable coefficients) and {@link SumBoundConstraint} — an
 * unweighted sum is exactly this with every coefficient fixed at {@code 1}, provably so: with
 * {@code coeffs[i] = 1}, {@code Math.floorDiv}/{@code Math.ceilDiv} by {@code 1} are the identity,
 * collapsing this class's {@code newMax}/{@code newMin} derivation to {@link SumBoundConstraint}'s
 * own direct {@code k - (totalMin - mins[i])} / {@code k - (totalMax - maxs[i])} formulas.
 * <p>
 * {@code propagateInt}'s {@code EQ} case additionally runs {@link
 * SubsetSumCoveragePropagation#computeSubsetSumCoverage} — real value-level GAC beyond this
 * class's own bounds-only pass — whenever {@link SubsetSumCoveragePropagation#eligible} judges the
 * achievable-sum range small enough; see that class's own Javadoc for why bounds consistency alone
 * is insufficient for {@code EQ} over gapped discrete domains.
 */
final class LinearBoundPropagation {
    private LinearBoundPropagation() {}

    static <N extends Number> Optional<Map<Variable<?>, Domain<?>>> propagateInt(
            List<Variable<N>> vars, int[] coeffs, int bound, Operator operator,
            Map<Variable<?>, Domain<?>> domains) {
        int n = vars.size();
        int[] minContribs = new int[n];
        int[] maxContribs = new int[n];

        for (int i = 0; i < n; i++) {
            @SuppressWarnings("unchecked")
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            int domMin = dom.stream().mapToInt(Number::intValue).min().orElseThrow();
            int domMax = dom.stream().mapToInt(Number::intValue).max().orElseThrow();
            minContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMin : coeffs[i] * domMax;
            maxContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMax : coeffs[i] * domMin;
        }

        int totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }

        if ((operator == Operator.EQ  && (bound < totalMin || bound > totalMax)) ||
            (operator == Operator.LEQ && bound < totalMin) ||
            (operator == Operator.GEQ && bound > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (coeffs[i] == 0) continue;
            @SuppressWarnings("unchecked")
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            int restMin = totalMin - minContribs[i];
            int restMax = totalMax - maxContribs[i];

            int newMin, newMax;
            if (coeffs[i] > 0) {
                newMax = (operator != Operator.GEQ) ? Math.floorDiv(bound - restMin, coeffs[i]) : Integer.MAX_VALUE;
                newMin = (operator != Operator.LEQ) ? Math.ceilDiv(bound - restMax, coeffs[i])  : Integer.MIN_VALUE;
            } else {
                newMin = (operator != Operator.GEQ) ? Math.ceilDiv(bound - restMin, coeffs[i])  : Integer.MIN_VALUE;
                newMax = (operator != Operator.LEQ) ? Math.floorDiv(bound - restMax, coeffs[i]) : Integer.MAX_VALUE;
            }

            DiscreteDomain.Builder<N> builder = null;
            for (N val : dom.toList()) {
                int v = val.intValue();
                if (v < newMin || v > newMax) {
                    if (builder == null) builder = dom.toBuilder();
                    builder.delete(val);
                }
            }
            if (builder != null) {
                DiscreteDomain<N> pruned = builder.build();
                if (pruned.isEmpty()) return Optional.empty();
                updated.put(vars.get(i), pruned);
            }
        }

        if (operator == Operator.EQ && SubsetSumCoveragePropagation.eligible(totalMin, totalMax)) {
            Optional<Map<Variable<?>, Domain<?>>> coverage =
                    propagateSubsetSumCoverage(vars, coeffs, bound, minContribs, maxContribs, domains, updated);
            if (coverage.isEmpty()) return Optional.empty();
            updated.putAll(coverage.get());
        }

        return Optional.of(updated);
    }

    @SuppressWarnings("unchecked")
    private static <N extends Number> Optional<Map<Variable<?>, Domain<?>>> propagateSubsetSumCoverage(
            List<Variable<N>> vars, int[] coeffs, int bound, int[] minContribs, int[] maxContribs,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> boundsNarrowed) {
        int n = vars.size();
        List<int[]> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) boundsNarrowed.getOrDefault(vars.get(i), domains.get(vars.get(i)));
            values.add(dom.stream().mapToInt(Number::intValue).toArray());
        }

        Optional<List<Set<Integer>>> kept =
                SubsetSumCoveragePropagation.computeSubsetSumCoverage(values, coeffs, minContribs, maxContribs, bound);
        if (kept.isEmpty()) return Optional.empty();

        // kept.get().get(i) is guaranteed non-empty here (SubsetSumCoveragePropagation itself
        // returns Optional.empty() the moment any term's retained set is empty), so narrowing a
        // domain down to it can never itself produce an empty domain -- same reasoning
        // ExtremumPropagation#propagateEqCoverage's own Javadoc documents for its narrowToValues
        // calls, and why no separate infeasibility check is needed in this loop.
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) boundsNarrowed.getOrDefault(vars.get(i), domains.get(vars.get(i)));
            Set<Integer> keepValues = kept.get().get(i);
            DiscreteDomain.Builder<N> builder = null;
            for (N val : dom.toList()) {
                if (!keepValues.contains(val.intValue())) {
                    if (builder == null) builder = dom.toBuilder();
                    builder.delete(val);
                }
            }
            if (builder != null) {
                updated.put(vars.get(i), builder.build());
            }
        }
        return Optional.of(updated);
    }

    static <N extends Number> Optional<Map<Variable<?>, Domain<?>>> propagateDouble(
            List<Variable<N>> vars, double[] coeffs, double bound, Operator operator,
            Map<Variable<?>, Domain<?>> domains) {
        int n = vars.size();
        double[] minContribs = new double[n];
        double[] maxContribs = new double[n];

        for (int i = 0; i < n; i++) {
            @SuppressWarnings("unchecked")
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            double domMin = NumericBounds.min(dom);
            double domMax = NumericBounds.max(dom);
            minContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMin : coeffs[i] * domMax;
            maxContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMax : coeffs[i] * domMin;
        }

        double totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }

        if ((operator == Operator.EQ  && (bound < totalMin || bound > totalMax)) ||
            (operator == Operator.LEQ && bound < totalMin) ||
            (operator == Operator.GEQ && bound > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (coeffs[i] == 0) continue;
            @SuppressWarnings("unchecked")
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            double restMin = totalMin - minContribs[i];
            double restMax = totalMax - maxContribs[i];

            double newMin, newMax;
            if (coeffs[i] > 0) {
                newMax = (operator != Operator.GEQ) ? (bound - restMin) / coeffs[i] : Double.POSITIVE_INFINITY;
                newMin = (operator != Operator.LEQ) ? (bound - restMax) / coeffs[i] : Double.NEGATIVE_INFINITY;
            } else {
                newMin = (operator != Operator.GEQ) ? (bound - restMin) / coeffs[i] : Double.NEGATIVE_INFINITY;
                newMax = (operator != Operator.LEQ) ? (bound - restMax) / coeffs[i] : Double.POSITIVE_INFINITY;
            }

            var pruned = NumericBounds.narrow(dom, newMin, newMax);
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(vars.get(i), pruned.get());
            }
        }
        return Optional.of(updated);
    }
}
