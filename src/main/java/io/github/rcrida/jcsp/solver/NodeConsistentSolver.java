package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by first ensuring the problem is node-consistent.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NodeConsistentSolver extends SolverDecorator {
    @Override
    @NonNull
    protected Optional<ConstraintSatisfactionProblem> preprocess(@NonNull ConstraintSatisfactionProblem csp) {
        return NodeConsistency.INSTANCE.apply(csp);
    }
}
