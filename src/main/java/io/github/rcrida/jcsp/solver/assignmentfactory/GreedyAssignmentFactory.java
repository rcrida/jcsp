package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.solver.SetDomainMoves;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * An {@link InitialAssignmentFactory} that builds an initial assignment greedily: variables are
 * assigned one at a time in their natural order, and each variable receives the value from its
 * domain that violates the fewest constraints against the variables already assigned. Ties are
 * broken randomly.
 * <p>
 * The resulting assignment is complete but not necessarily consistent. It typically starts with
 * far fewer conflicts than a random assignment, which reduces the number of steps required by a
 * subsequent local search.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GreedyAssignmentFactory implements InitialAssignmentFactory {

    public static final GreedyAssignmentFactory INSTANCE = new GreedyAssignmentFactory();

    /** Random draws {@link SetDomainMoves#representativeSeeds} adds for a {@link SetBoundedDomain} variable. */
    private static final int SET_DOMAIN_SAMPLE_SIZE = 3;

    @Override
    public Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp) {
        var current = Assignment.empty();
        for (Variable<?> variable : csp.getVariableDomains().keySet()) {
            val value = leastConflictingValue(variable, current, csp);
            current = Assignment.of(addEntry(current, variable, value));
        }
        return current;
    }

    private Object leastConflictingValue(Variable<?> variable, Assignment current, ConstraintSatisfactionProblem csp) {
        val constraintsOnVariable = csp.getConstraints().stream()
                .filter(c -> c.getVariables().contains(variable))
                .toList();

        AtomicInteger bestViolations = new AtomicInteger(Integer.MAX_VALUE);
        List<Object> bestValues = new ArrayList<>();
        candidateSeedValues(csp.getDomain(variable)).forEach(value -> {
            val candidate = Assignment.of(addEntry(current, variable, value));
            int violations = (int) constraintsOnVariable.stream()
                    .filter(c -> c.getVariables().stream().allMatch(v -> candidate.getValue(v).isPresent()))
                    .filter(c -> !c.isSatisfiedBy(candidate))
                    .count();
            if (violations < bestViolations.get()) {
                bestViolations.set(violations);
                bestValues.clear();
                bestValues.add(value);
            } else if (violations == bestViolations.get()) {
                bestValues.add(value);
            }
        });
        return bestValues.get(ThreadLocalRandom.current().nextInt(bestValues.size()));
    }

    /**
     * The candidates to score for {@code variable}: every value for a {@link DiscreteDomain}
     * (unchanged behaviour), or a small representative sample for a {@link SetBoundedDomain} —
     * see {@link SetDomainMoves#representativeSeeds} for why a short list, not full enumeration or
     * a bare random draw, is the right amount of effort for an initial-assignment seed.
     */
    private static Stream<?> candidateSeedValues(Domain<?> domain) {
        if (domain instanceof SetBoundedDomain<?> setDomain) {
            return SetDomainMoves.representativeSeeds(setDomain, SET_DOMAIN_SAMPLE_SIZE).stream();
        }
        return ((DiscreteDomain<?>) domain).stream();
    }

    private static Map<Variable<?>, Object> addEntry(Assignment current, Variable<?> variable, Object value) {
        val map = new HashMap<>(current.getValues());
        map.put(variable, value);
        return map;
    }
}
