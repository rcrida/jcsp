package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The domain check behind {@link Assignment#isConsistent} and {@link Assignment#isComplete}; a class
 * of its own because an {@code assert} in an interface is not filtered out of coverage.
 */
final class AssignmentValidation {
    private AssignmentValidation() {
    }

    /**
     * Checks every entry when assertions are enabled. The walk is the assert's own expression, so with
     * assertions disabled it costs nothing, where an {@code assert} per entry still walked every entry.
     */
    static void validate(Assignment assignment, ConstraintSatisfactionProblem csp) {
        assert invalidEntry(assignment, csp) == null : invalidEntry(assignment, csp);
    }

    /** A description of the first entry whose value {@code csp} does not allow, or {@code null} if there is none. */
    private static @Nullable String invalidEntry(Assignment assignment, ConstraintSatisfactionProblem csp) {
        for (Map.Entry<Variable<?>, Object> entry : assignment.entries()) {
            if (!csp.isAllowedValue(entry.getKey(), entry.getValue())) {
                return String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
            }
        }
        return null;
    }
}
