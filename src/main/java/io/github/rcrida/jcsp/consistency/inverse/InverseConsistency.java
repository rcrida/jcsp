package io.github.rcrida.jcsp.consistency.inverse;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies arc-consistency propagation for all {@link InverseConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class InverseConsistency {
    public static final InverseConsistency INSTANCE = new InverseConsistency();

    private InverseConsistency() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<InverseConstraint> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof InverseConstraint)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("InverseConsistency: infeasible detected");
        else log.info("InverseConsistency: fixpoint reached");
        return result;
    }
}
