package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The result of a {@link Propagatable#propagateWithReasons} call: updated domains paired with
 * the reason — the nogood that explains a domain wipeout.
 * <p>
 * When {@link #isInfeasible()} is true the constraint detected a domain wipeout; {@code reason}
 * is the nogood that explains it, or {@code null} if the propagator doesn't (yet) provide one —
 * the caller is responsible for choosing a fallback nogood in that case.
 * When feasible, {@code updatedDomains} is non-null and {@code reason} is always {@code null}: no
 * implementor overrides {@code propagateWithReasons} itself to report anything on the feasible
 * path, only {@link Propagatable#explainInfeasible} on the infeasible one.
 */
public record PropagationResult(
        @Nullable Map<Variable<?>, Domain<?>> updatedDomains,
        @Nullable NogoodConstraint reason) {

    public PropagationResult {
        updatedDomains = updatedDomains == null ? null : Map.copyOf(updatedDomains);
    }

    public static PropagationResult feasible(Map<Variable<?>, Domain<?>> domains,
                                             @Nullable NogoodConstraint reason) {
        return new PropagationResult(domains, reason);
    }

    public static PropagationResult infeasible(@Nullable NogoodConstraint reason) {
        return new PropagationResult(null, reason);
    }

    public boolean isInfeasible() {
        return updatedDomains == null;
    }
}
