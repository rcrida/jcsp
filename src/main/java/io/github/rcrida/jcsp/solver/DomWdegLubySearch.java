package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.solver.backtrackingsearch.ForcedAssignment;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.PhaseMemory;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.DomWdegVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Backtracking search combining the dom/wdeg variable-ordering heuristic with Luby restarts.
 * <p>
 * Each constraint has a weight (initially 1). When MAC inference causes a domain wipeout the
 * weights of active constraints on the failing variable are bumped (via
 * {@link AdaptiveVariableSelector#onConflict}). The variable selector then picks
 * {@code argmin(domainSize / weightedDegree)}, steering search away from costly failure regions.
 * Every rejected candidate here also raises {@link AdaptiveVariableSelector#onValueRejected} (last-conflict
 * reasoning, Lecoutre et al. 2009): the selector immediately re-picks the most recently failed
 * variable ahead of the ratio computation, as long as it's still unassigned, rather than waiting
 * for wdeg's own slower, aggregate weight accumulation to steer back to it. See {@link
 * DomWdegVariableSelector}'s own Javadoc for why these are two complementary mechanisms, not one
 * substituting for the other.
 * <p>
 * <b>{@link #getSolutions}</b> returns a complete lazy stream of all solutions using dom/wdeg
 * variable ordering and weight accumulation during the search — a drop-in replacement for
 * {@link io.github.rcrida.jcsp.solver.backtrackingsearch.BacktrackingSearch}. The returned
 * stream must be consumed sequentially; it shares a mutable {@link DomWdegVariableSelector}
 * across all nodes, so calling {@code .parallel()} would cause data races in the weight map.
 * <p>
 * <b>{@link #getSolution}</b> overrides the default and applies restarts: per-restart failure
 * budgets come from {@link #restartSchedule}, by default the Luby sequence 1, 1, 2, 1, 1, 2, 4, …
 * (multiplied by {@link #lubyUnit}), preserving weights across restarts so accumulated failure knowledge steers each new
 * attempt — except after {@link #STAGNANT_RESTART_LIMIT} consecutive restarts that fail to reach a
 * new deepest assignment, when {@link AdaptiveVariableSelector#onStagnation} discards them so the
 * restarts become independent draws again rather than variations on one doomed ordering (see
 * {@code docs/adr/0032-adaptive-weight-reset-on-stagnant-restarts.md}).
 * Returns {@link Optional#empty()} either when the problem is genuinely unsatisfiable
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
 * recursion when the budget is exhausted. {@link #phaseMemory} survives a restart the same way, so
 * each attempt re-enters the deepest path it previously reached rather than rediscovering it value
 * by value -- see {@link PhaseMemory}. Only {@link #getSolution} consults it; {@link #getSolutions}
 * has no restarts to recover from and keeps its existing value ordering exactly.
 * <p>
 * Both search methods check consistency <em>incrementally</em>, against {@link
 * ConstraintSatisfactionProblem#getConstraintsTouching} for the variable just assigned, rather than
 * rescanning every constraint in the problem via {@link Assignment#isConsistent}. Sound because
 * neither method ever recurses into a node whose parent assignment failed that check, so the parent
 * is consistent by induction (the root, with no values, trivially so) and extending it by one
 * variable can only newly violate a constraint that variable participates in. Every constraint is
 * therefore still checked with its final values: the check runs again each time any of its variables
 * is assigned, including the last, at which point all of them are assigned and nothing can change
 * them afterwards. The one thing this can miss is a nogood learned <em>after</em> the current prefix
 * was checked that the prefix alone already violates — which costs pruning, never correctness, since
 * a nogood is implied by the structural constraints that are fully checked regardless.
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

    /** First cutoff, in failed candidates, of {@link BranchAndBoundSolver}'s restart schedule. */
    static final long DEFAULT_RESTART_FAIL_BASE = 50;
    /** Factor by which {@link BranchAndBoundSolver}'s cutoff grows from one restart to the next. */
    static final double DEFAULT_RESTART_GROWTH = 1.3;

    /**
     * Consecutive restarts that may fail to reach a new deepest assignment before {@link
     * #getSolution} resets the selector's accumulated weights. Chosen from the corpus rather than
     * tuned: the two instances closest to being affected finish in 17 and 30 restarts in total, so
     * a limit of 32 cannot fire on either, while a stuck {@code Bibd} run plateaus for 158
     * consecutive restarts at one depth and so resets several times. See
     * <a href="../../../../../../../docs/adr/0032-adaptive-weight-reset-on-stagnant-restarts.md">ADR-0032</a>.
     */
    private static final int STAGNANT_RESTART_LIMIT = 32;

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
    /**
     * Value memory shared across every Luby restart of one {@link #getSolution} call, alongside
     * {@link #nogoodStore} and the selector's weights -- see {@link PhaseMemory}. Not consulted by
     * {@link #getSolutions}, which never restarts.
     */
    @NonNull PhaseMemory phaseMemory;
    @NonNull SolverListener listener;
    @NonNull Cancellation cancellation;
    @NonNull RestartRandomization restartRandomization;
    /** How many failed candidates each restart of {@link #getSolution} may spend; see {@link RestartSchedule}. */
    @NonNull RestartSchedule restartSchedule;
    /**
     * Builds this search's variable ordering, once per {@link #getSolutions}/{@link #getSolution}
     * call -- a factory rather than a selector because an {@link AdaptiveVariableSelector}
     * accumulates state about the search it is watching, so two calls must not share one. Defaults
     * to {@link AdaptiveVariableSelector.Factory#INSTANCE}, dom/wdeg.
     */
    AdaptiveVariableSelector.@NonNull Factory selectorFactory;

    /** Partial builder: sets defaults and validates preconditions in {@link #build}. */
    public static class DomWdegLubySearchBuilder {
        private int lubyUnit = DEFAULT_LUBY_UNIT;
        private int maxRestarts = DEFAULT_MAX_RESTARTS;
        private SolverLimits limits = SolverLimits.unlimited();
        private NogoodStore nogoodStore = new NogoodStore();
        private Statistics statistics = new Statistics();
        private PhaseMemory phaseMemory = new PhaseMemory();
        private SolverListener listener = SolverListener.NONE;
        private Cancellation cancellation = Cancellation.NEVER;
        private RestartRandomization restartRandomization = RestartRandomization.NONE;
        private RestartSchedule restartSchedule = RestartSchedule.LUBY;
        private AdaptiveVariableSelector.Factory selectorFactory = AdaptiveVariableSelector.Factory.INSTANCE;

        public DomWdegLubySearch build() {
            if (lubyUnit <= 0) throw new IllegalArgumentException("lubyUnit must be positive, got: " + lubyUnit);
            if (maxRestarts <= 0) throw new IllegalArgumentException("maxRestarts must be positive, got: " + maxRestarts);
            return new DomWdegLubySearch(lubyUnit, maxRestarts, domainValuesOrderer, inference, limits, nogoodStore, statistics, phaseMemory, listener, cancellation, restartRandomization, restartSchedule, selectorFactory);
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
        var selector = selectorFactory.createSelector(csp.getConstraints());
        long deadline = limits.deadlineNanos();
        return searchStream(csp, Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build(), selector, deadline);
    }

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        var selector = selectorFactory.createSelector(csp.getConstraints());
        long deadline = limits.deadlineNanos();
        int deepestSeen = 0;
        int stagnantRestarts = 0;
        // Fallback for a directly-constructed solver: in the full chain PropagationFixpointSolver
        // has already recorded the post-preprocessing figure, and first-write-wins keeps it.
        statistics.updateRootSearchSpace(csp.getSearchSpace());
        SearchProgress progress = new SearchProgress();
        LongSupplier budgets = restartSchedule.budgets(lubyUnit);
        for (int k = 1; k <= maxRestarts; k++) {
            progress.reset();
            if (phaseMemory.bestDepth() > deepestSeen) {
                deepestSeen = phaseMemory.bestDepth();
                stagnantRestarts = 0;
            } else if (++stagnantRestarts >= STAGNANT_RESTART_LIMIT) {
                log.debug("dom/wdeg+Luby: {} restarts without progress at restart {}, discarding weights and phase memory",
                        STAGNANT_RESTART_LIMIT, k);
                selector.onStagnation();
                // The remembered path is the stronger of the two correlators: it is the deepest one
                // seen, which is not the same as a completable one, so a dead prefix keeps being
                // replayed. Clearing deepestSeen with it lets the next restart set a fresh baseline
                // rather than counting instantly as stagnant again.
                phaseMemory.reset();
                deepestSeen = 0;
                stagnantRestarts = 0;
            }
            selector.onRestart(restartRandomization.randomFor(k));
            long budget = budgets.getAsLong();
            int[] failures = {0};
            try {
                Assignment root = Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build();
                Optional<Assignment> result = searchOne(csp, root, selector, failures, budget, deadline, 1.0, progress);
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
                // Also recorded here because cancellation can be detected inside inference rather
                // than at this class's own check; first-write-wins keeps whichever fired first.
                statistics.getRootSearchSpace()
                        .ifPresent(root -> statistics.updateRemainingSearchSpace(progress.remainingOf(root)));
                throw e;
            }
        }
        // Not Optional.empty(): every restart was cut off by its failure budget, so none of them
        // proved anything, and empty is this method's way of saying "proven unsatisfiable".
        log.warn("dom/wdeg+Luby: exhausted {} restarts without solution", maxRestarts);
        throw new RestartsExhaustedException(maxRestarts, statistics);
    }

    @SuppressWarnings("unchecked")
    private Stream<Assignment> searchStream(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull Assignment assignment,
                                            @NonNull AdaptiveVariableSelector selector,
                                            long deadline) {
        Optional<Assignment> forced = ForcedAssignment.extend(csp, assignment);
        if (forced.isEmpty()) {
            return Stream.empty();
        }
        Assignment current = forced.get();
        if (current.isComplete(csp)) {
            listener.onSolutionFound(current);
            return Stream.of(current);
        }
        Variable<?> variable = selector.select(csp, current);
        return domainValuesOrderer.order(csp, variable, current)
                .flatMap(value -> {
                    Assignment next = current.withValue((Variable<Object>) variable, value);
                    if (limits.checkStop(cancellation, next.getStatistics().getNodesExplored(), deadline)
                            != SolverLimits.StopReason.NONE) {
                        if (cancellation.isCancelled()) {
                            statistics.updateCurrentSearchSpace(csp.getSearchSpace());
                        }
                        return Stream.empty();
                    }
                    ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
                    if (!next.isConsistentAmong(cspWithNogoods.getConstraintsTouching(variable))) {
                        next.getStatistics().incrementBacktracks();
                        selector.onValueRejected(variable);
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
                                                                    AdaptiveVariableSelector selector) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next);
        if (inferred.isInfeasible()) {
            selector.onConflict(variable, next);
            selector.onValueRejected(variable);
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

    /**
     * One node of {@link #searchOne}: the problem and assignment it was entered with, the variable
     * chosen there, and how far through that variable's candidate values the search has got.
     */
    private static final class Frame {
        final ConstraintSatisfactionProblem csp;
        final Assignment current;
        final Variable<?> variable;
        final List<Object> candidates;
        final double childWeight;
        int next;

        Frame(ConstraintSatisfactionProblem csp, Assignment current, Variable<?> variable,
              List<Object> candidates, double weight) {
            this.csp = csp;
            this.current = current;
            this.variable = variable;
            this.candidates = candidates;
            this.childWeight = weight / candidates.size();
        }
    }

    /**
     * A depth-first search over an explicit stack of {@link Frame}s rather than recursion: the depth
     * is the number of branching decisions, which for a problem with thousands of variables exceeds
     * what a thread's stack holds (about 3,500 levels on the default 2MB), and a stack overflow is
     * not something a caller can recover a search from. {@link #searchStream}, the lazy
     * {@link #getSolutions} path, is still recursive.
     * <p>
     * A candidate resolved without a solution calls {@code progress.complete(childWeight)} exactly
     * once, wherever it ends: immediately for a rejected or failed value, or when the child node it
     * led to failed outright or ran out of candidates.
     */
    @SuppressWarnings("unchecked")
    private Optional<Assignment> searchOne(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull Assignment assignment,
                                           @NonNull AdaptiveVariableSelector selector,
                                           int[] failures,
                                           long budget,
                                           long deadline,
                                           double weight,
                                           SearchProgress progress) {
        Deque<Frame> stack = new ArrayDeque<>();
        ConstraintSatisfactionProblem enterCsp = csp;
        Assignment enterFrom = assignment;
        double enterWeight = weight;
        while (true) {
            if (enterCsp != null) {
                Optional<Assignment> forced = ForcedAssignment.extend(enterCsp, enterFrom);
                if (forced.isPresent()) {
                    Assignment current = forced.get();
                    if (current.isComplete(enterCsp)) {
                        listener.onSolutionFound(current);
                        return Optional.of(current);
                    }
                    Variable<?> variable = selector.select(enterCsp, current);
                    List<Object> candidates = phaseMemory.prioritise(variable,
                            (List<Object>) domainValuesOrderer.order(enterCsp, variable, current).toList());
                    stack.push(new Frame(enterCsp, current, variable, candidates, enterWeight));
                } else if (!stack.isEmpty()) {
                    progress.complete(stack.peek().childWeight);
                }
                enterCsp = null;
            }
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            Frame frame = stack.peek();
            if (frame.next == frame.candidates.size()) {
                stack.pop();
                if (!stack.isEmpty()) {
                    progress.complete(stack.peek().childWeight);
                }
                continue;
            }
            Object value = frame.candidates.get(frame.next++);
            Assignment next = frame.current.withValue((Variable<Object>) frame.variable, value);
            switch (limits.checkStop(cancellation, next.getStatistics().getNodesExplored(), deadline)) {
                case CANCELLED -> {
                    statistics.updateCurrentSearchSpace(frame.csp.getSearchSpace());
                    statistics.getRootSearchSpace()
                            .ifPresent(root -> statistics.updateRemainingSearchSpace(progress.remainingOf(root)));
                    throw new SolverCancelledException(statistics);
                }
                case LIMIT_EXCEEDED -> throw LimitsExceeded.INSTANCE;
                case NONE -> {}
            }
            ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(frame.csp);
            if (!next.isConsistentAmong(cspWithNogoods.getConstraintsTouching(frame.variable))) {
                next.getStatistics().incrementBacktracks();
                selector.onValueRejected(frame.variable);
                listener.onBacktrack(frame.variable, next);
                progress.complete(frame.childWeight);
                continue;
            }
            Optional<ConstraintSatisfactionProblem> inferred =
                    inferOrExplain(cspWithNogoods, frame.variable, next, selector);
            if (inferred.isEmpty()) {
                progress.complete(frame.childWeight);
                if (++failures[0] >= budget) throw BudgetExceeded.INSTANCE;
                continue;
            }
            phaseMemory.recordIfDeepest(next);
            enterCsp = inferred.get();
            enterFrom = next;
            enterWeight = frame.childWeight;
        }
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
