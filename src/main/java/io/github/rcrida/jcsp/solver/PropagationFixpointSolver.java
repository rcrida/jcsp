package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PropagationFixpointSolver extends SolverDecorator {

    /** When true (the default for plain CSP), non-singleton {@link BoundedDomain} variables are
     *  snapped to their interval midpoints after propagation converges, giving one concrete solution.
     *  Set to false in optimization chains where {@link BisectionConditioningSolver} explores the
     *  feasible region instead. */
    boolean snapBoundedDomains;

    private static final List<ConstraintConsistency> PROPAGATORS = List.of(
            AC3.INSTANCE,
            FixpointConsistency.of(AllDiffConstraint.class),
            FixpointConsistency.of(SumConstraint.class),
            FixpointConsistency.of(LinearConstraint.class),
            FixpointConsistency.of(CountConstraint.class),
            FixpointConsistency.of(InverseConstraint.class),
            FixpointConsistency.of(AmongConstraint.class),
            FixpointConsistency.of(AtLeastNConstraint.class),
            FixpointConsistency.of(AtMostNConstraint.class),
            FixpointConsistency.of(CumulativeConstraint.class),
            FixpointConsistency.of(GlobalCardinalityConstraint.class),
            FixpointConsistency.of(LexConstraint.class),
            FixpointConsistency.of(NaryTuplesConstraint.class)
    );

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            double domainSumBefore = domainSum(current);
            for (var propagator : PROPAGATORS) {
                var after = propagator.apply(current);
                if (after.isEmpty()) return Optional.empty();
                current = after.get();
            }
            changed = domainSum(current) < domainSumBefore;
            if (!changed && snapBoundedDomains) {
                var snapTarget = findWidestNonSingletonBounded(current);
                if (snapTarget != null) {
                    current = snapToMidpoint(current, snapTarget);
                    changed = true;
                }
            }
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", domainSum(current));
        return Optional.of(current);
    }

    @Nullable
    private static Variable<?> findWidestNonSingletonBounded(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> e.getValue() instanceof BoundedDomain<?> bd && !bd.isSingleton())
                .max(Comparator.comparingDouble(e ->
                        ((BoundedDomain<?>) e.getValue()).getMax().doubleValue()
                        - ((BoundedDomain<?>) e.getValue()).getMin().doubleValue()))
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static ConstraintSatisfactionProblem snapToMidpoint(
            @NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> target) {
        BoundedDomain<?> bd = (BoundedDomain<?>) csp.getDomain(target);
        double mid = (bd.getMin().doubleValue() + bd.getMax().doubleValue()) / 2.0;
        return csp.toBuilder()
                .variableDomainEntry((Variable) target, IntervalDomain.of(mid, mid))
                .build();
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
