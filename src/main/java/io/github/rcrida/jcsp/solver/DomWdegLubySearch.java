package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.DomWdegVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Backtracking search combining the dom/wdeg variable-ordering heuristic with Luby restarts.
 * <p>
 * Each constraint has a weight (initially 1). When MAC inference causes a domain wipeout the
 * weights of active constraints on the failing variable are bumped (via
 * {@link DomWdegVariableSelector#incrementWeights}). The variable selector then picks
 * {@code argmin(domainSize / weightedDegree)}, steering search away from costly failure regions.
 * <p>
 * <b>{@link #getSolutions}</b> returns a complete lazy stream of all solutions using dom/wdeg
 * variable ordering and weight accumulation during the search — a drop-in replacement for
 * {@link io.github.rcrida.jcsp.solver.backtrackingsearch.BacktrackingSearch}. The returned
 * stream must be consumed sequentially; it shares a mutable {@link DomWdegVariableSelector}
 * across all nodes, so calling {@code .parallel()} would cause data races in the weight map.
 * <p>
 * <b>{@link #getSolution}</b> overrides the default and applies Luby restarts: it uses the
 * sequence 1, 1, 2, 1, 1, 2, 4, … (multiplied by {@link #lubyUnit}) as per-restart failure
 * budgets, preserving weights across restarts so accumulated failure knowledge steers each new
 * attempt. Returns {@link Optional#empty()} either when the problem is genuinely unsatisfiable
 * (a restart exhausted its budget on the full tree) or when {@link #maxRestarts} restarts were
 * used without completing a full traversal; the two cases are not distinguished. Either way, the
 * {@link #statistics} field (a shared token seeded into every restart's root {@link Assignment},
 * not a fresh one per restart) still holds the true cumulative counts across the whole call —
 * see {@code SolverConfig.getStatistics()} for how a caller retrieves it regardless of outcome.
 * {@code restarts} specifically is recorded the moment each {@link BudgetExceeded} is caught, not
 * batched up at the end on the success path only — so a caller that observes {@link #statistics}
 * after a {@link LimitExceededException} or {@link SolverCancelledException} (e.g. from a {@link
 * SolverListener} callback, or by reading the exception's own carried snapshot) still sees every
 * restart that had actually completed before the interruption, not zero.
 * A lightweight {@link BudgetExceeded} sentinel (pre-allocated, no stack trace) unwinds the
 * recursion when the budget is exhausted.
 * <p>
 * {@link #cancellation} is checked the same way {@link #limits} is, at every site {@link #limits}
 * is: {@link #getSolutions}/{@link #searchStream} stop silently (matching how a limit hit already
 * behaves there); {@link #getSolution}/{@link #searchOne} throw {@link SolverCancelledException}
 * instead. This is the only call path in the whole solver chain that surfaces {@link
 * SolverCancelledException} (or {@link LimitExceededException}) to an external caller, since it's
 * the only {@code getSolution()} implementation with an algorithm of its own rather than one
 * defined purely as consuming {@link #getSolutions}'s stream.
 * <p>
 * {@link #restartRandomization} controls per-restart tie-breaking diversification: at the start of
 * each restart in {@link #getSolution}'s loop, {@code selector}'s tie-break {@link
 * java.util.Random} is reseeded via {@link RestartRandomization#randomFor}. Defaults to {@link
 * RestartRandomization#NONE}, which reseeds with {@code null} every time -- today's exact
 * behaviour (deterministic first-tied-candidate selection), unchanged. Not used by {@link
 * #getSolutions}, which never restarts.
 */
@Slf4j
@Value
@Builder
public class DomWdegLubySearch implements Solver {

    public static final int DEFAULT_LUBY_UNIT = 100;
    public static final int DEFAULT_MAX_RESTARTS = 512;

    // No @Builder.Default — defaults are set in DomWdegLubySearchBuilder below.
    int lubyUnit;
    int maxRestarts;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull SolverLimits limits;
    @NonNull NogoodStore nogoodStore;
    /**
     * Shared token every root {@link Assignment} (including every Luby restart) is seeded with,
     * rather than each starting from a fresh {@code Assignment.empty()} -- so it accumulates the
     * true cumulative counts across the whole search regardless of how it ends (solution, genuine
     * UNSAT, or a limit hit). Defaults to a fresh {@link Statistics} when not supplied; pass one in
     * (typically via {@code SolverConfig.getStatistics()}) to read it back after the call.
     */
    @NonNull Statistics statistics;
    @NonNull SolverListener listener;
    @NonNull Cancellation cancellation;
    @NonNull RestartRandomization restartRandomization;

    /** Partial builder: sets defaults and validates preconditions in {@link #build}. */
    public static class DomWdegLubySearchBuilder {
        private int lubyUnit = DEFAULT_LUBY_UNIT;
        private int maxRestarts = DEFAULT_MAX_RESTARTS;
        private SolverLimits limits = SolverLimits.unlimited();
        private NogoodStore nogoodStore = new NogoodStore();
        private Statistics statistics = new Statistics();
        private SolverListener listener = SolverListener.NONE;
        private Cancellation cancellation = Cancellation.NEVER;
        private RestartRandomization restartRandomization = RestartRandomization.NONE;

        public DomWdegLubySearch build() {
            if (lubyUnit <= 0) throw new IllegalArgumentException("lubyUnit must be positive, got: " + lubyUnit);
            if (maxRestarts <= 0) throw new IllegalArgumentException("maxRestarts must be positive, got: " + maxRestarts);
            return new DomWdegLubySearch(lubyUnit, maxRestarts, domainValuesOrderer, inference, limits, nogoodStore, statistics, listener, cancellation, restartRandomization);
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();
        private BudgetExceeded() { super(null, null, true, false); }
    }

    private static final class LimitsExceeded extends RuntimeException {
        static final LimitsExceeded INSTANCE = new LimitsExceeded();
        private LimitsExceeded() { super(null, null, true, false); }
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        var selector = new DomWdegVariableSelector(csp.getConstraints());
        long deadline = limits.deadlineNanos();
        return searchStream(csp, Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build(), selector, deadline);
    }

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        var selector = new DomWdegVariableSelector(csp.getConstraints());
        long deadline = limits.deadlineNanos();
        for (int k = 1; k <= maxRestarts; k++) {
            selector.reseedTieBreak(restartRandomization.randomFor(k));
            long budget = (long) lubyUnit * luby(k);
            int[] failures = {0};
            try {
                Assignment root = Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build();
                Optional<Assignment> result = searchOne(csp, root, selector, failures, budget, deadline);
                if (result.isPresent()) {
                    log.info("dom/wdeg+Luby: solution found at restart {}", k);
                    return result;
                }
                log.info("dom/wdeg+Luby: UNSAT confirmed at restart {}", k);
                return Optional.empty();
            } catch (BudgetExceeded ignored) {
                log.debug("dom/wdeg+Luby: budget {} exceeded at restart {}, restarting", budget, k);
                statistics.addRestarts(1);
                listener.onRestart(k);
            } catch (LimitsExceeded ignored) {
                log.info("dom/wdeg+Luby: limit exceeded at restart {}", k);
                throw new LimitExceededException(statistics);
            } catch (SolverCancelledException e) {
                log.info("dom/wdeg+Luby: cancelled at restart {}", k);
                throw e;
            }
        }
        log.warn("dom/wdeg+Luby: exhausted {} restarts without solution", maxRestarts);
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Stream<Assignment> searchStream(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull Assignment assignment,
                                            @NonNull DomWdegVariableSelector selector,
                                            long deadline) {
        if (assignment.isComplete(csp)) {
            listener.onSolutionFound(assignment);
            return Stream.of(assignment);
        }
        Variable<?> variable = selector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment)
                .flatMap(value -> {
                    Assignment next = assignment.withValue((Variable<Object>) variable, value);
                    if (limits.checkStop(cancellation, next.getStatistics().getNodesExplored().get(), deadline)
                            != SolverLimits.StopReason.NONE) {
                        return Stream.empty();
                    }
                    ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
                    if (!next.isConsistent(cspWithNogoods)) {
                        next.getStatistics().incrementBacktracks();
                        listener.onBacktrack(variable, next);
                        return Stream.empty();
                    }
                    try {
                        return inferOrExplain(cspWithNogoods, variable, next, selector)
                                .map(inferredCsp -> searchStream(inferredCsp, next, selector, deadline))
                                .orElseGet(Stream::empty);
                    } catch (SolverCancelledException e) {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Calls {@link #inference}'s {@link Inference#applyWithReason} unconditionally -- whatever
     * {@link Inference} is configured is polymorphically responsible for both propagating and, on
     * failure, explaining itself in one pass. A {@code null} {@link ConsistencyResult#reason()}
     * means the configured {@link Inference} doesn't want a nogood recorded for this failure at
     * all (see {@link Inference#withoutReasonTracking}, used to disable CDCL for a true
     * zero-explanation-cost path); this method has no fallback of its own to reach for in that
     * case, since choosing whether/how to explain is entirely {@link #inference}'s job now.
     */
    private Optional<ConstraintSatisfactionProblem> inferOrExplain(ConstraintSatisfactionProblem cspWithNogoods,
                                                                    Variable<?> variable,
                                                                    Assignment next,
                                                                    DomWdegVariableSelector selector) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next);
        if (inferred.isInfeasible()) {
            selector.incrementWeights(variable, next);
            if (inferred.reason() != null) {
                nogoodStore.record(inferred.reason());
                next.getStatistics().incrementNogoodsLearned();
                listener.onNogoodLearned(inferred.reason());
            }
            next.getStatistics().incrementBacktracks();
            listener.onBacktrack(variable, next);
            return Optional.empty();
        }
        return Optional.of(inferred.problem());
    }

    @SuppressWarnings("unchecked")
    private Optional<Assignment> searchOne(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull Assignment assignment,
                                           @NonNull DomWdegVariableSelector selector,
                                           int[] failures,
                                           long budget,
                                           long deadline) {
        if (assignment.isComplete(csp)) {
            listener.onSolutionFound(assignment);
            return Optional.of(assignment);
        }
        Variable<?> variable = selector.select(csp, assignment);
        for (Object value : domainValuesOrderer.order(csp, variable, assignment).toList()) {
            Assignment next = assignment.withValue((Variable<Object>) variable, value);
            switch (limits.checkStop(cancellation, next.getStatistics().getNodesExplored().get(), deadline)) {
                case CANCELLED -> throw new SolverCancelledException(statistics);
                case LIMIT_EXCEEDED -> throw LimitsExceeded.INSTANCE;
                case NONE -> {}
            }
            ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
            if (!next.isConsistent(cspWithNogoods)) {
                next.getStatistics().incrementBacktracks();
                listener.onBacktrack(variable, next);
                continue;
            }
            Optional<ConstraintSatisfactionProblem> inferred = inferOrExplain(cspWithNogoods, variable, next, selector);
            if (inferred.isEmpty()) {
                if (++failures[0] >= budget) throw BudgetExceeded.INSTANCE;
                continue;
            }
            Optional<Assignment> result = searchOne(inferred.get(), next, selector, failures, budget, deadline);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    /**
     * Returns the k-th term of the Luby sequence (1-indexed).
     * Sequence: 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, …
     */
    static long luby(long k) {
        long p = 1;
        while (p <= k) p <<= 1; // smallest power of 2 strictly greater than k
        if (k == p - 1) return p >>> 1; // k+1 is a power of 2 → L(k) = 2^(i-1)
        return luby(k - (p >>> 1) + 1);
    }
}
