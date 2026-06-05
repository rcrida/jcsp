package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.List;
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
        List<ConstraintConsistency> PREPROCESSORS = List.of(
                NodeConsistency.INSTANCE,
                AC3.INSTANCE,
                FixpointConsistency.of(SumConstraint.class),
                FixpointConsistency.of(LinearConstraint.class),
                FixpointConsistency.of(CountConstraint.class),
                FixpointConsistency.of(InverseConstraint.class),
                FixpointConsistency.of(AmongConstraint.class)
        );

        Factory INSTANCE = (maxAttempts, maxSteps, initialAssignmentFactory) -> {
            val inner = IndependentSubproblemLocalSolver.builder()
                    .delegate(MinConflictsSolver.of(maxAttempts, maxSteps, initialAssignmentFactory))
                    .build();
            return new LocalSolver() {
                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
                    var reduced = Optional.of(csp);
                    for (var p : PREPROCESSORS) reduced = reduced.flatMap(p::apply);
                    return reduced.flatMap(inner::getLocalSolution);
                }

                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                             @NonNull ToDoubleFunction<Assignment> objective) {
                    var reduced = Optional.of(csp);
                    for (var p : PREPROCESSORS) reduced = reduced.flatMap(p::apply);
                    return reduced.flatMap(r -> inner.getLocalSolution(r, objective));
                }
            };
        };

        LocalSolver createLocalSolver(int maxAttempts, int maxSteps, @NonNull InitialAssignmentFactory initialAssignmentFactory);
    }
}
