package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

import java.security.SecureRandom;

/**
 * A factory implementation of the {@link InitialAssignmentFactory} interface that generates
 * a random initial assignment for a given {@link ConstraintSatisfactionProblem} (CSP).
 * <p>
 * This class assigns random values to variables within the domains defined by the CSP.
 * Each variable in the CSP is assigned a value chosen uniformly at random from its domain.
 * The generated assignment may not satisfy the problem's constraints and is primarily
 * intended as a starting point for further refinement.
 */
public class RandomAssignmentFactory implements InitialAssignmentFactory {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp) {
        val builder = Assignment.builder();
        csp.getVariableDomains().forEach((key, value) -> builder.value(key, getRandomValue(value)));
        return builder.build();
    }

    private Object getRandomValue(Domain domain) {
        val list = domain.stream().toList();
        return list.get((int) RANDOM.nextLong(domain.size()));
    }
}
