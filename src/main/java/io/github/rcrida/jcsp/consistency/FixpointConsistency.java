package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A {@link ConstraintConsistency} that runs all {@link Propagatable} constraints of a given
 * type to fixpoint: filters, propagates, and repeats until no further domain reductions occur,
 * returning {@link Optional#empty()} as soon as any propagator signals infeasibility.
 *
 * <p>Use the {@link #of} factory to create instances. Adding a new propagator to the solver
 * chains ({@code PropagationFixpointSolver.PROPAGATORS}, {@code LocalSolver.Factory.PREPROCESSORS})
 * requires only a single {@code FixpointConsistency.of(MyConstraint.class)} entry.
 */
public final class FixpointConsistency implements ConstraintConsistency {
    private static final Logger log = LoggerFactory.getLogger(FixpointConsistency.class);

    private final Class<? extends Propagatable> constraintType;

    private FixpointConsistency(Class<? extends Propagatable> constraintType) {
        this.constraintType = constraintType;
    }

    public static FixpointConsistency of(Class<? extends Propagatable> constraintType) {
        return new FixpointConsistency(constraintType);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<Propagatable> constraints = (List) csp.getConstraints().stream()
                .filter(constraintType::isInstance)
                .toList();
        var name = constraintType.getSimpleName();
        if (constraints.isEmpty()) {
            log.info("{}: fixpoint reached", name);
            return Optional.of(csp);
        }
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("{}: infeasible detected", name);
                    return Optional.empty();
                }
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
        log.info("{}: fixpoint reached", name);
        return Optional.of(current);
    }
}
