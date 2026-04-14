package org.jcsp.solver;

import lombok.Value;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * Decorates another solver by first ensuring the problem is arc-consistent.
 */
@Value
public class ArcConsistentSolver implements Solver {
    @NonNull
    Solver decorated;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return AC3.INSTANCE.apply(csp)
                .map(decorated::getSolutions)
                .orElse(Stream.empty());
    }
}
