package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;

/** Incumbent-improvement progress, fired only by {@link BranchAndBoundSolver}. */
public interface OptimizationListener {
    /** A strictly better incumbent was found. */
    default void onIncumbentImproved(Assignment solution, double cost) {}
}
