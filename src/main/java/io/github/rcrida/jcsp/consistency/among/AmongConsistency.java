package io.github.rcrida.jcsp.consistency.among;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies value-set propagation for all {@link AmongConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class AmongConsistency implements ConstraintConsistency {
    public static final AmongConsistency INSTANCE = new AmongConsistency();

    private AmongConsistency() {}

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<AmongConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof AmongConstraint<?>)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("AmongConsistency: infeasible detected");
        else log.info("AmongConsistency: fixpoint reached");
        return result;
    }
}
