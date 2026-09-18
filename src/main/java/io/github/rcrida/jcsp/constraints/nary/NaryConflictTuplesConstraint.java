package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The negative-tuple sibling of {@link NaryTuplesConstraint}: {@link #conflicts} lists forbidden
 * combinations rather than permitted ones, with everything else implicitly allowed. Unlike
 * materialising the complement (the full cross-product of every constrained variable's domain,
 * minus {@link #conflicts} -- combinatorial, and the reason a plain conflict-table mapping was
 * declined for any arity above 1, see {@code Xcsp3CallbackHandler#buildCtrExtension(String,
 * XVarInteger[], int[][], boolean, java.util.Set)}), {@link #propagate} never enumerates that
 * complement: a value is unsupported exactly when the number of still-live conflicts fixing that
 * value equals the product of every <em>other</em> constrained variable's current domain size --
 * by a pigeonhole argument (a same-cardinality subset of a finite set must be the whole set), that
 * count can only ever be reached when literally every possible completion is separately listed as
 * a conflict, which is both necessary and sufficient for "no valid completion exists". This is
 * exact GAC, not a sound-but-incomplete approximation, with no dependence on domain size at all.
 * <p>
 * {@link #propagate} routes that reasoning through {@link #conflictIndex}, the direct counterpart of
 * {@link NaryTuplesConstraint}'s own {@link BitSet} support index (docs/adr/0021), for the same
 * reason and with the same payoff: deciding which conflicts are still live becomes word-parallel
 * {@link BitSet} intersection rather than a nested per-conflict, per-variable stream scan whose every
 * step cost a map lookup, an {@link Optional} allocation and a domain membership test. Not an
 * asymptotic change, but that per-conflict overhead is what dominated -- a single XCSP3 instance
 * ({@code driverlogw-09.xml.lzma}) builds 17,447 of these, and 60% of a JFR profile's samples on it
 * fell inside this one method, split across the two lambdas the scan allocated per call.
 * <p>
 * Added for XCSP3's {@code extension} construct's {@code positive="false"} case, confirmed via a
 * real competition instance ({@code driverlogw-09.xml.lzma}, over 1300 binary conflict-table
 * constraints) that previously threw {@code UnsupportedXcsp3ConstraintException} unconditionally.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NaryConflictTuplesConstraint extends NaryConstraint implements Propagatable {
    @Singular Set<Assignment> conflicts;

    /**
     * Lazily-built, immutable inverted index from {@link #conflicts}: {@code
     * conflictIndex.get(v).get(val)} is a {@link BitSet} over conflict positions, with bit {@code j}
     * set iff the {@code j}-th conflict assigns {@code v = val}. The exact counterpart of {@link
     * NaryTuplesConstraint}'s own support index -- identical structure, identical build, identical
     * lifecycle (computed once per constraint instance from the immutable {@link #conflicts} field,
     * read-only thereafter, so it is safe to share across concurrently-executing search threads with
     * no synchronization beyond the initial compare-and-set) -- only the meaning of a set bit differs:
     * there a bit records a permitted combination, here a forbidden one. See {@link
     * NaryTuplesConstraint}'s own field Javadoc for why this shape rather than STR2's mutable
     * current-tuple list, and docs/adr/0021 for the decision.
     */
    @EqualsAndHashCode.Exclude
    private final AtomicReference<Map<Variable<?>, Map<Object, BitSet>>> conflictIndex = new AtomicReference<>();

    private Map<Variable<?>, Map<Object, BitSet>> conflictIndex() {
        Map<Variable<?>, Map<Object, BitSet>> existing = conflictIndex.get();
        if (existing != null) return existing;
        conflictIndex.compareAndSet(null, buildConflictIndex());
        return conflictIndex.get();
    }

    private Map<Variable<?>, Map<Object, BitSet>> buildConflictIndex() {
        Map<Variable<?>, Map<Object, BitSet>> index = new LinkedHashMap<>();
        for (Variable<?> v : getVariables()) index.put(v, new LinkedHashMap<>());
        int position = 0;
        for (Assignment conflict : conflicts) {
            for (Variable<?> v : getVariables()) {
                Object value = conflict.getValue(v).orElseThrow();
                index.get(v).computeIfAbsent(value, key -> new BitSet(conflicts.size())).set(position);
            }
            position++;
        }
        return index;
    }

    /**
     * How many of {@code live}'s conflicts assign the value {@code support} was indexed under.
     * Word-parallel, unlike re-counting the conflict list per value; the clone is why this is only
     * ever called for a variable that already passed {@link #propagate}'s own product gate.
     */
    private static long liveOccurrences(BitSet support, BitSet live) {
        BitSet intersection = (BitSet) support.clone();
        intersection.and(live);
        return intersection.cardinality();
    }

    public static NaryConflictTuplesConstraint of(@NonNull Set<Assignment> conflicts) {
        assert !conflicts.isEmpty() : "conflicts must not be empty";
        var variableSets = conflicts.stream().map(a -> a.getValues().keySet()).collect(Collectors.toSet());
        assert variableSets.size() == 1 : "all conflict tuples must contain exactly the same variables";
        return NaryConflictTuplesConstraint.builder()
                .variables(variableSets.iterator().next())
                .conflicts(conflicts)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return !conflicts.contains(assignment.extractPartialAssignment(getVariables()));
    }

    /**
     * See the class Javadoc for the pigeonhole argument this relies on. {@code
     * otherDomainProduct}'s multiplication short-circuits the moment the running product exceeds
     * {@link #conflicts}'s own size (an upper bound on any single value's live-conflict count): at
     * that point no value of {@code v} could possibly be fully covered, so {@code v} is skipped
     * entirely without finishing the (potentially overflow-prone, for wide constraints) product.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Map<Object, BitSet>> index = conflictIndex();
        BitSet live = null;
        for (Variable<?> v : getVariables()) {
            Map<Object, BitSet> conflictsByValue = index.get(v);
            BitSet union = new BitSet(conflicts.size());
            for (Object value : ((DiscreteDomain<?>) domains.get(v)).asCollection()) {
                BitSet occurrences = conflictsByValue.get(value);
                if (occurrences != null) union.or(occurrences);
            }
            if (live == null) live = union; else live.and(union);
            // No conflict survives, so nothing is forbidden any more and every combination is
            // allowed -- the opposite of NaryTuplesConstraint, where an empty live set means no
            // permitted tuple remains and the constraint is infeasible.
            if (live.isEmpty()) return Optional.of(Map.of());
        }

        long maxPossibleCount = live.cardinality();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<?> v : getVariables()) {
            long otherDomainProduct = 1;
            boolean tooLargeToMatter = false;
            for (Variable<?> other : getVariables()) {
                if (other.equals(v)) continue;
                otherDomainProduct *= domains.get(other).size();
                if (otherDomainProduct > maxPossibleCount) { tooLargeToMatter = true; break; }
            }
            if (tooLargeToMatter) continue;

            Map<Object, BitSet> conflictsByValue = index.get(v);
            DiscreteDomain<?> dom = (DiscreteDomain<?>) domains.get(v);
            Set<Object> unsupported = new HashSet<>();
            for (Object value : dom.asCollection()) {
                BitSet occurrences = conflictsByValue.get(value);
                // A value appearing in no conflict at all is trivially supported: otherDomainProduct
                // is at least 1 here, since a non-empty `live` means every variable's domain still
                // holds at least that surviving conflict's own value.
                if (occurrences == null) continue;
                if (liveOccurrences(occurrences, live) == otherDomainProduct) unsupported.add(value);
            }
            if (unsupported.isEmpty()) continue;

            var builder = dom.toBuilder();
            for (var value : unsupported) builder.delete(value);
            var narrowed = builder.build();
            if (narrowed.isEmpty()) return Optional.empty();
            updated.put(v, narrowed);
        }
        return Optional.of(updated);
    }

    /** Mirrors {@link NaryTuplesConstraint#explainInfeasible}'s own reasoning exactly. */
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
        return "NOT IN {" + conflicts.stream()
                .map(a -> sortedVars.stream()
                        .map(v -> a.getValue(v).orElseThrow().toString())
                        .collect(Collectors.joining(", ", "(", ")")))
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }
}
