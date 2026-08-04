package io.github.rcrida.jcsp.solver;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.lp.LpBound;
import io.github.rcrida.jcsp.solver.lp.LpModelBuilder;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * An optimization solver that applies branch-and-bound pruning. Whenever the cost of the current
 * partial assignment meets or exceeds the best complete solution found so far (the
 * <em>incumbent</em>), the branch is cut immediately.
 *
 * <p>Returns a stream of improving complete assignments (each strictly better than the previous);
 * the last element is the global optimum found within the search.
 *
 * <p>The objective is supplied at construction time by {@link Solver.Factory#createSolver(ConstraintSatisfactionProblem, ToDoubleFunction)},
 * so it must return a lower bound on the cost of any completion of a partial assignment.
 *
 * <p>Like {@link DomWdegLubySearch}, folds a shared {@link #nogoodStore} into every node: each
 * candidate value is checked and propagated against {@code nogoodStore.apply(csp)} rather than the
 * bare {@code csp}, and {@link #inference}'s {@link Inference#applyWithReason} is used instead of
 * plain {@link Inference#apply} so a domain wipeout's reason (when one can be derived) is recorded
 * back into the store. This is orthogonal to the incumbent-bound pruning above: a nogood records a
 * genuine constraint violation (permanently true regardless of the incumbent), while the bound cut
 * records a cost dominance relative to the current incumbent -- the two prunings compose freely.
 * <p>
 * {@link #cancellation} is checked at the same site as {@link #limits}, and behaves the same way a
 * limit hit does: the affected candidate is filtered out, so the stream simply yields whatever
 * improving solutions were already found. Unlike {@link DomWdegLubySearch}, this class has no
 * distinct single-solution algorithm of its own -- {@code getSolution()} just consumes this
 * stream -- so it never throws {@link SolverCancelledException}; the caller gets back the best
 * incumbent found so far, exactly like today's {@link LimitExceededException} asymmetry.
 * <p>
 * When {@link #objective} is itself a {@link LinearObjective}, the plain {@code
 * objective.applyAsDouble(partial) >= incumbent} pruning check for non-complete assignments is
 * replaced with a single per-node {@link io.github.rcrida.jcsp.solver.lp.LpModelBuilder#solve} call
 * (see ADR-0009), reused for two purposes: (1) a strictly tighter (never looser) bound than the
 * plain check under the same non-negative-coefficients/non-negative-domain assumption it already
 * requires, with an immediate prune on LP infeasibility -- a relaxation's infeasibility already
 * proves the unrelaxed subtree is infeasible too; and (2), when not pruned, choosing which variable
 * to branch on next: the currently-unassigned variable whose LP-relaxed value is farthest from an
 * integer (standard MIP "most fractional" branching), falling back to {@link
 * #unassignedVariableSelector} when every LP-covered unassigned variable already has an integral
 * value, or none are LP-covered at all. This only changes <em>which</em> variable is decided next,
 * not how its domain is split -- unlike textbook MIP branching's binary {@code x<=floor(v)}/
 * {@code x>=ceil(v)} children, this class still enumerates {@link #domainValuesOrderer}'s full
 * ordering for whichever variable is chosen, so the benefit is search-order quality, not a
 * fundamentally tighter per-child bound. No separate opt-in is needed: {@link Solver.Factory}
 * passing a {@link LinearObjective} as {@code objective} (it implements {@link
 * ToDoubleFunction}{@code <Assignment>}) is what triggers this. Complete assignments are unaffected
 * -- their real cost is exact, so relaxing it would only add solve cost for no benefit. The rest of
 * the optimization chain (e.g. {@link BisectionConditioningSolver} still running before this class)
 * is unaffected; narrowing that to account for a joint LP relaxation is deferred future work.
 */
@Slf4j
@Value
@Builder
public class BranchAndBoundSolver implements Solver {
    /** Below this, an LP-relaxed value is treated as already integral rather than fractional. */
    private static final double FRACTIONAL_EPSILON = 1e-6;

    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull ToDoubleFunction<Assignment> objective;
    @Builder.Default
    @NonNull SolverLimits limits = SolverLimits.unlimited();
    @Builder.Default
    @NonNull NogoodStore nogoodStore = new NogoodStore();
    /**
     * Shared token the root {@link Assignment} is seeded with (instead of a fresh {@code
     * Assignment.empty()}), so it's readable via {@code SolverConfig.getStatistics()} after the
     * call regardless of whether an improving solution was ever found.
     */
    @Builder.Default
    @NonNull Statistics statistics = new Statistics();
    @Builder.Default
    @NonNull SolverListener listener = SolverListener.NONE;
    @Builder.Default
    @NonNull Cancellation cancellation = Cancellation.NEVER;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        long deadline = limits.deadlineNanos();
        return search(csp, Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build(), incumbent, deadline);
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       double[] incumbent,
                                       long deadline) {
        if (assignment.isComplete(csp)) {
            double cost = objective.applyAsDouble(assignment);
            if (cost >= incumbent[0]) {
                return Stream.empty();
            }
            incumbent[0] = cost;
            log.info("Found improving solution with cost {}: {}", cost, assignment);
            listener.onIncumbentImproved(assignment, cost);
            return Stream.of(assignment);
        }
        Variable<?> variable;
        if (objective instanceof LinearObjective linearObjective) {
            Optional<LpBound> bound = LpModelBuilder.solve(csp, linearObjective);
            if (bound.isEmpty() || bound.get().lowerBound() >= incumbent[0]) {
                return Stream.empty();
            }
            variable = selectFractionalVariable(assignment, bound.get())
                    .orElseGet(() -> unassignedVariableSelector.select(csp, assignment));
        } else {
            if (objective.applyAsDouble(assignment) >= incumbent[0]) {
                return Stream.empty();
            }
            variable = unassignedVariableSelector.select(csp, assignment);
        }
        return searchValues(variable, csp, assignment, incumbent, deadline);
    }

    /**
     * Among {@code bound}'s LP-covered variables that {@code assignment} hasn't decided yet, picks
     * the one whose relaxed value is farthest from an integer ({@code min(frac, 1-frac)}, maximal at
     * a half-integer) -- standard MIP "most fractional" branching. {@link Optional#empty()} when no
     * unassigned variable is LP-covered, or every covered one is already within {@link
     * #FRACTIONAL_EPSILON} of an integer, letting the caller fall back to {@link
     * #unassignedVariableSelector}.
     */
    private Optional<Variable<?>> selectFractionalVariable(Assignment assignment, LpBound bound) {
        Variable<?> best = null;
        double bestFractionality = FRACTIONAL_EPSILON;
        for (var entry : bound.solution().entrySet()) {
            Variable<?> variable = entry.getKey();
            if (assignment.getValue(variable).isPresent()) {
                continue;
            }
            double value = entry.getValue();
            double fractionality = Math.min(value - Math.floor(value), Math.ceil(value) - value);
            if (fractionality > bestFractionality) {
                bestFractionality = fractionality;
                best = variable;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Wildcard-capture helper: binds {@code T} so that {@code getDomain(variable)} and
     * {@code withValue(variable, value)} share the same type argument, avoiding an unchecked cast.
     */
    private <T> Stream<Assignment> searchValues(Variable<T> variable,
                                                 ConstraintSatisfactionProblem csp,
                                                 Assignment assignment,
                                                 double[] incumbent,
                                                 long deadline) {
        ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
        return domainValuesOrderer.order(csp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> {
                    if (limits.checkStop(cancellation, next.getStatistics().getNodesExplored().get(), deadline)
                            != SolverLimits.StopReason.NONE) {
                        return false;
                    }
                    if (!next.isConsistent(cspWithNogoods)) {
                        next.getStatistics().incrementBacktracks();
                        listener.onBacktrack(variable, next);
                        return false;
                    }
                    return true;
                })
                .flatMap(next -> {
                    try {
                        return inferOrExplain(cspWithNogoods, variable, next)
                                .map(inferred -> search(inferred, next, incumbent, deadline))
                                .orElseGet(Stream::empty);
                    } catch (SolverCancelledException e) {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Calls {@link #inference}'s {@link Inference#applyWithReason} unconditionally, mirroring
     * {@link DomWdegLubySearch#inferOrExplain}: whatever {@link Inference} is configured is
     * polymorphically responsible for both propagating and, on failure, explaining itself in one
     * pass. A {@code null} {@link ConsistencyResult#reason()} means the configured {@link
     * Inference} doesn't want a nogood recorded for this failure (see
     * {@link Inference#withoutReasonTracking}) -- this method has no fallback of its own for that
     * case, since choosing whether/how to explain is entirely {@link #inference}'s job.
     */
    private Optional<ConstraintSatisfactionProblem> inferOrExplain(ConstraintSatisfactionProblem cspWithNogoods,
                                                                     Variable<?> variable,
                                                                     Assignment next) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next);
        if (inferred.isInfeasible()) {
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
}
