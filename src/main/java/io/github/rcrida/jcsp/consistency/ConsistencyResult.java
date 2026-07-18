package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import org.jspecify.annotations.Nullable;

/**
 * The result of a combined propagate-and-explain pass over a whole {@link ConstraintSatisfactionProblem}:
 * the narrowed problem on success, or the nogood that explains the failure (if one could be derived)
 * on infeasibility. Mirrors {@link PropagationResult} one level up — at CSP granularity rather than
 * a single constraint's domain map — so a domain wipeout can be explained as a side effect of the
 * same traversal that discovers it, instead of a second, from-scratch re-derivation (see {@link
 * ConstraintConsistency#applyWithReason} and {@link Inference#applyWithReason}).
 */
public record ConsistencyResult(@Nullable ConstraintSatisfactionProblem problem, @Nullable NogoodConstraint reason) {

    public static ConsistencyResult feasible(ConstraintSatisfactionProblem problem) {
        return new ConsistencyResult(problem, null);
    }

    public static ConsistencyResult infeasible(@Nullable NogoodConstraint reason) {
        return new ConsistencyResult(null, reason);
    }

    public boolean isInfeasible() {
        return problem == null;
    }
}
