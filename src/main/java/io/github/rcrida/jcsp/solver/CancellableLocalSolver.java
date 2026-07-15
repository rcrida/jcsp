package io.github.rcrida.jcsp.solver;

import org.jspecify.annotations.NonNull;

/**
 * Package-private contract for {@link LocalSolver} implementations whose search loop can be asked
 * to stop early via a {@link Cancellation} token. {@link RaceLocalSolver} uses this to cut short
 * the losing delegate once another delegate has already produced a result, instead of letting it
 * run to its own {@code maxAttempts}/{@code maxSteps} completion for no benefit.
 * <p>
 * {@link #withCancellation} returns a copy configured with the given token; the ordinary
 * {@link LocalSolver#getLocalSolution} methods on that copy then honour it. Implemented by
 * {@link MinConflictsSolver} and {@link TabuSearchSolver}.
 */
interface CancellableLocalSolver {
    LocalSolver withCancellation(@NonNull Cancellation cancellation);
}
