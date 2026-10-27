package io.github.rcrida.jcsp.variables;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the variable a problem or a solution holds under a name. A variable is identified by being
 * the one its factory issued, so a test that parsed or built a problem cannot recreate one by name
 * to look it up; it has to ask the problem or the solution for its own.
 */
public final class VariablesByName {
    private VariablesByName() {
    }

    public static <T> Variable<T> in(ConstraintSatisfactionProblem csp, String name) {
        return named(csp.getVariableDomains().keySet(), name);
    }

    public static <T> Variable<T> in(Assignment assignment, String name) {
        List<Variable<?>> variables = new ArrayList<>();
        assignment.forEach((variable, value) -> variables.add(variable));
        return named(variables, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> Variable<T> named(Iterable<? extends Variable<?>> variables, String name) {
        for (Variable<?> variable : variables) {
            if (variable.getName().equals(name)) {
                return (Variable<T>) variable;
            }
        }
        throw new IllegalArgumentException("No variable named " + name);
    }
}
