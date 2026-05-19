package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Selects a uniformly random variable from the full set of problem variables.
 * Suitable for local search when the assignment is feasible and any variable
 * is a candidate for perturbation.
 */
public class RandomVariableSelector implements UnassignedVariableSelector {
    public static final RandomVariableSelector INSTANCE = new RandomVariableSelector();

    private RandomVariableSelector() {}

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        var variables = csp.getVariableDomains().keySet().stream().toList();
        return variables.get(ThreadLocalRandom.current().nextInt(variables.size()));
    }
}
