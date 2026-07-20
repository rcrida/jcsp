package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DivisionConstraint;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.DiffnConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint;
import io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.RegularConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs AC3, AllDiff GAC, SumConstraint bounds propagation, LinearConstraint bounds propagation,
 * CountConstraint value propagation, InverseConstraint arc consistency, and AmongConstraint
 * value-set propagation in a combined fixpoint loop.
 *
 * <p>The propagators are not independent: AllDiff GAC can expose naked pairs that AC3 then
 * propagates to neighbouring constraints; sum, linear, count, inverse, and among propagation
 * tightens domains that AC3 and AllDiff GAC can then exploit further. Running each once misses
 * this feedback. This solver iterates until none of the seven makes further progress,
 * or exits immediately with {@link Optional#empty()} as soon as any propagator
 * detects infeasibility.
 *
 * <p>To add a new propagator for a {@link io.github.rcrida.jcsp.consistency.Propagatable} constraint
 * type, append {@code FixpointConsistency.of(MyConstraint.class)} to {@link #PROPAGATORS}.
 *
 * <p>When {@code snap} is true (satisfaction mode with {@link BoundedDomain} variables), any
 * non-singleton bounded domain remaining after propagation is snapped to its interval midpoint,
 * giving one concrete solution for underdetermined continuous systems. When {@code snap} is false
 * (optimization mode), intervals are left open so that a downstream {@link BisectionConditioningSolver}
 * can explore the feasible region.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PropagationFixpointSolver extends SolverDecorator {

    private static final List<ConstraintConsistency> PROPAGATORS = List.of(
            FixpointConsistency.of(UnaryComparatorConstraint.class),
            FixpointConsistency.of(BinaryComparatorConstraint.class),
            FixpointConsistency.of(BinaryOffsetConstraint.class),
            FixpointConsistency.of(AbsoluteDifferenceConstraint.class),
            AC3.INSTANCE,
            NogoodFixpointConsistency.INSTANCE,
            FixpointConsistency.of(AllDiffConstraint.class),
            FixpointConsistency.of(SumConstraint.class),
            FixpointConsistency.of(LinearConstraint.class),
            FixpointConsistency.of(CountConstraint.class),
            FixpointConsistency.of(InverseConstraint.class),
            FixpointConsistency.of(AmongConstraint.class),
            FixpointConsistency.of(AtLeastNConstraint.class),
            FixpointConsistency.of(AtMostNConstraint.class),
            FixpointConsistency.of(AtMostOneConstraint.class),
            FixpointConsistency.of(CumulativeConstraint.class),
            FixpointConsistency.of(GlobalCardinalityConstraint.class),
            FixpointConsistency.of(LexConstraint.class),
            FixpointConsistency.of(MaxConstraint.class),
            FixpointConsistency.of(MinConstraint.class),
            FixpointConsistency.of(NaryElementConstraint.class),
            FixpointConsistency.of(NaryTuplesConstraint.class),
            FixpointConsistency.of(ProductConstraint.class),
            FixpointConsistency.of(DivisionConstraint.class),
            FixpointConsistency.of(CircuitConstraint.class),
            FixpointConsistency.of(DiffnConstraint.class),
            FixpointConsistency.of(RegularConstraint.class),
            FixpointConsistency.of(IncreasingConstraint.class),
            FixpointConsistency.of(DecreasingConstraint.class),
            FixpointConsistency.of(ReifiedConstraint.class),
            FixpointConsistency.of(ImplicationConstraint.class)
    );

    /** When true, snaps non-singleton bounded domains to midpoints after propagation converges. */
    boolean snap;

    /**
     * Runs the full propagator fixpoint without snapping bounded domains, with no seed hint for
     * round 1 (used only for preprocessing, before any search node exists to derive one from).
     * See {@link #applyFixpoint(ConstraintSatisfactionProblem, Set)}.
     */
    static Optional<ConstraintSatisfactionProblem> applyFixpoint(
            @NonNull ConstraintSatisfactionProblem csp) {
        return applyFixpoint(csp, null);
    }

    /**
     * Runs the full propagator fixpoint without snapping bounded domains. Called by
     * {@link io.github.rcrida.jcsp.solver.Solver.Factory#FULL_PROPAGATION_INFERENCE} to apply
     * all propagators during backtracking search, not just as a preprocessing pass.
     * <p>
     * Tracks which variables' domains changed during the previous round and passes that set to
     * each propagator via {@link ConstraintConsistency#apply(ConstraintSatisfactionProblem, Set)},
     * so {@link NogoodFixpointConsistency} can skip re-checking nogoods that reference none of
     * them (see its javadoc). {@code initialSeed} is round 1's dirty-variable hint: {@code
     * FULL_PROPAGATION_INFERENCE} passes the diff between the pre- and post-MAC domains (plus any
     * variable of a newly-learned nogood, which must always be checked once) rather than {@code
     * null}, since at a search node this call's input is exactly the parent's already-converged
     * CSP -- nothing else could have changed. {@code null} (this class's own preprocessing call,
     * and any other caller with no such parent to diff against) falls back to a full first-round
     * scan exactly as before.
     */
    static Optional<ConstraintSatisfactionProblem> applyFixpoint(
            @NonNull ConstraintSatisfactionProblem csp, @Nullable Set<Variable<?>> initialSeed) {
        var current = csp;
        Set<Variable<?>> changedVariables = initialSeed;
        boolean changed = true;
        while (changed) {
            Map<Variable<?>, Domain<?>> before = current.getVariableDomains();
            double domainSumBefore = domainSum(current);
            for (var propagator : PROPAGATORS) {
                var after = propagator.apply(current, changedVariables);
                if (after.isEmpty()) return Optional.empty();
                current = after.get();
            }
            changed = domainSum(current) < domainSumBefore;
            changedVariables = changed ? changedVariables(before, current.getVariableDomains()) : null;
        }
        return Optional.of(current);
    }

    /**
     * Returns every variable whose domain in {@code after} differs from its domain in {@code
     * before} (added/removed variables cannot occur -- every propagator narrows an existing
     * domain, never changes the variable set). Package-private (not {@code private}) so {@link
     * io.github.rcrida.jcsp.solver.Solver.Factory#FULL_PROPAGATION_INFERENCE} can reuse it to
     * compute {@code applyFixpoint}'s round-1 seed from the pre-/post-MAC domains.
     */
    static Set<Variable<?>> changedVariables(Map<Variable<?>, Domain<?>> before,
                                              Map<Variable<?>, Domain<?>> after) {
        Set<Variable<?>> result = new HashSet<>();
        for (var entry : after.entrySet()) {
            if (!Objects.equals(entry.getValue(), before.get(entry.getKey()))) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Thin wrapper over {@link #applyFixpointWithReason}, kept for direct callers/tests: returns
     * the nogood that explains a conflict, or {@link Optional#empty()} if the fixpoint converges
     * feasibly instead.
     */
    static Optional<NogoodConstraint> explainConflict(@NonNull ConstraintSatisfactionProblem csp) {
        ConsistencyResult result = applyFixpointWithReason(csp, null);
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    /**
     * Single-pass combination of {@link #applyFixpoint} and the old separate {@code explainConflict}
     * traversal: identical loop and seeding as {@link #applyFixpoint} — each propagator's {@link
     * ConstraintConsistency#applyWithReason} costs the same as {@link ConstraintConsistency#apply}
     * on the feasible path (see each propagator's own {@code applyWithReason} override) — and only
     * the propagator that actually signals infeasibility contributes a reason, computed as part of
     * this same pass rather than a second, from-scratch, unseeded replay.
     */
    static ConsistencyResult applyFixpointWithReason(
            @NonNull ConstraintSatisfactionProblem csp, @Nullable Set<Variable<?>> initialSeed) {
        var current = csp;
        Set<Variable<?>> changedVariables = initialSeed;
        boolean changed = true;
        while (changed) {
            Map<Variable<?>, Domain<?>> before = current.getVariableDomains();
            double domainSumBefore = domainSum(current);
            for (var propagator : PROPAGATORS) {
                ConsistencyResult after = propagator.applyWithReason(current, changedVariables);
                if (after.isInfeasible()) return after;
                current = after.problem();
            }
            changed = domainSum(current) < domainSumBefore;
            changedVariables = changed ? changedVariables(before, current.getVariableDomains()) : null;
        }
        return ConsistencyResult.feasible(current);
    }

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        return runFixpoint(csp);
    }

    private @NonNull Optional<ConstraintSatisfactionProblem> runFixpoint(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            var result = applyFixpoint(current);
            if (result.isEmpty()) return Optional.empty();
            changed = domainSum(result.get()) < domainSum(current);
            current = result.get();
            if (!changed && snap) {
                var snapTarget = BisectionConditioningSolver.findWidestBounded(current);
                if (snapTarget != null) {
                    BoundedDomain<?> bd = (BoundedDomain<?>) current.getDomain(snapTarget);
                    double mid = (bd.getMin().doubleValue() + bd.getMax().doubleValue()) / 2.0;
                    current = BisectionConditioningSolver.withSnapped(current, snapTarget, mid);
                    changed = true;
                }
            }
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", domainSum(current));
        return Optional.of(current);
    }

    /**
     * Sums each domain's "size" as a progress metric for fixpoint convergence: discrete domains
     * contribute their element count, while {@link BoundedDomain} (e.g. {@link IntervalDomain})
     * contribute their width, so interval narrowing is recognised as progress.
     */
    private static double domainSum(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream()
                .mapToDouble(d -> (d instanceof BoundedDomain<?> bd)
                        ? bd.getMax().doubleValue() - bd.getMin().doubleValue()
                        : d.size())
                .sum();
    }
}
