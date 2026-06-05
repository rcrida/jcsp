package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Base class for {@link ConstraintConsistency} implementations that run a subset of
 * {@link Propagatable} constraints to fixpoint.
 *
 * <p>Filters the problem's constraints to those matching the supplied type, then iterates
 * propagation until no further domain reductions occur, returning {@link Optional#empty()}
 * as soon as any propagator signals infeasibility.
 *
 * <p>Subclasses need only supply the constraint type and expose a singleton instance:
 * <pre>
 *   public final class SumConsistency extends FixpointConsistency {
 *       public static final SumConsistency INSTANCE = new SumConsistency();
 *       private SumConsistency() { super(SumConstraint.class); }
 *   }
 * </pre>
 */
public abstract class FixpointConsistency implements ConstraintConsistency {
    private final Class<? extends Propagatable> constraintType;
    private final Logger log;

    protected FixpointConsistency(Class<? extends Propagatable> constraintType) {
        this.constraintType = constraintType;
        this.log = LoggerFactory.getLogger(getClass());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<Propagatable> constraints = (List) csp.getConstraints().stream()
                .filter(constraintType::isInstance)
                .toList();
        if (constraints.isEmpty()) {
            log.info("{}: fixpoint reached", getClass().getSimpleName());
            return Optional.of(csp);
        }
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("{}: infeasible detected", getClass().getSimpleName());
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
        log.info("{}: fixpoint reached", getClass().getSimpleName());
        return Optional.of(current);
    }
}
