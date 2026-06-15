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
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

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
            FixpointConsistency.of(LexConstraint.class)
    );

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            int domainSumBefore = domainSum(current);
            for (var propagator : PROPAGATORS) {
                var after = propagator.apply(current);
                if (after.isEmpty()) return Optional.empty();
                current = after.get();
            }
            changed = domainSum(current) < domainSumBefore;
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", domainSum(current));
        return Optional.of(current);
    }

    private static int domainSum(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream().mapToInt(Domain::size).sum();
    }
}
