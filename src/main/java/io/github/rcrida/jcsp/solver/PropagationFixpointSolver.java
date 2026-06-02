package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.alldiff.AllDiffConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Runs AC3 and AllDiff GAC in a combined fixpoint loop.
 *
 * <p>The two propagators are not independent: AllDiff GAC can expose naked pairs that AC3 then
 * propagates to neighbouring constraints, which shrinks domains that AllDiff GAC can exploit
 * further. Running each once misses this feedback. This solver iterates until neither makes
 * further progress.
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

            changed = domainSum(current) < domainSumBefore;
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", domainSum(current));
        return Optional.of(current);
    }

    private static int domainSum(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream().mapToInt(Domain::size).sum();
    }
}
