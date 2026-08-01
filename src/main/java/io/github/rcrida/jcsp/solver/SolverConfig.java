package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Bundles the configuration knobs {@link Solver.Factory#createSolver} accepts, so that adding a
 * new one doesn't mean adding another overload -- {@code limits} and {@link #nogoodLearningEnabled}
 * used to be separate parameters (and separate overloads for "with" and "without" each), which
 * doesn't scale as more knobs get added.
 * <p>
 * {@code SolverConfig.builder().build()} reproduces {@code createSolver}'s previous unconfigured
 * defaults exactly: unlimited search, nogood learning (CDCL) enabled.
 * <p>
 * {@link #nogoodLearningEnabled} affects both chains -- the satisfaction chain's {@link
 * DomWdegLubySearch} and the optimization chain's {@link BranchAndBoundSolver}, which also folds a
 * {@link io.github.rcrida.jcsp.assignments.NogoodStore} into its search since 2026-07-18. {@code Solver.Factory} reads this once, at
 * construction time, via the shared {@code Solver.Factory#nogoodLearningInference} helper, to
 * decide which {@link io.github.rcrida.jcsp.consistency.Inference} to hand the terminal solver -- {@code FULL_PROPAGATION_INFERENCE}
 * when {@code true}, or {@link io.github.rcrida.jcsp.consistency.Inference#withoutReasonTracking}
 * wrapping it when {@code false} -- rather than either terminal solver branching on the flag
 * itself; either way {@code false} disables CDCL entirely: no explanation computation, no
 * accumulation, e.g. for problem shapes where learned nogoods rarely get reused.
 * <p>
 * {@code statistics} is the shared token search writes into for the entire life of the returned
 * {@link BoundSolver}: every top-level search node -- across every independent subproblem and,
 * for the satisfaction chain, every Luby restart -- accumulates into this exact instance. Since
 * the caller keeps {@code config} in scope after passing it to {@code createSolver}, {@code
 * config.getStatistics()} is always readable afterward regardless of how the solve ends: a real
 * solution, a genuine UNSAT {@code Optional.empty()}, or a thrown {@link LimitExceededException}
 * -- closing the gap where genuine UNSAT previously left no {@link Statistics} reachable from
 * {@link BoundSolver} at all. Construct a fresh {@link SolverConfig} (and therefore a fresh {@link
 * Statistics}) per logical solve; reusing one {@link SolverConfig} across multiple
 * {@link BoundSolver#getSolution}/{@link BoundSolver#getSolutions} calls accumulates counts across all of them.
 * <p>
 * {@code listener} is threaded the same way {@code statistics} is -- read once at construction time
 * and passed by reference into every builder that needs to fire {@link SolverListener} events (see
 * {@link Solver.Factory#INSTANCE}) -- but, unlike {@code statistics}, is never mutated by the
 * library itself; it's purely a caller-supplied callback.
 * <p>
 * {@code cancellation} is threaded the same way {@code limits} is -- read once at construction time
 * and passed by reference into every builder that checks it -- but where {@code limits} caps work
 * by a pre-configured node/time budget, {@code cancellation} is an external stop signal a caller
 * can trigger at any point during the solve, e.g. from a callback on a registered {@code listener}.
 * It mirrors {@code limits}' own asymmetry: {@link BoundSolver#getSolution()} throws {@link
 * SolverCancelledException} only in the satisfaction chain; every other path (both chains'
 * {@code getSolutions()}, the optimization chain's {@code getSolution()}) stops silently instead.
 */
@Value
@Builder
public class SolverConfig {
    @Builder.Default @NonNull SolverLimits limits = SolverLimits.unlimited();
    @Builder.Default boolean nogoodLearningEnabled = true;
    @Builder.Default @NonNull Statistics statistics = new Statistics();
    @Builder.Default @NonNull SolverListener listener = SolverListener.NONE;
    @Builder.Default @NonNull Cancellation cancellation = Cancellation.NEVER;
}
