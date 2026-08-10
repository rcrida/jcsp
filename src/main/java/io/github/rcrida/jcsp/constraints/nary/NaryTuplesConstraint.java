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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Singular Set<Assignment> tuples;

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
     * Generalised arc consistency for the table constraint: a tuple is "live" if every one of
     * its values is still present in the corresponding variable's domain. If no tuples remain
     * live, the constraint is infeasible. Otherwise, for each variable, any domain value not
     * used by some live tuple has no support and is pruned.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        var liveTuples = tuples.stream()
                .filter(t -> getVariables().stream().allMatch(v -> domains.get(v).contains(t.getValue(v).orElseThrow())))
                .toList();
        if (liveTuples.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<?> v : getVariables()) {
            DiscreteDomain<?> dom = (DiscreteDomain<?>) domains.get(v);
            var supportedValues = liveTuples.stream().map(t -> t.getValue(v).orElseThrow()).collect(Collectors.toSet());
            if (supportedValues.size() < dom.size()) {
                var builder = dom.toBuilder();
                for (var value : dom.toList()) if (!supportedValues.contains(value)) builder.delete(value);
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
