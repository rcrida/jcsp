package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * A single consistency/propagation pass over a {@link ConstraintSatisfactionProblem}.
 * Returns the reduced problem, or {@link Optional#empty()} if infeasibility is detected.
 */
@FunctionalInterface
public interface ConstraintConsistency {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp);

    /**
     * Variant of {@link #apply(ConstraintSatisfactionProblem)} that accepts a hint of which
     * variables' domains changed since this consistency pass last ran in the current fixpoint
     * loop (see {@code PropagationFixpointSolver#applyFixpoint}), or {@code null} meaning "unknown
     * — assume everything may have changed". Passes whose cost scales with a fixed, small
     * constraint count (every {@link io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency}
     * instance, {@link io.github.rcrida.jcsp.consistency.arc.AC3}) have no need for the hint and
     * inherit this default, which simply ignores it and delegates to {@link #apply}.
     * {@code io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency} is the one
     * override: its constraint count grows unboundedly over a search (see {@code NogoodStore}),
     * so skipping constraints that don't reference any changed variable is where this hint
     * actually pays for itself.
     */
    default Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp,
                                                           @Nullable Set<Variable<?>> changedSinceLastRun) {
        return apply(csp);
    }

    /**
     * Re-runs this consistency pass with reason tracking and returns the nogood that explains
     * the conflict, or {@link Optional#empty()} if this pass did not detect a conflict.
     * The default returns empty; subclasses that support explanation override this.
     */
    default Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        return Optional.empty();
    }
}
