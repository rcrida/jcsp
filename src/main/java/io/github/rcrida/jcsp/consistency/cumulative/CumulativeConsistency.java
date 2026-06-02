package io.github.rcrida.jcsp.consistency.cumulative;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyFixpoint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;

import java.util.List;
import java.util.Optional;

/**
 * Applies timetabling propagation for all {@link CumulativeConstraint} instances in a problem,
 * iterating to fixpoint via {@link ConsistencyFixpoint}.
 */
@Slf4j
public class CumulativeConsistency {
    public static final CumulativeConsistency INSTANCE = new CumulativeConsistency();

    private CumulativeConsistency() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<CumulativeConstraint> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof CumulativeConstraint)
                .toList();
        var result = ConsistencyFixpoint.apply(csp, constraints);
        if (result.isEmpty()) log.warn("CumulativeConsistency: infeasible timetable detected");
        else log.info("CumulativeConsistency: fixpoint reached {}", result.get());
        return result;
    }
}
