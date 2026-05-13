package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A factory implementation of the {@link InitialAssignmentFactory} interface that generates
 * a random initial assignment for a given {@link ConstraintSatisfactionProblem} (CSP).
 * <p>
 * This class assigns random values to variables within the domains defined by the CSP.
 * Each variable in the CSP is assigned a value chosen uniformly at random from its domain.
 * The generated assignment may not satisfy the problem's constraints and is primarily
 * intended as a starting point for further refinement.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RandomAssignmentFactory implements InitialAssignmentFactory {

    public static final RandomAssignmentFactory INSTANCE = new RandomAssignmentFactory();

    @Override
    public Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp) {
        val builder = Assignment.builder();
        csp.getVariableDomains().forEach((key, value) -> builder.value(key, getRandomValue(value)));
        return builder.build();
    }

    private Object getRandomValue(Domain domain) {
        int index = ThreadLocalRandom.current().nextInt(domain.size());
        return domain.stream().skip(index).findAny().get();
    }
}
