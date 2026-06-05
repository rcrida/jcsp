package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by applying Régin's GAC propagator for all
 * {@link AllDiffConstraint} instances before solving.
 * Has no effect if the problem contains no {@link AllDiffConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AllDiffConsistentSolver extends SolverDecorator {
    private static final FixpointConsistency CONSISTENCY = FixpointConsistency.of(AllDiffConstraint.class);

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        return CONSISTENCY.apply(csp);
    }
}
