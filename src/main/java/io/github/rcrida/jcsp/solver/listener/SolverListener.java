package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.BoundSolver;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.IndependentSubproblemSolver;
import io.github.rcrida.jcsp.solver.SolverConfig;

/**
 * Registered before calling {@link BoundSolver#getSolution()}/{@link BoundSolver#getSolutions()}
 * via {@link SolverConfig} to observe main-chain search progress as it happens. Composed from
 * {@link #onSolutionFound} (declared here) plus {@link SearchTreeListener} (backtracking-tree
 * mechanics), {@link OptimizationListener} ({@link BranchAndBoundSolver}'s incumbent updates), and
 * {@link PropagationListener} (propagator-level fixpoint progress) -- grouped into separate
 * interfaces for focused documentation, but a caller always just implements/overrides methods on
 * this one combined type, e.g. {@code new SolverListener() { ... }}.
 *
 * <p>All default implementations are no-ops; override only the events you need. {@link #NONE} is
 * the shared sentinel every solver defaults to when no listener is supplied — gated on with
 * reference equality ({@code ==}), never {@code instanceof}/dispatch, at every hot-path call site
 * so that an unregistered listener costs exactly what it costs today: nothing extra.
 *
 * <p><b>Thread-safety contract:</b> {@link IndependentSubproblemSolver} runs subproblems
 * concurrently (virtual threads). Every method on a registered {@link SolverListener} may
 * therefore be invoked concurrently from multiple threads; implementations are responsible for
 * their own synchronization. The library itself performs no additional locking around these calls
 * -- it only ever reads already thread-safe state (e.g. the {@link Assignment} passed to each
 * callback) before invoking them.
 */
public interface SolverListener extends SearchTreeListener, OptimizationListener, PropagationListener {
    SolverListener NONE = new SolverListener() {};

    /** A complete, consistent solution was found ({@link DomWdegLubySearch}, the satisfaction chain). */
    default void onSolutionFound(Assignment solution) {}
}
