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
 * propagation fixpoint if MAC alone cannot isolate the cause. Never returns
 * {@link Optional#empty()} — since the current assignment is itself always a valid, if not
 * minimal, nogood, {@link GroundNogoodConstraint#of} over it is the final fallback.
 *
 * <p>Falls back to the assignment values unchanged when MAC itself wipes a domain (no finer
 * explanation is possible). Otherwise defers to whatever {@link PropagationFixpointSolver#explainConflict}
 * finds in the MAC-reduced problem — a {@link GroundNogoodConstraint} when some propagator's own
 * {@code explainInfeasible} applies, a {@link io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint}
 * when only the generic current-bounds fallback applies, or nothing when the fixpoint finds no
 * conflict at all in the MAC-reduced problem (a known asymmetry — the original inference call that
 * actually failed may have found infeasibility this re-derivation doesn't reproduce).
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
        return reason.isPresent() ? reason : Optional.of(GroundNogoodConstraint.of(assignment.getValues()));
    }
}
