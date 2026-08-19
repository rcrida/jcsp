package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that requires each tracked value to appear a bounded number of times across
 * a set of variables: {@code cardinalityRanges.get(v).min() <= count(vars, v) <= cardinalityRanges.get(v).max()}
 * for each entry. An exact count (the common case) is just {@code min() == max()}.
 * <p>
 * Values not present in the cardinality map are unconstrained (open GCC).
 * <p>
 * Equivalent to MiniZinc's {@code global_cardinality(vars, values, counts)} (exact form, via
 * {@link #of}) or {@code global_cardinality_low_up(vars, values, lows, ups)} (range form, via
 * {@link #ofRange}). Generalises {@link CountConstraint} (single value) and {@link AllDiffConstraint}
 * (all counts == 1).
 * <p>
 * {@link #propagate}/{@link #explainInfeasible} implement Régin's flow-based generalized arc
 * consistency algorithm — the same matching + residual-graph SCC idea {@link AllDiffConstraint}
 * uses for its own Régin (1994) algorithm, generalized from 0/1 bipartite matching to a flow
 * network with per-tracked-value {@code [min, max]} capacities (Régin, "Generalized Arc
 * Consistency for Global Cardinality Constraint", AAAI 1996 -- the general range form is Régin's
 * own original formulation, not a later extension). Every untracked value is merged into one
 * shared sink node rather than modelled individually: this constraint never needs to know
 * <em>which</em> untracked value a variable takes, only that it can reach one, so merging is
 * lossless for GAC purposes and keeps the flow network's size independent of how many distinct
 * untracked values appear.
 * <p>
 * The flow-with-lower-bounds network construction, max-flow computation, and residual-graph GAC
 * filtering itself live in {@link GlobalCardinalityPropagation}, shared with {@link
 * GlobalCardinalityVariableConstraint} (each tracked value's occurrence count is itself a
 * variable, re-read fresh on every call, rather than this class's fixed {@link #cardinalityRanges}
 * map) -- both classes build the identical flow network and differ only in where each tracked
 * value's {@code [lo,hi]} bounds come from. See that class's own Javadoc for the GAC filtering
 * detail beyond the exact-count case (the {@code sinkOriginal} node, excess-capacity edges, and
 * why no node-splitting technique is needed since the {@code [min,max]} bound sits on an edge in
 * this formulation, not a node).
 *
 * @see <a href="https://doi.org/10.1609/aaai.v1.10380">Régin (1996)</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GlobalCardinalityConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {
    /** A tracked value's permitted occurrence count, inclusive on both ends ({@code min <= max}). */
    public record OccurrenceRange(int min, int max) {
        public OccurrenceRange {
            assert min >= 0 : "min must be >= 0";
            assert min <= max : "min must be <= max";
        }
    }

    @Singular private final Map<T, OccurrenceRange> cardinalityRanges;

    public static <T> GlobalCardinalityConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                                        @NonNull Map<T, Integer> cardinalities) {
        // A structural (domain-independent) infeasibility: no assignment of `variables` can ever
        // supply more total quota than there are variables to supply it. Failing fast here beats
        // discovering it only as an unexplained UNSAT deep in search -- and for exactly this shape
        // (Σ quotas > n), the flow-based propagator's own violating-subset extraction can return
        // empty (see findViolatingSubset's Javadoc), so search wouldn't even get a useful nogood.
        assert cardinalities.values().stream().mapToInt(Integer::intValue).sum() <= variables.size()
                : "sum of cardinalities exceeds variable count: no assignment can satisfy this GCC";
        Map<T, OccurrenceRange> ranges = new HashMap<>();
        cardinalities.forEach((value, count) -> ranges.put(value, new OccurrenceRange(count, count)));
        return GlobalCardinalityConstraint.<T>builder()
                .variables(variables)
                .cardinalityRanges(ranges)
                .build();
    }

    /**
     * As {@link #of(Set, Map)}, but each tracked value's occurrence count is a {@code [min, max]}
     * range rather than a fixed count.
     */
    public static <T> GlobalCardinalityConstraint<T> ofRange(@NonNull Set<Variable<T>> variables,
                                                             @NonNull Map<T, OccurrenceRange> cardinalityRanges) {
        // Same structural check as of(), but over each value's minimum -- the maximums have no
        // equivalent structural bound (an unreachably high max is just never binding).
        assert cardinalityRanges.values().stream().mapToInt(OccurrenceRange::min).sum() <= variables.size()
                : "sum of minimum occurrences exceeds variable count: no assignment can satisfy this GCC";
        return GlobalCardinalityConstraint.<T>builder()
                .variables(variables)
                .cardinalityRanges(cardinalityRanges)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        Map<T, Integer> counts = new HashMap<>();
        for (T v : values) counts.merge(v, 1, Integer::sum);

        for (var entry : cardinalityRanges.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) > entry.getValue().max()) return false;
        }
        if (values.size() < getVariables().size()) return true;
        for (var entry : cardinalityRanges.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) < entry.getValue().min()) return false;
        }
        return true;
    }

    /**
     * Builds this constraint's own tracked-value order once and derives {@code [lo,hi]} from
     * {@link #cardinalityRanges} in that same order, then delegates the actual flow-with-lower-bounds
     * computation to {@link GlobalCardinalityPropagation#computeFlow} -- shared with {@link
     * GlobalCardinalityVariableConstraint}, whose only difference is where {@code lo}/{@code hi}
     * come from (a variable's current bounds, re-read fresh each call, rather than this fixed map).
     */
    @SuppressWarnings("unchecked")
    private GlobalCardinalityPropagation.FlowResult<T> computeFlow(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<T> trackedValues = new ArrayList<>(cardinalityRanges.keySet());
        int[] lo = new int[trackedValues.size()];
        int[] hi = new int[trackedValues.size()];
        for (int k = 0; k < trackedValues.size(); k++) {
            OccurrenceRange range = cardinalityRanges.get(trackedValues.get(k));
            lo[k] = range.min();
            hi[k] = range.max();
        }
        return GlobalCardinalityPropagation.computeFlow(
                (Set<Variable<T>>) (Set<?>) getVariables(), trackedValues, lo, hi, domains);
    }

    /**
     * Régin's GAC propagator, generalized from bipartite matching to flow-with-lower-bounds -- see
     * {@link GlobalCardinalityPropagation#propagateFromFlow} for the algorithm itself (identical
     * for this class and {@link GlobalCardinalityVariableConstraint}); {@link #computeFlow} above
     * is the only part specific to the fixed-range case.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        GlobalCardinalityPropagation.FlowResult<T> result = computeFlow(domains);
        if (!result.feasible()) return Optional.empty();
        return Optional.of(GlobalCardinalityPropagation.propagateFromFlow(result, cardinalityRanges.keySet(), domains));
    }

    /**
     * Finds the violating variable subset via {@link GlobalCardinalityPropagation#findViolatingSubset}
     * -- the standard max-flow-min-cut construction, identical for this class and {@link
     * GlobalCardinalityVariableConstraint}.
     */
    private Optional<List<Variable<?>>> findViolatingSubset(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GlobalCardinalityPropagation.findViolatingSubset(computeFlow(domains));
    }

    /**
     * Attributes infeasibility to the violating subset found by {@link #findViolatingSubset},
     * mirroring {@link AllDiffConstraint#explainInfeasible}'s exact two-tier fallback: a ground
     * reason via {@link Propagatable#allSingletonReason} when every violator is currently
     * singleton, else a {@link RangeNogoodConstraint} over the same subset's current bounds. Which
     * one of the two Hall-type conditions actually failed is never inspected — the violating
     * subset alone is enough for either fallback, exactly as it is for {@link AllDiffConstraint}.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return findViolatingSubset(domains).flatMap(zVars -> {
            Map<Variable<?>, Object> ground = Propagatable.allSingletonReason(zVars, domains);
            if (!ground.isEmpty()) return GroundNogoodConstraint.fromReason(ground);
            return RangeNogoodConstraint.fromCurrentBounds(zVars, domains);
        });
    }

    @Override
    public String getRelation() {
        return "GlobalCardinality(" + cardinalityRanges.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue().min() + ".." + e.getValue().max())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}
