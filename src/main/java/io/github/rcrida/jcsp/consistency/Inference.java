package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;
import java.util.Set;

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
     * "conflict explainer" interface for this — an {@link Inference} is the only thing that knows
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

    /**
     * Variant of {@link #apply} for a caller that narrowed {@code problem}'s domains itself since the
     * parent node's propagation converged: {@code alsoChanged} names the variables it narrowed.
     * {@code io.github.rcrida.jcsp.solver.BranchAndBoundSolver} is one, narrowing its incumbent bound
     * into the domains before branching.
     * <p>
     * An implementation that seeds its propagation from a dirty set has to include {@code
     * alsoChanged} in it, since the caller's narrowing happened outside any propagation pass and so
     * appears in no diff the implementation can take for itself -- without it that narrowing wakes no
     * propagator and the problem handed back is not a fixpoint of its own propagators. The default
     * ignores it, which is correct for an implementation that re-derives everything regardless.
     */
    default Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                                          Assignment assignment, Set<Variable<?>> alsoChanged) {
        return apply(problem, variable, assignment);
    }

    /** {@link #applyWithReason} with {@link #apply(ConstraintSatisfactionProblem, Variable, Assignment, Set)}'s
     *  caller-narrowed variables; same contract for {@code alsoChanged}. */
    default ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                              Assignment assignment, Set<Variable<?>> alsoChanged) {
        return applyWithReason(problem, variable, assignment);
    }

    /**
     * Wraps {@code delegate} so {@link #applyWithReason} never derives a reason on failure (a
     * {@code null} one, not this interface's default assignment-wide fallback), letting a caller
     * that always calls {@code applyWithReason} still get a true zero-explanation-cost path -- the
     * returned wrapper calls {@code delegate}'s plain {@link #apply}, never {@code
     * delegate.applyWithReason}, so whatever (possibly non-trivial) reason-derivation {@code
     * delegate} itself might otherwise do is skipped entirely, not just discarded.
     */
    static Inference withoutReasonTracking(Inference delegate) {
        return new Inference() {
            @Override
            public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem,
                                                                  Variable<?> variable, Assignment assignment) {
                return delegate.apply(problem, variable, assignment);
            }

            @Override
            public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                                      Variable<?> variable, Assignment assignment) {
                return apply(problem, variable, assignment)
                        .map(ConsistencyResult::feasible)
                        .orElseGet(() -> ConsistencyResult.infeasible(null));
            }

            // Both seeded variants are forwarded rather than left to their defaults, which would
            // drop alsoChanged on the floor: this wrapper is what the default (learning-off)
            // configuration wires in, so the defaults would mean no caller-narrowed variable ever
            // reaches the fixpoint's dirty seed outside a learning solve.
            @Override
            public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem,
                                                                  Variable<?> variable, Assignment assignment,
                                                                  Set<Variable<?>> alsoChanged) {
                return delegate.apply(problem, variable, assignment, alsoChanged);
            }

            @Override
            public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                                      Variable<?> variable, Assignment assignment,
                                                      Set<Variable<?>> alsoChanged) {
                return apply(problem, variable, assignment, alsoChanged)
                        .map(ConsistencyResult::feasible)
                        .orElseGet(() -> ConsistencyResult.infeasible(null));
            }
        };
    }
}
