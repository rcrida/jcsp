package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;

/**
 * A strategy for ordering the domain values of a variable in a constraint satisfaction problem (CSP)
 * based on the "Least Constraining Value" heuristic. This heuristic prioritizes values that eliminate
 * the fewest possible domain values for neighboring variables, thereby aiming to preserve flexibility
 * in the search space.
 * <p>
 * The ordering is determined by evaluating how many domain values would be eliminated for each
 * neighbouring variable if a particular value is assigned to the given variable. The value order is
 * established in ascending order of the number of eliminated values, with values that affect fewer
 * neighbours or eliminate fewer possibilities being prioritized.
 * <p>
 * This implementation specifically accounts for constraints that are instances of {@code BinaryConstraint},
 * which operate on pairs of variables. The elimination of domain values is computed by iterating
 * over the binary constraints involving the variable of interest and evaluating their satisfaction.
 */
public class LeastConstrainingValueOrderer implements DomainValuesOrderer {
    public static final LeastConstrainingValueOrderer INSTANCE = new LeastConstrainingValueOrderer();

    private LeastConstrainingValueOrderer() {}

    @Override
    public List<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable variable, @NonNull Assignment assignment) {
        final var binaryConstraints = csp.getConstraints().stream()
                .filter(BinaryConstraint.class::isInstance)
                .map(BinaryConstraint.class::cast)
                .filter(constraint -> constraint.getLeft().equals(variable) || constraint.getRight().equals(variable))
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

            if (constraint.getLeft().equals(variable)) {
                neighbour = constraint.getRight();
                directedConstraint = constraint;
            } else {
                neighbour = constraint.getLeft();
                directedConstraint = constraint.reversed();
            }

            if (assignment.getValue(neighbour).isPresent()) {
                continue;
            }

            final var neighbourDomain = csp.getVariableDomains().get(neighbour);

            eliminated += neighbourDomain.stream()
                    .filter(neighbourValue -> !directedConstraint.isSatisfiedBy(value, neighbourValue))
                    .count();
        }

        return eliminated;
    }
}
