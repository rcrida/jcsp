package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bundles the configuration knobs {@link Solver.Factory#createSolver} accepts, so that adding a
 * new one doesn't mean adding another overload -- {@code limits} and {@link #nogoodLearningEnabled}
 * used to be separate parameters (and separate overloads for "with" and "without" each), which
 * doesn't scale as more knobs get added.
 * <p>
 * {@code SolverConfig.builder().build()} gives unlimited search and, since 2026-09-23, nogood
 * learning (CDCL) <em>off</em> -- see {@link #learningEnabled()} for the measurement behind that
 * change, and [ADR-0030] for why.
 * <p>
 * {@link #nogoodLearningEnabled} is a tri-state {@link Boolean}: {@code TRUE}/{@code FALSE} are a
 * caller's explicit choice and always win, while {@code null} (the default) means "no opinion --
 * let the library decide". That distinction exists so the library's own default can change
 * without breaking a caller who deliberately asked for one behaviour. Read it through
 * {@link #learningEnabled()} rather than the raw getter, which reports only what was configured.
 * <p>
 * {@link #nogoodLearningEnabled} affects both chains -- the satisfaction chain's {@link
 * DomWdegLubySearch} and the optimization chain's {@link BranchAndBoundSolver}, which also folds a
 * {@link io.github.rcrida.jcsp.assignments.NogoodStore} into its search since 2026-07-18. {@code Solver.Factory} reads this once, at
 * construction time, via the shared {@code Solver.Factory#nogoodLearningInference} helper, to
 * decide which {@link io.github.rcrida.jcsp.consistency.Inference} to hand the terminal solver --
 * the per-solve {@link io.github.rcrida.jcsp.consistency.Inference} built by {@code
 * Solver.Factory#propagationInference} when {@code true}, or {@link
 * io.github.rcrida.jcsp.consistency.Inference#withoutReasonTracking} wrapping it when {@code
 * false} -- rather than either terminal solver branching on the flag itself; either way {@code
 * false} disables CDCL entirely: no explanation computation, no accumulation, e.g. for problem
 * shapes where learned nogoods rarely get reused. The flag also decides (via {@code
 * FixpointPropagation.Factory#forProblem}) whether the propagator fixpoint includes {@code
 * NogoodFixpointConsistency} at all when {@code csp} carries no nogoods of its own yet -- nogoods
 * already present on the problem (e.g. via {@code ConstraintSatisfactionProblem.Builder#nogood})
 * are propagated regardless of this flag.
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
 * <p>
 * {@code restartRandomization} is threaded the same way {@code cancellation}/{@code listener} are
 * -- read once at construction time and passed by reference -- into {@link DomWdegLubySearch},
 * whose {@code getSolution()} reseeds its variable selector's tie-breaking from it once per Luby
 * restart. Unlike {@code cancellation}/{@code listener}, it does <em>not</em> default to a no-op:
 * each {@link SolverConfig} gets its own fresh random base seed (via {@link ThreadLocalRandom}),
 * so restart diversification is on by default -- search
 * behaviour (node counts, and potentially which solution comes back for a problem with multiple
 * solutions) is therefore not reproducible run-to-run unless a caller explicitly pins one via
 * {@link RestartRandomization#seeded}, matching the fact that it was never reproducible
 * launch-to-launch anyway (see {@link RestartRandomization}'s own Javadoc). Pass {@link
 * RestartRandomization#NONE} to restore today's pre-2026-08-14 deterministic tie-breaking exactly.
 */
@Value
@Builder
public class SolverConfig {
    @Builder.Default @NonNull SolverLimits limits = SolverLimits.unlimited();
    /**
     * {@code null} means "library's choice"; {@code TRUE}/{@code FALSE} are an explicit override.
     * Resolve it via {@link #learningEnabled()} -- this raw accessor reports what was configured,
     * not what will happen.
     */
    @Builder.Default @Nullable Boolean nogoodLearningEnabled = null;
    @Builder.Default @NonNull Statistics statistics = new Statistics();
    @Builder.Default @NonNull SolverListener listener = SolverListener.NONE;
    @Builder.Default @NonNull Cancellation cancellation = Cancellation.NEVER;
    @Builder.Default @NonNull RestartRandomization restartRandomization =
            RestartRandomization.seeded(ThreadLocalRandom.current().nextLong());

    /**
     * Whether nogood learning actually runs: an explicit {@link #nogoodLearningEnabled} if the
     * caller set one, otherwise the library's own default, which is currently <em>off</em>.
     * <p>
     * That default is measured, not assumed. Across the bundled 85-instance XCSP3 corpus, enabling
     * learning solves exactly the same 72 instances as disabling it, while costing wall-clock on
     * the instances that do solve: 18 faster without it, 14 unaffected, and <b>none reliably
     * slower</b> (geometric mean 0.86, with {@code LangfordBin-08} 15.0s to 5.6s and {@code
     * Mario-easy-4} 3.2s to 1.3s, each stable across three seeds). {@code
     * ChessboardColoration-07-07} additionally goes from {@code SATISFIABLE} to {@code OPTIMUM
     * FOUND} without it, on three seeds of three. The mechanism is not merely neutral here: it is
     * a net cost, because the clauses it learns essentially never fire -- see ADR-0030 for why
     * that is structural rather than a defect in the explanations.
     * <p>
     * It stays available, and this returns {@code true} the moment a caller asks for it, because
     * "never pays" is a statement about this corpus rather than about clause learning. The one
     * instance that appeared to benefit ({@code Sat-flat200-00-clause}, the only genuinely
     * SAT-shaped instance present) did not survive repetition -- it is 8% faster <em>without</em>
     * learning across three seeds -- so a problem shape where CDCL earns its keep is untested here
     * rather than ruled out.
     */
    public boolean learningEnabled() {
        return Boolean.TRUE.equals(nogoodLearningEnabled);
    }
}
