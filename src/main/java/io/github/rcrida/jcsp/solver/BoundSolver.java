package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Assignment;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * A solver that has been bound to a specific {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem}
 * and (for optimization) an objective function. Created by {@link Solver.Factory#createSolver}.
 *
 * <p>For satisfaction solvers, {@link #getSolution()} returns a feasible solution or
 * {@link Optional#empty()} when the problem is UNSAT; throws {@link LimitExceededException}
 * (with {@link io.github.rcrida.jcsp.assignments.Statistics}) if a
 * {@link io.github.rcrida.jcsp.assignments.SolverLimits} is exceeded before the search completes,
 * or {@link SolverCancelledException} if a caller-supplied {@link Cancellation} (registered via
 * {@code SolverConfig.getCancellation()}) is cancelled first.
 *
 * <p>For optimization solvers, {@link #getSolution()} returns the best solution found so far
 * (or {@link Optional#empty()} if none was found) — limits and cancellation both truncate the
 * stream silently rather than throwing.
 */
public interface BoundSolver {
    Stream<Assignment> getSolutions();

    Optional<Assignment> getSolution();
}
