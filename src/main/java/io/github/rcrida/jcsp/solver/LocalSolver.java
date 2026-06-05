package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.among.AmongConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.count.CountConsistency;
import io.github.rcrida.jcsp.consistency.inverse.InverseConsistency;
import io.github.rcrida.jcsp.consistency.linear.LinearConsistency;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import io.github.rcrida.jcsp.consistency.sum.SumConsistency;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Defines an interface for solving constraint satisfaction problems (CSPs) using local search techniques.
 * Implementations of this interface aim to find assignments of values to variables that satisfy the
 * constraints of a given CSP.
 * <p>
 * Implementations may include various strategies for local search such as hill climbing, simulated annealing,
 * or the min-conflicts heuristic.
 */
public interface LocalSolver {
    Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp);

    /**
     * Returns the feasible assignment with the lowest objective value found across all attempts.
     * Each attempt runs a repair search for up to {@code maxSteps} steps; on finding a feasible
     * assignment the objective is evaluated and the attempt ends. The default ignores the objective
     * and delegates to the satisfaction search.
     */
    default Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                   @NonNull ToDoubleFunction<Assignment> objective) {
        return getLocalSolution(csp);
    }

    interface Factory {
        Factory INSTANCE = (maxAttempts, maxSteps, initialAssignmentFactory) -> {
            val inner = IndependentSubproblemLocalSolver.builder()
                    .delegate(MinConflictsSolver.of(maxAttempts, maxSteps, initialAssignmentFactory))
                    .build();
            return new LocalSolver() {
                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
                    return NodeConsistency.INSTANCE.apply(csp)
                            .flatMap(AC3.INSTANCE::apply)
                            .flatMap(SumConsistency.INSTANCE::apply)
                            .flatMap(LinearConsistency.INSTANCE::apply)
                            .flatMap(CountConsistency.INSTANCE::apply)
                            .flatMap(InverseConsistency.INSTANCE::apply)
                            .flatMap(AmongConsistency.INSTANCE::apply)
                            .flatMap(inner::getLocalSolution);
                }

                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                             @NonNull ToDoubleFunction<Assignment> objective) {
                    return NodeConsistency.INSTANCE.apply(csp)
                            .flatMap(AC3.INSTANCE::apply)
                            .flatMap(SumConsistency.INSTANCE::apply)
                            .flatMap(LinearConsistency.INSTANCE::apply)
                            .flatMap(CountConsistency.INSTANCE::apply)
                            .flatMap(InverseConsistency.INSTANCE::apply)
                            .flatMap(AmongConsistency.INSTANCE::apply)
                            .flatMap(reduced -> inner.getLocalSolution(reduced, objective));
                }
            };
        };

        LocalSolver createLocalSolver(int maxAttempts, int maxSteps, @NonNull InitialAssignmentFactory initialAssignmentFactory);
    }
}
