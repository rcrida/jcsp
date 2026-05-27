package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import lombok.Value;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delegates to {@code primary} for the first {@code primaryCount} calls, then switches
 * permanently to {@code fallback}. Useful for seeding local search with a structured
 * initial assignment before falling back to a cheaper random strategy.
 */
@Value
@Builder
@EqualsAndHashCode(exclude = "callCount")
@ToString(exclude = "callCount")
public class FallbackAssignmentFactory implements InitialAssignmentFactory {
    @NonNull InitialAssignmentFactory primary;
    int primaryCount;
    @NonNull InitialAssignmentFactory fallback;

    @Builder.Default
    AtomicInteger callCount = new AtomicInteger();

    @Override
    public Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp) {
        return callCount.getAndIncrement() < primaryCount
                ? primary.getAssignment(csp)
                : fallback.getAssignment(csp);
    }
}
