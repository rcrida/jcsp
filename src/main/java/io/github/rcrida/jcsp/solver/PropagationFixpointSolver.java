package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.alldiff.AllDiffConsistency;
import io.github.rcrida.jcsp.consistency.among.AmongConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.count.CountConsistency;
import io.github.rcrida.jcsp.consistency.inverse.InverseConsistency;
import io.github.rcrida.jcsp.consistency.linear.LinearConsistency;
import io.github.rcrida.jcsp.consistency.sum.SumConsistency;
import io.github.rcrida.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

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
 * or exits immediately with {@link java.util.Optional#empty()} as soon as any propagator
 * detects infeasibility.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PropagationFixpointSolver extends SolverDecorator {

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            int domainSumBefore = domainSum(current);

            var afterAC3 = AC3.INSTANCE.apply(current);
            if (afterAC3.isEmpty()) return Optional.empty();
            current = afterAC3.get();

            var afterAllDiff = AllDiffConsistency.INSTANCE.apply(current);
            if (afterAllDiff.isEmpty()) return Optional.empty();
            current = afterAllDiff.get();

            var afterSum = SumConsistency.INSTANCE.apply(current);
            if (afterSum.isEmpty()) return Optional.empty();
            current = afterSum.get();

            var afterLinear = LinearConsistency.INSTANCE.apply(current);
            if (afterLinear.isEmpty()) return Optional.empty();
            current = afterLinear.get();

            var afterCount = CountConsistency.INSTANCE.apply(current);
            if (afterCount.isEmpty()) return Optional.empty();
            current = afterCount.get();

            var afterInverse = InverseConsistency.INSTANCE.apply(current);
            if (afterInverse.isEmpty()) return Optional.empty();
            current = afterInverse.get();

            var afterAmong = AmongConsistency.INSTANCE.apply(current);
            if (afterAmong.isEmpty()) return Optional.empty();
            current = afterAmong.get();

            changed = domainSum(current) < domainSumBefore;
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", domainSum(current));
        return Optional.of(current);
    }

    private static int domainSum(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream().mapToInt(Domain::size).sum();
    }
}
