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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * exact GAC, not a sound-but-incomplete approximation, and costs the same O(variables × |{@link
 * #conflicts}|) per round {@link NaryTuplesConstraint#propagate} already costs -- no dependence on
 * domain size at all.
 * <p>
 * Added for XCSP3's {@code extension} construct's {@code positive="false"} case, confirmed via a
 * real competition instance ({@code driverlogw-09.xml.lzma}, over 1300 binary conflict-table
 * constraints) that previously threw {@code UnsupportedXcsp3ConstraintException} unconditionally.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NaryConflictTuplesConstraint extends NaryConstraint implements Propagatable {
    @Singular Set<Assignment> conflicts;

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
        var liveConflicts = conflicts.stream()
                .filter(t -> getVariables().stream().allMatch(v -> domains.get(v).contains(t.getValue(v).orElseThrow())))
                .toList();
        if (liveConflicts.isEmpty()) return Optional.of(Map.of());

        long maxPossibleCount = liveConflicts.size();
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

            Map<Object, Long> liveConflictCountByValue = liveConflicts.stream()
                    .collect(Collectors.groupingBy(t -> t.getValue(v).orElseThrow(), Collectors.counting()));

            DiscreteDomain<?> dom = (DiscreteDomain<?>) domains.get(v);
            Set<Object> unsupported = new HashSet<>();
            for (var value : dom.toList()) {
                if (liveConflictCountByValue.getOrDefault(value, 0L) == otherDomainProduct) unsupported.add(value);
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
