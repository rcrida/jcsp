package io.github.rcrida.jcsp.solver;

import lombok.Value;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * Decorates another solver by first ensuring the problem is node-consistent.
 */
@Value
public class NodeConsistentSolver implements Solver {
    @NonNull
    Solver decorated;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return NodeConsistency.INSTANCE.apply(csp)
                .map(decorated::getSolutions)
                .orElse(Stream.empty());
    }
}
