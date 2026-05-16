package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by first ensuring the problem is arc-consistent.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ArcConsistentSolver extends SolverDecorator {
    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(@NonNull ConstraintSatisfactionProblem csp) {
        return AC3.INSTANCE.apply(csp);
    }
}
