package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles {@link BoundedDomain} variables that do not collapse to singletons during propagation
 * by recursively bisecting the widest non-singleton interval and re-propagating until all
 * intervals are within epsilon of their midpoint. Only present in the optimization chain;
 * {@code inner} is a {@link BranchAndBoundSolver} that handles any remaining discrete variables.
 *
 * <p>{@link #getSolutions(ConstraintSatisfactionProblem)} explores all feasible points via
 * bisection and returns them in improving objective order (each strictly better than the previous).
 *
 * <p>{@link #allFeasible} threads a shared {@code incumbent} through its recursion and prunes a
 * subtree immediately once {@code objective} evaluated against a <em>partial</em> {@link Assignment}
 * of whichever variables are currently singleton (see {@link #partialAssignmentLowerBound}) already
 * meets or exceeds it -- mirroring {@link BranchAndBoundSolver#search}'s own
 * {@code objective.applyAsDouble(assignment) >= incumbent[0]} check, and relying on the same
 * pre-existing contract ({@link Solver.Factory#createSolver(ConstraintSatisfactionProblem,
 * ToDoubleFunction)}'s objective "must return a lower bound on the cost of any completion of a
 * partial assignment"). Without this, several {@code BoundedDomain} variables whose tight bounds
 * depend on other, still-unresolved discrete variables force every bisection split to explore both
 * halves regardless of cost, with every leaf re-running the entire inner discrete search from
 * scratch.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BisectionConditioningSolver extends SolverDecorator {

    private static final List<ConstraintConsistency> REPROPAGATORS = List.of(
            FixpointConsistency.of(SumBoundConstraint.class),
            FixpointConsistency.of(SumVariableConstraint.class),
            FixpointConsistency.of(LinearBoundConstraint.class),
            FixpointConsistency.of(LinearVariableConstraint.class)
    );

    @NonNull ToDoubleFunction<Assignment> objective;
    double epsilon;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        val target = findWidestBounded(csp);
        if (target == null) {
            return getInner().getSolutions(csp);
        }
        return allFeasible(csp, new double[]{Double.MAX_VALUE});
    }

    /**
     * Explicitly pinned to {@code getSolutions(csp).findFirst()} rather than inheriting
     * {@link SolverDecorator}'s default (which delegates to {@code inner.getSolution} and would
     * skip this class's own bisection logic in {@link #getSolutions} entirely, since that logic
     * lives outside the {@code preprocess}-then-delegate pattern the base default assumes).
     */
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    private Stream<Assignment> allFeasible(@NonNull ConstraintSatisfactionProblem csp, double[] incumbent) {
        if (partialAssignmentLowerBound(csp) >= incumbent[0]) {
            return Stream.empty();
        }
        val target = findWidestBounded(csp);
        if (target == null) {
            // All bounded domains are singletons. If fully determined, validate and return the
            // forced assignment; otherwise delegate remaining discrete variables to inner.
            return (csp.isFullyDetermined() ? forcedSolution(csp).stream() : getInner().getSolutions(csp))
                    .filter(candidate -> {
                        double cost = objective.applyAsDouble(candidate);
                        if (cost < incumbent[0]) {
                            incumbent[0] = cost;
                            return true;
                        }
                        return false;
                    });
        }
        val bd = (BoundedDomain<?>) csp.getDomain(target);
        double lo = bd.getMin().doubleValue();
        double hi = bd.getMax().doubleValue();
        double mid = (lo + hi) / 2.0;
        if (hi - lo <= epsilon) {
            log.debug("Snapping {} to {}", target, mid);
            return allFeasible(withSnapped(csp, target, mid), incumbent);
        }
        log.debug("Bisecting {} at {} in [{}, {}]", target, mid, lo, hi);
        return Stream.concat(
                narrow(csp, target, lo, mid).stream().flatMap(c -> allFeasible(c, incumbent)),
                narrow(csp, target, mid, hi).stream().flatMap(c -> allFeasible(c, incumbent))
        );
    }

    /**
     * A valid lower bound on any completion of {@code csp}: {@link #objective} evaluated against a
     * partial {@link Assignment} of only the variables that are currently singleton, relying on the
     * same "unassigned contributes nothing yet" convention {@link BranchAndBoundSolver} already
     * requires of every optimization objective in this codebase.
     */
    private double partialAssignmentLowerBound(ConstraintSatisfactionProblem csp) {
        val values = csp.getVariableDomains().entrySet().stream()
                .filter(e -> e.getValue().isSingleton())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().singleValue().orElseThrow()));
        return objective.applyAsDouble(Assignment.of(values));
    }

    @Nullable
    static Variable<?> findWidestBounded(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> e.getValue() instanceof BoundedDomain<?> bd && !bd.isSingleton())
                .max(Comparator.comparingDouble(e ->
                        ((BoundedDomain<?>) e.getValue()).getMax().doubleValue()
                        - ((BoundedDomain<?>) e.getValue()).getMin().doubleValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    static ConstraintSatisfactionProblem withSnapped(ConstraintSatisfactionProblem csp,
                                                     Variable<?> target, double mid) {
        return csp.toBuilder()
                .variableDomainEntry((Variable) target, IntervalDomain.of(mid, mid))
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<ConstraintSatisfactionProblem> narrow(ConstraintSatisfactionProblem csp,
                                                                   Variable<?> target,
                                                                   double lo, double hi) {
        BoundedDomain<?> bounded = (BoundedDomain<?>) csp.getDomain(target);
        var narrowedDomain = bounded.withBounds(lo, hi);
        return repropagate(csp.toBuilder()
                .variableDomainEntry((Variable) target, narrowedDomain)
                .build());
    }

    private static Optional<ConstraintSatisfactionProblem> repropagate(ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            double widthBefore = boundedWidth(current);
            for (var propagator : REPROPAGATORS) {
                var next = propagator.apply(current);
                if (next.isEmpty()) return Optional.empty();
                current = next.get();
            }
            changed = boundedWidth(current) < widthBefore;
        }
        return Optional.of(current);
    }

    private static double boundedWidth(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream()
                .filter(BoundedDomain.class::isInstance)
                .mapToDouble(d -> {
                    BoundedDomain<?> bd = (BoundedDomain<?>) d;
                    return bd.getMax().doubleValue() - bd.getMin().doubleValue();
                })
                .sum();
    }
}
