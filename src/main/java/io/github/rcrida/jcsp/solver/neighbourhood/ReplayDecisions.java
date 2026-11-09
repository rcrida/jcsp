package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.backtrackingsearch.ForcedAssignment;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link DecisionSource.Factory#REPLAY}: walks the solution down from the root, branching on whatever a fresh
 * selector picks and assigning it the solution's value, and records the variables it branched on.
 * <p>
 * A replay of a solution of {@code csp} cannot fail: every value it assigns is the solution's, propagation
 * never rules out a value of a solution, and what propagation forces agrees with it. So a failure here
 * means the solution was not one, and is reported by the {@code orElseThrow} that meets it. It does not
 * check the solution, though: a value that propagation would have forced is never compared with the
 * solution's own.
 */
final class ReplayDecisions implements DecisionSource {
    private final Inference inference;
    private final AdaptiveVariableSelector.Factory selectors;
    private final Assignment root;

    ReplayDecisions(@NonNull Inference inference, AdaptiveVariableSelector.@NonNull Factory selectors,
                    @NonNull Assignment root) {
        this.inference = inference;
        this.selectors = selectors;
        this.root = root;
    }

    @Override
    public List<Variable<?>> decisionsOf(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment solution) {
        List<Variable<?>> decisions = new ArrayList<>();
        AdaptiveVariableSelector selector = selectors.createSelector(csp.getConstraints());
        ConstraintSatisfactionProblem current = csp;
        Assignment assignment = root;
        while (true) {
            assignment = ForcedAssignment.extend(current, assignment).orElseThrow();
            if (assignment.isComplete(current)) return decisions;
            Variable<?> decision = selector.select(current, assignment);
            decisions.add(decision);
            assignment = assignment.withValue(decision, solution.getValue(decision).orElseThrow());
            current = inference.apply(current, decision, assignment, Set.of()).orElseThrow();
        }
    }
}
