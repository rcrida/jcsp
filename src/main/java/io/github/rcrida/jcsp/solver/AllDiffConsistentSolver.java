package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.alldiff.AllDiffConsistency;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Decorates another solver by applying {@link AllDiffConsistency} (Régin's GAC propagator)
 * before solving. Has no effect if the problem contains no {@link io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AllDiffConsistentSolver extends SolverDecorator {
    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        return AllDiffConsistency.INSTANCE.apply(csp);
    }
}
