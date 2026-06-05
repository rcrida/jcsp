package io.github.rcrida.jcsp.consistency.count;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies count-value propagation for all {@link CountConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class CountConsistency implements ConstraintConsistency {
    public static final CountConsistency INSTANCE = new CountConsistency();

    private CountConsistency() {}

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<CountConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof CountConstraint<?>)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("CountConsistency: infeasible detected");
        else log.info("CountConsistency: fixpoint reached");
        return result;
    }
}
