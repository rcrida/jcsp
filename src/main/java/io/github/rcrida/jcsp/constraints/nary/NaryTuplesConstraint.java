package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * An n-ary constraint defined by a set of permitted assignments: the combined values of the
 * constrained variables must match one of the allowed {@link Assignment} tuples.
 * <p>
 * Each tuple is expressed as an {@link Assignment}, so variable order is irrelevant.
 * All tuples must contain exactly the same variable set.
 * For partial assignments the constraint is optimistically satisfied.
 * <p>
 * Equivalent to MiniZinc's {@code table(x, t)} constraint.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NaryTuplesConstraint extends NaryConstraint implements Propagatable {
    @Getter @Singular Set<Assignment> tuples;

    /**
     * Lazily-built, immutable inverted index from {@link #tuples}: {@code
     * supportByVariableValue.get(v).get(val)} is a {@link BitSet} over tuple positions, with bit
     * {@code j} set iff {@code tuples}' {@code j}-th tuple assigns {@code v = val}. Computed once
     * per constraint instance (a pure function of the immutable {@link #tuples} field) and read-only
     * thereafter, so it's safe to share across concurrently-executing search threads (e.g. {@link
     * io.github.rcrida.jcsp.solver.RaceLocalSolver} runs multiple local-search strategies over the
     * same constraint objects in parallel) with no synchronization beyond the initial
     * compare-and-set -- unlike classic STR2 (Lecoutre 2011), which relies on a mutable
     * "current tuple list" restored via search-backtracking hooks, a shape that doesn't fit this
     * codebase's stateless {@code propagate(domains) -> domains} contract or its concurrent-solving
     * usage. Excluded from {@code equals}/{@code hashCode} (mirrors {@link #tuples} itself already
     * determining constraint identity) and from the {@code @SuperBuilder}-generated builder, since a
     * field with a direct initializer and no {@code @Builder.Default} is a Lombok-builder-invisible
     * derived field, not a settable one -- exactly what's wanted here (every instance starts with its
     * own fresh, empty cache).
     */
    @EqualsAndHashCode.Exclude
    private final AtomicReference<Map<Variable<?>, Map<Object, BitSet>>> supportIndex = new AtomicReference<>();

    /**
     * Replaces {@link #propagate}'s naive {@code O(tuples × variables)} stream scan with word-parallel
     * {@link BitSet} operations over {@link #supportIndex}: {@code O(tuples/64 × variables)}, plus a
     * one-time index-build cost amortised across every call for this constraint's lifetime. Not an
     * asymptotic improvement -- it eliminates per-tuple stream/lambda/hash-lookup overhead rather than
     * changing the underlying complexity class -- but that overhead is exactly what dominates on a
     * large table (e.g. {@code Steiner3-08.xml.lzma}'s 36 constraints, 80,640 tuples/6 variables each).
     */
    private Map<Variable<?>, Map<Object, BitSet>> supportIndex() {
        Map<Variable<?>, Map<Object, BitSet>> existing = supportIndex.get();
        if (existing != null) return existing;
        Map<Variable<?>, Map<Object, BitSet>> built = buildSupportIndex();
        supportIndex.compareAndSet(null, built);
        return supportIndex.get();
    }

    private Map<Variable<?>, Map<Object, BitSet>> buildSupportIndex() {
        Map<Variable<?>, Map<Object, BitSet>> index = new LinkedHashMap<>();
        for (Variable<?> v : getVariables()) index.put(v, new LinkedHashMap<>());
        int position = 0;
        for (Assignment tuple : tuples) {
            for (Variable<?> v : getVariables()) {
                Object value = tuple.getValue(v).orElseThrow();
                index.get(v).computeIfAbsent(value, key -> new BitSet(tuples.size())).set(position);
            }
            position++;
        }
        return index;
    }

    public static NaryTuplesConstraint of(@NonNull Set<Assignment> tuples) {
        assert !tuples.isEmpty() : "tuples must not be empty";
        var variableSets = tuples.stream().map(a -> a.getValues().keySet()).collect(Collectors.toSet());
        assert variableSets.size() == 1 : "all tuples must contain exactly the same variables";
        return NaryTuplesConstraint.builder()
                .variables(variableSets.iterator().next())
                .tuples(tuples)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return tuples.contains(assignment.extractPartialAssignment(getVariables()));
    }

    /**
     * Generalised arc consistency for the table constraint, via {@link #supportIndex}: a tuple is
     * "live" if every one of its values is still present in the corresponding variable's domain --
     * computed here as the {@link BitSet} intersection, across every variable, of the union of that
     * variable's currently-live values' own support bitsets. If the intersection is empty no tuple
     * remains live and the constraint is infeasible (checked incrementally, one variable at a time,
     * so an early domain wipeout skips building the rest). Otherwise, for each variable, any domain
     * value whose own support bitset doesn't intersect the live set has no support and is pruned.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Map<Object, BitSet>> index = supportIndex();
        BitSet live = null;
        for (Variable<?> v : getVariables()) {
            Map<Object, BitSet> supportByValue = index.get(v);
            BitSet union = new BitSet(tuples.size());
            for (Object value : ((DiscreteDomain<?>) domains.get(v)).asCollection()) {
                BitSet support = supportByValue.get(value);
                if (support != null) union.or(support);
            }
            if (live == null) live = union; else live.and(union);
            if (live.isEmpty()) return Optional.empty();
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<?> v : getVariables()) {
            Map<Object, BitSet> supportByValue = index.get(v);
            DiscreteDomain<?> dom = (DiscreteDomain<?>) domains.get(v);
            List<Object> unsupported = new ArrayList<>();
            for (Object value : dom.asCollection()) {
                BitSet support = supportByValue.get(value);
                if (support == null || !support.intersects(live)) unsupported.add(value);
            }
            if (!unsupported.isEmpty()) {
                var builder = dom.toBuilder();
                for (Object value : unsupported) builder.delete(value);
                updated.put(v, builder.build());
            }
        }
        return Optional.of(updated);
    }

    /**
     * {@link #propagate} already reports infeasibility purely from {@code liveTuples} being empty
     * against the current domains, with no requirement that any variable be singleton, so
     * {@link RangeNogoodConstraint#fromCurrentBounds} — citing each variable's current bounding
     * interval — is tried first: it's sound here for the same propagator-agnostic reason it's
     * sound for any {@link Propagatable}, per its own Javadoc, not because of anything specific to
     * tuple support. Falls back to {@link Propagatable#allSingletonReason}'s fully collective
     * ground reason only when it can't safely cite some variable's domain as a range (e.g. a
     * gapped domain, common here since {@link #propagate} prunes individual unsupported values
     * rather than contiguous ranges) — a partial subset can't rule out an unlisted open-domain
     * variable still finding support from some tuple.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        var sortedVars = getVariables().stream()
                .sorted(Comparator.comparing(Object::toString))
                .toList();
        return "{" + tuples.stream()
                .map(a -> sortedVars.stream()
                        .map(v -> a.getValue(v).orElseThrow().toString())
                        .collect(Collectors.joining(", ", "(", ")")))
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }
}
