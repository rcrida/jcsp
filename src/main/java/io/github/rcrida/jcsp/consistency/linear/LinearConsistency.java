package io.github.rcrida.jcsp.consistency.linear;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies bounds propagation for all {@link LinearConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class LinearConsistency {
    public static final LinearConsistency INSTANCE = new LinearConsistency();

    private LinearConsistency() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<LinearConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof LinearConstraint<?>)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("LinearConsistency: infeasible detected");
        else log.info("LinearConsistency: fixpoint reached");
        return result;
    }
}
