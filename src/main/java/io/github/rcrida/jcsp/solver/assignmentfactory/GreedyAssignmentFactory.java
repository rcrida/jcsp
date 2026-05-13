package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An {@link InitialAssignmentFactory} that builds an initial assignment greedily: variables are
 * assigned one at a time in their natural order, and each variable receives the value from its
 * domain that violates the fewest constraints against the variables already assigned. Ties are
 * broken randomly.
 * <p>
 * The resulting assignment is complete but not necessarily consistent. It typically starts with
 * far fewer conflicts than a random assignment, which reduces the number of steps required by a
 * subsequent local search.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GreedyAssignmentFactory implements InitialAssignmentFactory {

    public static final GreedyAssignmentFactory INSTANCE = new GreedyAssignmentFactory();

    @Override
    public Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp) {
        var current = Assignment.empty();
        for (Variable variable : csp.getVariableDomains().keySet()) {
            val value = leastConflictingValue(variable, current, csp);
            current = Assignment.of(addEntry(current, variable, value));
        }
        return current;
    }

    private Object leastConflictingValue(Variable variable, Assignment current, ConstraintSatisfactionProblem csp) {
        val constraintsOnVariable = csp.getConstraints().stream()
                .filter(c -> c.getVariables().contains(variable))
                .toList();

        val domainValues = csp.getVariableDomains().get(variable).stream().toList();
        int bestViolations = Integer.MAX_VALUE;
        List<Object> bestValues = new ArrayList<>();
        for (Object value : domainValues) {
            val candidate = Assignment.of(addEntry(current, variable, value));
            int violations = (int) constraintsOnVariable.stream()
                    .filter(c -> c.getVariables().stream().allMatch(v -> candidate.getValue(v).isPresent()))
                    .filter(c -> !c.isSatisfiedBy(candidate))
                    .count();
            if (violations < bestViolations) {
                bestViolations = violations;
                bestValues.clear();
                bestValues.add(value);
            } else if (violations == bestViolations) {
                bestValues.add(value);
            }
        }
        return bestValues.get(ThreadLocalRandom.current().nextInt(bestValues.size()));
    }

    private static Map<Variable, Object> addEntry(Assignment current, Variable variable, Object value) {
        val map = new HashMap<>(current.getValues());
        map.put(variable, value);
        return map;
    }
}
