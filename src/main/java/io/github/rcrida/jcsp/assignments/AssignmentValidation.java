package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;

/**
 * The domain check behind {@link Assignment#isConsistent} and {@link Assignment#isComplete}; a class
 * of its own because an {@code assert} in an interface is not filtered out of coverage.
 */
final class AssignmentValidation {
    private AssignmentValidation() {
    }

    static void validate(Assignment assignment, ConstraintSatisfactionProblem csp) {
        assignment.forEach((variable, value) -> {
            assert csp.isAllowedValue(variable, value) : String.format("Invalid assigned value for variable '%s': %s", variable, value);
        });
    }
}
