package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;

/**
 * Interface for inference algorithms in constraint satisfaction problems. The inference algorithm adds a global constraint for
 * the new variable assignment and imposes arc-, path-, or k-consistency constraints as desired.
 */
@FunctionalInterface
public interface Inference {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment);

    /**
     * Variant of {@link #apply} that also explains a failure as part of the same pass, rather than
     * requiring a caller to separately re-derive one afterward (there is deliberately no separate
     * "conflict explainer" interface for this — an {@code Inference} is the only thing that knows
     * how its own propagation failed, so explaining it is this interface's job, not a second one's).
     * The default delegates to {@link #apply} and, on failure, falls back to the current assignment
     * itself as a {@link GroundNogoodConstraint} — always sound, if not always minimal, since the
     * exact combination of values just tried is by definition jointly infeasible. Implementations
     * that can derive a tighter reason as a byproduct of their own propagation (see {@code
     * Solver.Factory#FULL_PROPAGATION_INFERENCE}) override this for a genuine single-pass
     * combination instead of relying on this default's assignment-wide fallback.
     */
    default ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment) {
        return apply(problem, variable, assignment)
                .map(ConsistencyResult::feasible)
                .orElseGet(() -> ConsistencyResult.infeasible(GroundNogoodConstraint.of(assignment.getValues())));
    }
}
