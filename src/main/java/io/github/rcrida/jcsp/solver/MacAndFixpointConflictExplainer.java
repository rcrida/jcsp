package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.MAC;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;

/**
 * Explains a conflict by re-running MAC with reason tracking, then falling back to the full
 * propagation fixpoint if MAC alone cannot isolate the cause.
 *
 * <p>Returns the assignment values unchanged when MAC itself wipes a domain (no finer
 * explanation is possible) or when the fixpoint finds no conflict in the MAC-reduced problem.
 */
public final class MacAndFixpointConflictExplainer implements ConflictExplainer {

    public static final MacAndFixpointConflictExplainer INSTANCE = new MacAndFixpointConflictExplainer();

    private MacAndFixpointConflictExplainer() {}

    @Override
    public Map<Variable<?>, Object> explain(ConstraintSatisfactionProblem csp,
                                            Variable<?> variable,
                                            Assignment assignment) {
        var postMac = MAC.INSTANCE.apply(csp, variable, assignment);
        if (postMac.isEmpty()) return assignment.getValues();
        var reason = PropagationFixpointSolver.explainConflict(postMac.get());
        return reason.isEmpty() ? assignment.getValues() : reason;
    }
}
