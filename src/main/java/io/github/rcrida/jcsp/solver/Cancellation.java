package io.github.rcrida.jcsp.solver;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal an external caller can use to ask a search to stop early.
 * Checked frequently — once per local-search step ({@link RaceLocalSolver}'s original use), once
 * per main-chain search node, once per {@link SetBranchingSolver} branch step, once per cutset
 * assignment {@link io.github.rcrida.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver}
 * tries, and once per propagator within {@link FixpointPropagation}'s fixpoint loop — the same way
 * {@link io.github.rcrida.jcsp.assignments.SolverLimits} is checked throughout the backtracking
 * solvers; cheap enough to leave in place unconditionally.
 * <p>
 * Registered on the main chain via {@code SolverConfig.getCancellation()}. See {@link
 * SolverCancelledException} for how a cancelled satisfaction-chain search surfaces to the caller.
 * <p>
 * {@link #NEVER} is the shared sentinel every unconfigured solve defaults to. Since it's shared
 * process-wide, {@link #cancel()} throws {@link UnsupportedOperationException} when called on it,
 * rather than silently cancelling every other unconfigured solve in progress.
 */
public final class Cancellation {
    public static final Cancellation NEVER = new Cancellation();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        if (this == NEVER) {
            throw new UnsupportedOperationException("Cancellation.NEVER is a shared sentinel and must never be cancelled");
        }
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
