package io.github.rcrida.jcsp.consistency.alldiff;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies Régin's GAC propagator for all {@link AllDiffConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class AllDiffConsistency implements ConstraintConsistency {
    public static final AllDiffConsistency INSTANCE = new AllDiffConsistency();

    private AllDiffConsistency() {}

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<AllDiffConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof AllDiffConstraint<?>)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("AllDiffConsistency: infeasible detected");
        else log.info("AllDiffConsistency: fixpoint reached");
        return result;
    }
}
