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
import io.github.rcrida.jcsp.domains.Domain;
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
 * subtree immediately once {@link #lowerBound} -- {@link #intervalLowerBound}'s interval-arithmetic
 * bound over every open {@link BoundedDomain} variable's own current range when {@link #objective}
 * is a {@link LinearObjective}, or {@link #partialAssignmentLowerBound}'s weaker "only variables
 * already singleton" fallback otherwise -- already meets or exceeds it -- mirroring {@link
 * BranchAndBoundSolver#search}'s own
 * {@code objective.applyAsDouble(assignment) >= incumbent[0]} check, and relying on the same
 * pre-existing contract ({@link Solver.Factory#createSolver(ConstraintSatisfactionProblem,
 * ToDoubleFunction)}'s objective "must return a lower bound on the cost of any completion of a
 * partial assignment"). Without this, several {@link BoundedDomain} variables whose tight bounds
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
        return getSolutions(csp, Double.MAX_VALUE);
    }

    /**
     * Package-private overload letting a same-package caller seed the incumbent {@link #allFeasible}
     * threads through its recursion, instead of always starting from "nothing found yet" ({@link
     * Double#MAX_VALUE}). Used by {@link BranchAndBoundSolver#resolveContinuousResidual} to carry its
     * own already-known incumbent into a per-leaf bisection fallback, so a residual that can't
     * possibly beat a bound already found elsewhere in the search tree is pruned immediately instead
     * of being fully (and uselessly) explored. {@link #getSolutions(ConstraintSatisfactionProblem)}
     * itself is unaffected -- it always seeds {@link Double#MAX_VALUE}.
     */
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp, double incumbentSeed) {
        val target = findWidestBounded(csp);
        if (target == null) {
            return getInner().getSolutions(csp);
        }
        return decomposedSolutions(csp, incumbentSeed).orElseGet(() -> allFeasible(csp, new double[]{incumbentSeed}));
    }

    /**
     * When {@link #objective} is a {@link LinearObjective} and {@code csp}'s still-open {@link
     * BoundedDomain} variables split into independent components (see {@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem#decomposeSubproblems(java.util.function.Predicate)}),
     * solves each component's own residual independently and combines them -- avoiding {@link
     * #allFeasible}'s single combined tree, whose size is exponential in the <em>total</em> open
     * variable count even when several of those variables have no constraint coupling them to each
     * other at all (a common shape for {@link BranchAndBoundSolver#resolveContinuousResidual}: many
     * already-singleton discrete variables threading every constraint, with the still-open
     * continuous ones only weakly, independently coupled through them). {@link Optional#empty()}
     * means "not decomposable" (or the objective isn't a {@link LinearObjective}), signalling the
     * caller to fall back to {@link #allFeasible} unchanged.
     * <p>
     * Each component's own stream is obtained via a fresh, unseeded ({@link Double#MAX_VALUE})
     * recursive call to this same method, then combined via the same {@link LazyList}-backed
     * cross-product {@link IndependentSubproblemSolver#getSolutions} already uses (safe to reuse
     * here: {@link Assignment#merge} is safe across two assignments with disjoint variable sets,
     * which independent components are by construction) -- {@code LinearObjective#applyAsDouble}
     * already treats a coefficient variable absent from an assignment as contributing {@code 0}, so
     * no per-component objective splitting is needed: the same, full {@link #objective} is handed to
     * every component's own recursive solve unchanged. Running the combined stream through the exact
     * incumbent filter {@link #allFeasible} uses (seeded with {@code incumbentSeed}, not {@link
     * Double#MAX_VALUE}) is what recovers this class's own "each element strictly better than the
     * previous" contract despite the cross-product's own visiting order having no relationship to
     * combined objective value -- the same reasoning: a filter admitting only strict improvements
     * over a monotonically-ratcheting incumbent produces a strictly-improving output regardless of
     * the order candidates are offered to it in.
     */
    private Optional<Stream<Assignment>> decomposedSolutions(@NonNull ConstraintSatisfactionProblem csp, double incumbentSeed) {
        if (!(objective instanceof LinearObjective)) {
            return Optional.empty();
        }
        if (lowerBound(csp) >= incumbentSeed) {
            return Optional.of(Stream.empty());
        }
        val components = csp.decomposeSubproblems(v -> csp.getDomain(v) instanceof BoundedDomain<?> bd && !bd.isSingleton());
        if (components.isEmpty()) {
            return Optional.empty();
        }
        log.debug("Residual decomposes into {} independent components", components.get().size());

        double[] incumbent = {incumbentSeed};
        Stream<Assignment> combined = components.get().stream()
                .map(sub -> new LazyList<>(getSolutions(sub, Double.MAX_VALUE)))
                .reduce((ll1, ll2) -> new LazyList<>(ll1.stream().flatMap(a1 -> ll2.stream().map(a1::merge))))
                .map(LazyList::stream)
                .orElse(Stream.empty());
        return Optional.of(combined.filter(candidate -> {
            double cost = objective.applyAsDouble(candidate);
            if (cost < incumbent[0]) {
                incumbent[0] = cost;
                return true;
            }
            return false;
        }));
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
        if (lowerBound(csp) >= incumbent[0]) {
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
     * A valid lower bound on any completion of {@code csp}. When {@link #objective} is a {@link
     * LinearObjective}, uses {@link #intervalLowerBound} -- interval arithmetic over every open
     * {@link BoundedDomain} variable's own current bounds, not just variables already singleton --
     * since that bound is strictly tighter (it can never be looser: a singleton is the degenerate
     * case of an interval whose min equals its max) and is what {@link #allFeasible}'s incumbent
     * pruning actually needs to cut a branch before every variable in it happens to have collapsed.
     * Falls back to {@link #partialAssignmentLowerBound} for an opaque {@link ToDoubleFunction} that
     * can't be introspected for per-variable coefficients.
     */
    private double lowerBound(ConstraintSatisfactionProblem csp) {
        return objective instanceof LinearObjective linearObjective
                ? intervalLowerBound(csp, linearObjective)
                : partialAssignmentLowerBound(csp);
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

    /**
     * A lower bound on {@code linearObjective} over {@code csp}'s current domains, sound for any
     * coefficient sign: each term contributes {@code coefficient * domain.min} when the coefficient
     * is non-negative or {@code coefficient * domain.max} otherwise -- whichever extreme of the
     * variable's own current range minimises that term -- summed with the objective's constant.
     * Unlike {@link #partialAssignmentLowerBound}, this reads every referenced variable's current
     * {@link Domain} (via {@link #termLowerBound}) rather than skipping every variable that isn't
     * yet singleton, so it tightens incrementally as {@link #allFeasible}'s bisection narrows each
     * variable's bounds, not just at the moment a variable collapses to a point. A coefficient
     * variable {@code csp} doesn't itself contain is skipped, mirroring {@code LinearObjective}'s own
     * "absent contributes 0" convention -- real for {@link #decomposedSolutions}'s per-component
     * calls, where {@code linearObjective} is deliberately the full, unrestricted objective but
     * {@code csp} only ever contains one component's own variables; {@link
     * ConstraintSatisfactionProblem#getDomain} throws rather than returning empty for a variable it
     * doesn't contain, so this can't just delegate to {@link #termLowerBound} unconditionally the way
     * it originally did.
     */
    private double intervalLowerBound(ConstraintSatisfactionProblem csp, LinearObjective linearObjective) {
        double total = linearObjective.getConstant();
        for (var entry : linearObjective.getCoefficients().entrySet()) {
            if (!csp.getVariableDomains().containsKey(entry.getKey())) {
                continue;
            }
            total += termLowerBound(csp, entry.getKey(), entry.getValue());
        }
        return total;
    }

    private <N extends Number> double termLowerBound(ConstraintSatisfactionProblem csp, Variable<N> variable, double coefficient) {
        Domain<N> domain = csp.getDomain(variable);
        if (domain.isSingleton()) {
            return coefficient * domain.singleValue().orElseThrow().doubleValue();
        }
        BoundedDomain<N> bounded = (BoundedDomain<N>) domain;
        double bound = coefficient >= 0 ? bounded.getMin().doubleValue() : bounded.getMax().doubleValue();
        return coefficient * bound;
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
