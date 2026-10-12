package io.github.rcrida.jcsp.solver.backtrackingsearch;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assigns, in one step, every unassigned variable that propagation has already left with a single
 * value, so that the search spends no node, consistency check or inference per forced value.
 * <p>
 * Sound because assigning a variable whose domain is already {@code {v}} cannot narrow anything:
 * every {@link io.github.rcrida.jcsp.consistency.Propagatable} reads domains only, and the domain
 * already holds {@code {v}}. The intermediate per-variable checks could only have rejected earlier,
 * so {@link #extend} runs one batch check over the union of constraints touching all the newly
 * assigned variables instead.
 * <p>
 * The union of touching constraints is held by identity: a table constraint's {@code hashCode} is
 * linear in its tuples, and hashing it per node dominated the search.
 * <p>
 * Only {@link DiscreteDomain}s are extended: a singleton continuous or set-interval domain is
 * resolved by the callers' own machinery.
 */
public final class ForcedAssignment {
    private ForcedAssignment() {
    }

    /**
     * {@code assignment} extended with the single value of every unassigned singleton discrete
     * variable of {@code csp}, or {@code assignment} itself (reference-identical) when there is none.
     * {@link Optional#empty()} when the extension violates a constraint touching a newly assigned
     * variable, after counting that as a backtrack.
     */
    @SuppressWarnings("unchecked")
    public static @NonNull Optional<Assignment> extend(@NonNull ConstraintSatisfactionProblem csp,
                                                       @NonNull Assignment assignment) {
        Assignment extended = assignment;
        Set<Constraint> touched = null;
        Variable<?> last = null;
        for (Map.Entry<Variable<?>, Domain<?>> entry : csp.getVariableDomains().entrySet()) {
            if (!(entry.getValue() instanceof DiscreteDomain<?> domain) || !domain.isSingleton()
                    || assignment.isAssigned(entry.getKey())) {
                continue;
            }
            Variable<Object> variable = (Variable<Object>) entry.getKey();
            extended = extended.withValue(variable, domain.singleValue().orElseThrow());
            if (touched == null) {
                touched = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            touched.addAll(csp.getConstraintsTouching(variable));
            last = variable;
        }
        if (touched == null || extended.isConsistentAmong(touched)) {
            return Optional.of(extended);
        }
        extended.getStatistics().incrementBacktracks();
        extended.listener().onBacktrack(last, extended);
        return Optional.empty();
    }
}
