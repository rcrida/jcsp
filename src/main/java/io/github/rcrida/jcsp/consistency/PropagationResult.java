package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The result of a {@link Propagatable#propagateWithReasons} call: updated domains paired with
 * the reason — a partial variable-to-value map from the current state that caused the prunings.
 * <p>
 * When {@link #isInfeasible()} is true the constraint detected a domain wipeout; {@code reason}
 * identifies the variables whose current values triggered it. An empty reason map means the
 * propagator does not yet implement explanation (uses the default); the caller is responsible for
 * choosing the nogood in that case.
 * When feasible, {@code updatedDomains} is non-null and {@code reason} explains the prunings made.
 */
public record PropagationResult(
        @Nullable Map<Variable<?>, Domain<?>> updatedDomains,
        Map<Variable<?>, Object> reason) {

    public PropagationResult {
        updatedDomains = updatedDomains == null ? null : Map.copyOf(updatedDomains);
        reason = Map.copyOf(reason);
    }

    public static PropagationResult feasible(Map<Variable<?>, Domain<?>> domains,
                                             Map<Variable<?>, Object> reason) {
        return new PropagationResult(domains, reason);
    }

    public static PropagationResult infeasible(Map<Variable<?>, Object> reason) {
        return new PropagationResult(null, reason);
    }

    public boolean isInfeasible() {
        return updatedDomains == null;
    }
}
