package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;

/**
 * Explains a propagation conflict by returning a nogood: a partial variable-to-value map
 * whose entries together guarantee failure, derived by re-running propagation with reason
 * tracking after {@link io.github.rcrida.jcsp.consistency.Inference#apply} returns empty.
 * <p>
 * The returned map must be a valid nogood — every branch whose assignment subsumes it will
 * also fail. An empty map is treated as "no explanation available"; callers use the full
 * current assignment as the nogood in that case.
 */
@FunctionalInterface
public interface ConflictExplainer {
    Map<Variable<?>, Object> explain(ConstraintSatisfactionProblem csp,
                                     Variable<?> variable,
                                     Assignment assignment);
}
