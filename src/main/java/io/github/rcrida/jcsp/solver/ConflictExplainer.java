package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;

/**
 * Explains a propagation conflict by returning a nogood constraint, derived by re-running
 * propagation with reason tracking after {@link io.github.rcrida.jcsp.consistency.Inference#apply}
 * returns empty.
 * <p>
 * Any branch whose assignment subsumes the returned nogood must also fail — that's the only
 * soundness requirement; implementations are free to choose how they represent it (see
 * {@link NogoodConstraint}). {@link Optional#empty()} means "this explainer can't account for the
 * conflict" — used by composite explainers that try several specialized strategies before falling
 * back to a general-purpose one (e.g. {@link MacAndFixpointConflictExplainer}) that never returns
 * empty, since the full current assignment is always itself a valid, if not minimal, nogood.
 */
@FunctionalInterface
public interface ConflictExplainer {
    Optional<NogoodConstraint> explain(ConstraintSatisfactionProblem csp,
                                       Variable<?> variable,
                                       Assignment assignment);
}
