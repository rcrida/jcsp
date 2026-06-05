package io.github.rcrida.jcsp.consistency.sum;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies bounds propagation for all {@link SumConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class SumConsistency implements ConstraintConsistency {
    public static final SumConsistency INSTANCE = new SumConsistency();

    private SumConsistency() {}

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<SumConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof SumConstraint<?>)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("SumConsistency: infeasible detected");
        else log.info("SumConsistency: fixpoint reached");
        return result;
    }
}
