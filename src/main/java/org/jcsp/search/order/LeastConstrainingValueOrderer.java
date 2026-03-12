package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.BinaryConstraint;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;

public class LeastConstrainingValueOrderer implements DomainValuesOrderer {
    @Override
    public List<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable variable, @NonNull Assignment assignment) {
        final var binaryConstraints = csp.getConstraints().stream()
                .filter(BinaryConstraint.class::isInstance)
                .map(BinaryConstraint.class::cast)
                .filter(constraint -> constraint.left().equals(variable) || constraint.right().equals(variable))
                .toList();

        return csp.getVariableDomains().get(variable).stream()
                .sorted(Comparator.comparingLong(value ->
                        countEliminatedValues(csp, assignment, variable, value, binaryConstraints)))
                .toList();
    }

    private long countEliminatedValues(
            @NonNull ConstraintSatisfactionProblem csp,
            @NonNull Assignment assignment,
            @NonNull Variable variable,
            @NonNull Object value,
            @NonNull Iterable<BinaryConstraint> constraints
    ) {
        long eliminated = 0;

        for (BinaryConstraint constraint : constraints) {
            final Variable neighbour;
            final BinaryConstraint directedConstraint;

            if (constraint.left().equals(variable)) {
                neighbour = constraint.right();
                directedConstraint = constraint;
            } else {
                neighbour = constraint.left();
                directedConstraint = constraint.reversed();
            }

            if (assignment.getValue(neighbour).isPresent()) {
                continue;
            }

            final var neighbourDomain = csp.getVariableDomains().get(neighbour);

            eliminated += neighbourDomain.stream()
                    .filter(neighbourValue -> !directedConstraint.isSatisfied(value, neighbourValue))
                    .count();
        }

        return eliminated;
    }
}
