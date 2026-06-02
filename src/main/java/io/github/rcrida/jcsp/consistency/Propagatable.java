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
}
