package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
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
 * used without completing a full traversal; the two cases are not distinguished.
 * A lightweight {@link BudgetExceeded} sentinel (pre-allocated, no stack trace) unwinds the
 * recursion when the budget is exhausted.
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

    /** Partial builder: sets defaults and validates preconditions in {@code build()}. */
    public static class DomWdegLubySearchBuilder {
        private int lubyUnit = DEFAULT_LUBY_UNIT;
        private int maxRestarts = DEFAULT_MAX_RESTARTS;

        public DomWdegLubySearch build() {
            if (lubyUnit <= 0) throw new IllegalArgumentException("lubyUnit must be positive, got: " + lubyUnit);
            if (maxRestarts <= 0) throw new IllegalArgumentException("maxRestarts must be positive, got: " + maxRestarts);
            return new DomWdegLubySearch(lubyUnit, maxRestarts, domainValuesOrderer, inference);
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();
        private BudgetExceeded() { super(null, null, true, false); }
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        var selector = new DomWdegVariableSelector(csp.getConstraints());
        return searchStream(csp, Assignment.empty(), selector);
    }

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        var selector = new DomWdegVariableSelector(csp.getConstraints());
        for (int k = 1; k <= maxRestarts; k++) {
            long budget = (long) lubyUnit * luby(k);
            int[] failures = {0};
            try {
                Optional<Assignment> result = searchOne(csp, Assignment.empty(), selector, failures, budget);
                if (result.isPresent()) {
                    log.info("dom/wdeg+Luby: solution found at restart {}", k);
                    result.get().getStatistics().addRestarts(k - 1);
                    return result;
                }
                log.info("dom/wdeg+Luby: UNSAT confirmed at restart {}", k);
                return Optional.empty();
            } catch (BudgetExceeded ignored) {
                log.debug("dom/wdeg+Luby: budget {} exceeded at restart {}, restarting", budget, k);
            }
        }
        log.warn("dom/wdeg+Luby: exhausted {} restarts without solution", maxRestarts);
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Stream<Assignment> searchStream(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull Assignment assignment,
                                            @NonNull DomWdegVariableSelector selector) {
        if (assignment.isComplete(csp)) return Stream.of(assignment);
        Variable<?> variable = selector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment)
                .flatMap(value -> {
                    Assignment next = assignment.withValue((Variable<Object>) variable, value);
                    if (!next.isConsistent(csp)) {
                        next.getStatistics().incrementBacktracks();
                        return Stream.empty();
                    }
                    Optional<ConstraintSatisfactionProblem> inferred = inference.apply(csp, variable, next);
                    if (inferred.isEmpty()) {
                        selector.incrementWeights(csp, variable, next);
                        next.getStatistics().incrementBacktracks();
                        return Stream.empty();
                    }
                    return searchStream(inferred.get(), next, selector);
                });
    }

    @SuppressWarnings("unchecked")
    private Optional<Assignment> searchOne(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull Assignment assignment,
                                           @NonNull DomWdegVariableSelector selector,
                                           int[] failures,
                                           long budget) {
        if (assignment.isComplete(csp)) return Optional.of(assignment);
        Variable<?> variable = selector.select(csp, assignment);
        for (Object value : domainValuesOrderer.order(csp, variable, assignment).toList()) {
            Assignment next = assignment.withValue((Variable<Object>) variable, value);
            if (!next.isConsistent(csp)) {
                next.getStatistics().incrementBacktracks();
                continue;
            }
            Optional<ConstraintSatisfactionProblem> inferred = inference.apply(csp, variable, next);
            if (inferred.isEmpty()) {
                selector.incrementWeights(csp, variable, next);
                next.getStatistics().incrementBacktracks();
                if (++failures[0] >= budget) throw BudgetExceeded.INSTANCE;
                continue;
            }
            Optional<Assignment> result = searchOne(inferred.get(), next, selector, failures, budget);
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
