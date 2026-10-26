package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.solver.Cancellation;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The constraints a problem's tables, clauses and equations imply but state only piecemeal, added so
 * that the solver can reason about them jointly: {@link ParitySystems}, {@link Pigeonholes} and
 * {@link LatticeSystems}. Every one is
 * entailed by constraints already present, which stay, so nothing a solution must satisfy changes.
 * The solver works on the augmented copy and the caller's problem is left alone.
 */
public final class RedundantConstraints {
    private RedundantConstraints() {
    }

    /** {@code csp} plus the redundant constraints it implies, or {@code csp} itself when there are none. */
    public static @NonNull ConstraintSatisfactionProblem withRedundantConstraints(@NonNull ConstraintSatisfactionProblem csp) {
        return withRedundantConstraints(csp, Cancellation.NEVER);
    }

    /**
     * As {@link #withRedundantConstraints(ConstraintSatisfactionProblem)}, stopping the one search it
     * runs (see {@link LatticeSystems}) when {@code cancellation} is cancelled.
     */
    public static @NonNull ConstraintSatisfactionProblem withRedundantConstraints(@NonNull ConstraintSatisfactionProblem csp,
                                                                                   @NonNull Cancellation cancellation) {
        List<Constraint> redundant = new ArrayList<>();
        redundant.addAll(ParitySystems.systemsIn(csp));
        redundant.addAll(Pigeonholes.pigeonholesIn(csp));
        redundant.addAll(LatticeSystems.systemsIn(csp, cancellation));
        if (redundant.isEmpty()) return csp;
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = csp.toBuilder();
        redundant.forEach(builder::constraint);
        return builder.build();
    }
}
