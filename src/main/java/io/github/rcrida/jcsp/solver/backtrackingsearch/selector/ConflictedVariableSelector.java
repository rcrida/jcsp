package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Selects a random variable from those currently involved in a violated constraint.
 * Suitable for local search: only considers variables that are causing conflicts.
 */
public class ConflictedVariableSelector implements UnassignedVariableSelector {
    public static final ConflictedVariableSelector INSTANCE = new ConflictedVariableSelector();

    private ConflictedVariableSelector() {}

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        var conflicted = csp.getConstraints().stream()
                .filter(Predicate.not(c -> c.isSatisfiedBy(assignment)))
                .flatMap(c -> c.getVariables().stream())
                .distinct()
                .toList();
        return conflicted.get(ThreadLocalRandom.current().nextInt(conflicted.size()));
    }
}
