package io.github.rcrida.jcsp.consistency.cumulative;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.List;
import java.util.Optional;

/**
 * Applies timetabling propagation to all {@link CumulativeConstraint} instances in a problem,
 * iterating until no further domain tightening is possible (fixpoint).
 * <p>
 * Timetabling identifies each task's <em>compulsory part</em> — the interval that must be
 * occupied regardless of the final start time — builds a mandatory resource profile, and
 * tightens each start-variable domain to exclude positions that would exceed the capacity.
 */
@Slf4j
public class CumulativeConsistency {
    public static final CumulativeConsistency INSTANCE = new CumulativeConsistency();

    private CumulativeConsistency() {}

    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<CumulativeConstraint> constraints = csp.getConstraints().stream()
                .filter(c -> c instanceof CumulativeConstraint)
                .map(c -> (CumulativeConstraint) c)
                .toList();

        if (constraints.isEmpty()) return Optional.of(csp);

        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (CumulativeConstraint constraint : constraints) {
                var result = constraint.timetable(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("CumulativeConsistency: infeasible timetable detected");
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        var variable = (Variable) entry.getKey();
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        var domain   = (Domain) entry.getValue();
                        builder.variableDomainEntry(variable, domain);
                    }
                    current = builder.build();
                    changed = true;
                    log.debug("CumulativeConsistency: tightened domains {}", updates.keySet());
                }
            }
        }
        log.info("CumulativeConsistency: fixpoint reached {}", current);
        return Optional.of(current);
    }
}
