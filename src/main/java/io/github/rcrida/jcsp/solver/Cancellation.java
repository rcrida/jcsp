package io.github.rcrida.jcsp.solver;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal threaded through a local search loop so an external caller
 * (currently only {@link RaceLocalSolver}) can ask it to stop early. Checked once per search step,
 * the same way {@link io.github.rcrida.jcsp.assignments.SolverLimits} is checked in the
 * backtracking solvers — cheap enough to leave in place unconditionally.
 * <p>
 * {@link #NEVER} is a shared sentinel for callers that have no external cancellation source (i.e.
 * every {@link LocalSolver} entry point invoked directly rather than through a race); {@link #cancel()}
 * must never be called on it.
 */
final class Cancellation {
    static final Cancellation NEVER = new Cancellation();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    void cancel() {
        cancelled.set(true);
    }

    boolean isCancelled() {
        return cancelled.get();
    }
}
