package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;
import java.util.Optional;

/**
 * A constraint that can propagate domain reductions from the current variable domains.
 * <p>
 * Implementing constraints compute the tightest feasible domain for each of their variables
 * given the current domains of the other variables, and return the changed domains as a map.
 */
public interface Propagatable {
    /**
     * @param domains current variable domains for the whole problem
     * @return updated domains for variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is provably infeasible
     */
    Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains);

    /**
     * Propagates domain reductions and returns the reason for each change: the variable-value
     * pairs from the current state that caused the pruning.
     * <p>
     * The default implementation delegates to {@link #propagate} and returns an empty reason map,
     * meaning no explanation is provided. Constraints override this to return tighter nogoods.
     */
    default PropagationResult propagateWithReasons(Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains)
                .map(updated -> PropagationResult.feasible(updated, Map.of()))
                .orElse(PropagationResult.infeasible(Map.of()));
    }
}
