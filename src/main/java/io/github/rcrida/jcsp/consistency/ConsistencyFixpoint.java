package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.List;
import java.util.Optional;

/**
 * Shared fixpoint loop for constraint propagators.
 * <p>
 * Iterates over the supplied list of {@link Propagatable} constraints, applying each one to the
 * current domains, updating the CSP when domains shrink, and repeating until no propagator
 * makes further progress. Returns {@link Optional#empty()} as soon as any propagator signals
 * infeasibility.
 */
public final class ConsistencyFixpoint {
    private ConsistencyFixpoint() {}

    @SuppressWarnings({"rawtypes"})
    public static Optional<ConstraintSatisfactionProblem> apply(
            ConstraintSatisfactionProblem csp,
            List<? extends Propagatable> constraints) {
        if (constraints.isEmpty()) return Optional.of(csp);
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) return Optional.empty();
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        builder.variableDomainEntry((Variable) entry.getKey(), (Domain) entry.getValue());
                    }
                    current = builder.build();
                    changed = true;
                }
            }
        }
        return Optional.of(current);
    }
}
