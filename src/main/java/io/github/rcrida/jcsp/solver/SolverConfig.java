package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Bundles the configuration knobs {@link Solver.Factory#createSolver} accepts, so that adding a
 * new one doesn't mean adding another overload -- {@code limits} and {@code nogoodLearningEnabled}
 * used to be separate parameters (and separate overloads for "with" and "without" each), which
 * doesn't scale as more knobs get added.
 * <p>
 * {@code SolverConfig.builder().build()} reproduces {@code createSolver}'s previous unconfigured
 * defaults exactly: unlimited search, nogood learning (CDCL) enabled.
 * <p>
 * {@code nogoodLearningEnabled} affects both chains -- the satisfaction chain's {@code
 * DomWdegLubySearch} and the optimization chain's {@code BranchAndBoundSolver}, which also folds a
 * {@code NogoodStore} into its search since 2026-07-18. {@code Solver.Factory} reads this once, at
 * construction time, via the shared {@code Solver.Factory#nogoodLearningInference} helper, to
 * decide which {@code Inference} to hand the terminal solver -- {@code FULL_PROPAGATION_INFERENCE}
 * when {@code true}, or {@link io.github.rcrida.jcsp.consistency.Inference#withoutReasonTracking}
 * wrapping it when {@code false} -- rather than either terminal solver branching on the flag
 * itself; either way {@code false} disables CDCL entirely: no explanation computation, no
 * accumulation, e.g. for problem shapes where learned nogoods rarely get reused.
 * <p>
 * {@code statistics} is the shared token search writes into for the entire life of the returned
 * {@code BoundSolver}: every top-level search node -- across every independent subproblem and,
 * for the satisfaction chain, every Luby restart -- accumulates into this exact instance. Since
 * the caller keeps {@code config} in scope after passing it to {@code createSolver}, {@code
 * config.getStatistics()} is always readable afterward regardless of how the solve ends: a real
 * solution, a genuine UNSAT {@code Optional.empty()}, or a thrown {@code LimitExceededException}
 * -- closing the gap where genuine UNSAT previously left no {@link Statistics} reachable from
 * {@code BoundSolver} at all. Construct a fresh {@code SolverConfig} (and therefore a fresh {@code
 * Statistics}) per logical solve; reusing one {@code SolverConfig} across multiple {@code
 * getSolution()}/{@code getSolutions()} calls accumulates counts across all of them.
 */
@Value
@Builder
public class SolverConfig {
    @Builder.Default @NonNull SolverLimits limits = SolverLimits.unlimited();
    @Builder.Default boolean nogoodLearningEnabled = true;
    @Builder.Default @NonNull Statistics statistics = new Statistics();
}
