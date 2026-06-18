package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Assignment;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * A solver that has been bound to a specific {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem}
 * and (for optimization) an objective function. Created by {@link Solver.Factory#createSolver}.
 *
 * <p>For satisfaction solvers, {@link #getSolution()} returns any feasible solution (findFirst).
 * For optimization solvers, {@link #getSolution()} returns the global optimum by consuming the
 * improving stream to its last element.
 */
public interface BoundSolver {
    Stream<Assignment> getSolutions();

    default Optional<Assignment> getSolution() {
        return getSolutions().findFirst();
    }
}
