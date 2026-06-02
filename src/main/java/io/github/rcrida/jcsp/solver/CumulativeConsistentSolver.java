package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.cumulative.CumulativeConsistency;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by applying {@link CumulativeConsistency} timetabling propagation
 * before solving. Has no effect if the problem contains no {@link io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CumulativeConsistentSolver extends SolverDecorator {
    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        return CumulativeConsistency.INSTANCE.apply(csp);
    }
}
