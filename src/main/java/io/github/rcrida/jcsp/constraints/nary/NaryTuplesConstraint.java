package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
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
public class NaryTuplesConstraint extends NaryConstraint {
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

    @Override
    public String getRelation() {
        return "{" + tuples.stream()
                .map(NaryTuplesConstraint::tupleString)
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }

    private static String tupleString(Assignment assignment) {
        return assignment.getValues().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
