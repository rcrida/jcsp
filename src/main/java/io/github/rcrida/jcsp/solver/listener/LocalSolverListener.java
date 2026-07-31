package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.IndependentSubproblemLocalSolver;
import io.github.rcrida.jcsp.solver.LargeNeighborhoodSolver;
import io.github.rcrida.jcsp.solver.LocalSolver;
import io.github.rcrida.jcsp.solver.LocalSolverConfig;
import io.github.rcrida.jcsp.solver.MinConflictsSolver;
import io.github.rcrida.jcsp.solver.TabuSearchSolver;
import io.github.rcrida.jcsp.solver.WalkSATSolver;

/**
 * Registered before calling {@link LocalSolver#getLocalSolution} via {@link LocalSolverConfig} to
 * observe repair-search progress. Extends {@link PropagationListener} (shared with
 * {@link SolverListener}: {@link LocalSolver.Factory#PREPROCESSORS} runs a real, if non-fixpoint,
 * propagation pass before repair search starts). Deliberately does <em>not</em> share
 * {@link SearchTreeListener}/{@link OptimizationListener} with {@link SolverListener} -- nogoods,
 * restarts, backtracking, and incumbent-bound pruning are backtracking/branch-and-bound concepts
 * that don't exist in repair-based local search.
 *
 * <p>All default implementations are no-ops; override only the events you need. {@link #NONE} is
 * the shared sentinel every local-search solver defaults to when no listener is supplied.
 *
 * <p><b>Thread-safety contract:</b> {@link IndependentSubproblemLocalSolver} runs subproblems
 * concurrently (virtual threads), and {@link MinConflictsSolver}/{@link TabuSearchSolver}/
 * {@link WalkSATSolver}/{@link LargeNeighborhoodSolver} run every attempt concurrently
 * ({@code IntStream.parallel()}). Every method on a registered {@link LocalSolverListener} may
 * therefore be invoked concurrently from multiple threads; implementations are responsible for
 * their own synchronization.
 */
public interface LocalSolverListener extends PropagationListener {
    LocalSolverListener NONE = new LocalSolverListener() {};

    /** A complete, consistent solution was found. */
    default void onSolutionFound(Assignment solution) {}

    /** A repair-search step was taken. */
    default void onLocalSearchStep(int attempt, int step, Assignment current) {}
}
