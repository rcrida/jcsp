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
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
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
 */
@Slf4j
@Value
@Builder
public class BranchAndBoundSolver implements Solver {
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
     * Assignment.empty()}), so it's readable via {@link SolverConfig#getStatistics()} after the
     * call regardless of whether an improving solution was ever found.
     */
    @Builder.Default
    @NonNull Statistics statistics = new Statistics();

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        long deadline = limits.deadlineNanos();
        return search(csp, Assignment.builder().statistics(statistics).build(), incumbent, deadline);
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       double[] incumbent,
                                       long deadline) {
        if (objective.applyAsDouble(assignment) >= incumbent[0]) {
            return Stream.empty();
        }
        if (assignment.isComplete(csp)) {
            double cost = objective.applyAsDouble(assignment);
            incumbent[0] = cost;
            log.info("Found improving solution with cost {}: {}", cost, assignment);
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return searchValues(variable, csp, assignment, incumbent, deadline);
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
                    if (limits.isNodeLimitExceeded(next.getStatistics().getNodesExplored().get())
                            || limits.isTimeLimitExceeded(deadline)) {
                        limits.markLimitReached();
                        return false;
                    }
                    if (!next.isConsistent(cspWithNogoods)) {
                        next.getStatistics().incrementBacktracks();
                        return false;
                    }
                    return true;
                })
                .flatMap(next -> inferOrExplain(cspWithNogoods, variable, next)
                        .map(inferred -> search(inferred, next, incumbent, deadline))
                        .orElseGet(Stream::empty));
    }

    /**
     * Calls {@link #inference}'s {@link Inference#applyWithReason} unconditionally, mirroring
     * {@link DomWdegLubySearch#inferOrExplain}: whatever {@link Inference} is configured is
     * polymorphically responsible for both propagating and, on failure, explaining itself in one
     * pass. A {@code null} {@link ConsistencyResult#reason()} means the configured {@link
     * Inference} doesn't want a nogood recorded for this failure (see
     * {@link Inference#withoutReasonTracking}) -- this method has no fallback of its own for that
     * case, since choosing whether/how to explain is entirely {@code inference}'s job.
     */
    private Optional<ConstraintSatisfactionProblem> inferOrExplain(ConstraintSatisfactionProblem cspWithNogoods,
                                                                     Variable<?> variable,
                                                                     Assignment next) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next);
        if (inferred.isInfeasible()) {
            if (inferred.reason() != null) {
                nogoodStore.record(inferred.reason());
                next.getStatistics().incrementNogoodsLearned();
            }
            next.getStatistics().incrementBacktracks();
            return Optional.empty();
        }
        return Optional.of(inferred.problem());
    }
}
