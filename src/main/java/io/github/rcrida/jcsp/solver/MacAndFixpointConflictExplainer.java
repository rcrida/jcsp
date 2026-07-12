package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.MAC;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;

/**
 * Explains a conflict by re-running MAC with reason tracking, then falling back to the full
 * propagation fixpoint if MAC alone cannot isolate the cause. Always returns a
 * {@link GroundNogoodConstraint} — never {@link Optional#empty()} — since the current assignment
 * is itself always a valid, if not minimal, nogood.
 *
 * <p>Falls back to the assignment values unchanged when MAC itself wipes a domain (no finer
 * explanation is possible) or when the fixpoint finds no conflict in the MAC-reduced problem.
 */
public final class MacAndFixpointConflictExplainer implements ConflictExplainer {

    public static final MacAndFixpointConflictExplainer INSTANCE = new MacAndFixpointConflictExplainer();

    private MacAndFixpointConflictExplainer() {}

    @Override
    public Optional<NogoodConstraint> explain(ConstraintSatisfactionProblem csp,
                                              Variable<?> variable,
                                              Assignment assignment) {
        var postMac = MAC.INSTANCE.apply(csp, variable, assignment);
        if (postMac.isEmpty()) return Optional.of(GroundNogoodConstraint.of(assignment.getValues()));
        var reason = PropagationFixpointSolver.explainConflict(postMac.get());
        return Optional.of(GroundNogoodConstraint.of(reason.isEmpty() ? assignment.getValues() : reason));
    }
}
