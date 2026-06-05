package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by applying timetabling propagation for all
 * {@link CumulativeConstraint} instances before solving.
 * Has no effect if the problem contains no {@link CumulativeConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CumulativeConsistentSolver extends SolverDecorator {
    private static final FixpointConsistency CONSISTENCY = FixpointConsistency.of(CumulativeConstraint.class);

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        return CONSISTENCY.apply(csp);
    }
}
