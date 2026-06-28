package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DivisionConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
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
                FixpointConsistency.of(UnaryComparatorConstraint.class),
                FixpointConsistency.of(BinaryComparatorConstraint.class),
                FixpointConsistency.of(BinaryOffsetConstraint.class),
                FixpointConsistency.of(AbsoluteDifferenceConstraint.class),
                AC3.INSTANCE,
                FixpointConsistency.of(SumConstraint.class),
                FixpointConsistency.of(LinearConstraint.class),
                FixpointConsistency.of(CountConstraint.class),
                FixpointConsistency.of(InverseConstraint.class),
                FixpointConsistency.of(AmongConstraint.class),
                FixpointConsistency.of(AtLeastNConstraint.class),
                FixpointConsistency.of(AtMostNConstraint.class),
                FixpointConsistency.of(CumulativeConstraint.class),
                FixpointConsistency.of(GlobalCardinalityConstraint.class),
                FixpointConsistency.of(LexConstraint.class),
                FixpointConsistency.of(MaxConstraint.class),
                FixpointConsistency.of(MinConstraint.class),
                FixpointConsistency.of(NaryElementConstraint.class),
                FixpointConsistency.of(NaryTuplesConstraint.class),
                FixpointConsistency.of(ProductConstraint.class),
                FixpointConsistency.of(DivisionConstraint.class)
        );

        Factory INSTANCE = (maxAttempts, maxSteps, initialAssignmentFactory) -> {
            val minConflicts = IndependentSubproblemLocalSolver.builder()
                    .delegate(MinConflictsSolver.of(maxAttempts, maxSteps, initialAssignmentFactory))
                    .build();
            val walkSat = IndependentSubproblemLocalSolver.builder()
                    .delegate(WalkSATSolver.of(maxAttempts, maxSteps, initialAssignmentFactory))
                    .build();
            val lns = IndependentSubproblemLocalSolver.builder()
                    .delegate(LargeNeighborhoodSolver.of(maxAttempts, maxSteps, initialAssignmentFactory))
                    .build();
            return new LocalSolver() {
                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
                    var reduced = Optional.of(csp);
                    for (var p : PREPROCESSORS) reduced = reduced.flatMap(p::apply);
                    return reduced.flatMap(r -> {
                        boolean allBoolean = r.getVariableDomains().values().stream()
                                .allMatch(d -> d.isSingleton() || WalkSATSolver.canFlip(d));
                        boolean noCountingConstraints = r.getConstraints().stream()
                                .noneMatch(c -> c instanceof ExactlyOneConstraint
                                        || c instanceof AtLeastNConstraint);
                        return (allBoolean && noCountingConstraints ? walkSat : minConflicts).getLocalSolution(r);
                    });
                }

                @Override
                public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                             @NonNull ToDoubleFunction<Assignment> objective) {
                    var reduced = Optional.of(csp);
                    for (var p : PREPROCESSORS) reduced = reduced.flatMap(p::apply);
                    return reduced.flatMap(r -> {
                        boolean hasExactlyOne = r.getConstraints().stream()
                                .anyMatch(c -> c instanceof ExactlyOneConstraint);
                        return (hasExactlyOne ? lns : minConflicts).getLocalSolution(r, objective);
                    });
                }
            };
        };

        LocalSolver createLocalSolver(int maxAttempts, int maxSteps, @NonNull InitialAssignmentFactory initialAssignmentFactory);
    }
}
