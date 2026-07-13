package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Conflict-scoring helpers shared by the conflict-repair local search solvers
 * ({@link MinConflictsSolver}, {@link WalkSATSolver}, {@link TabuSearchSolver}) that all
 * operate over the same constraint set and score candidate values the same way.
 */
final class LocalSearchSupport {
    private LocalSearchSupport() {}

    // Use binary decompositions where available (preserves per-pair granularity for AllDiff,
    // ExactlyOne, etc.) and fall through to the original n-ary constraint where no binary
    // decomposition exists (e.g. AtLeastN, AtMostN) so they are never silently dropped.
    // Unary constraints have no binary form and are included directly.
    static Stream<Constraint> conflictConstraints(@NonNull ConstraintSatisfactionProblem csp) {
        Stream<Constraint> binaryAndUnary = Stream.concat(
                csp.getAllBinaryConstraints().stream(),
                csp.getConstraints().stream().filter(c -> c instanceof UnaryConstraint));
        Stream<Constraint> nonDecomposableNary = csp.getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint && (!(c instanceof BinaryDecomposable bd) || bd.getAsBinaryConstraints().isEmpty()));
        return Stream.concat(binaryAndUnary, nonDecomposableNary);
    }

    /**
     * Computes the total weight of unsatisfied constraints when {@code variable} is assigned {@code value}.
     */
    static <T> double weighConflicts(@NonNull Variable<T> variable, @NonNull T value, @NonNull Assignment current,
                                      @NonNull List<? extends Constraint> variableConstraints,
                                      @NonNull Map<Constraint, Double> constraintWeights) {
        val candidate = current.withValue(variable, value);
        return variableConstraints.stream()
                .filter(Predicate.not(constraint -> constraint.isSatisfiedBy(candidate)))
                .map(constraint -> constraintWeights.getOrDefault(constraint, 1.0))
                .reduce(0.0, Double::sum);
    }

    /**
     * Computes the total weight of every unsatisfied constraint in the whole CSP under {@code assignment} —
     * a global cost measure, unlike {@link #weighConflicts}, which only looks at one variable's constraints.
     */
    static double totalWeight(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment,
                               @NonNull Map<Constraint, Double> constraintWeights) {
        return conflictConstraints(csp)
                .filter(Predicate.not(constraint -> constraint.isSatisfiedBy(assignment)))
                .map(constraint -> constraintWeights.getOrDefault(constraint, 1.0))
                .reduce(0.0, Double::sum);
    }

    /** Increments the weight of every constraint currently violated by {@code current} by 1. */
    static void incrementViolatedWeights(@NonNull ConstraintSatisfactionProblem csp,
                                         @NonNull Map<Constraint, Double> constraintWeights,
                                         @NonNull Assignment current) {
        conflictConstraints(csp)
                .filter(Predicate.not(constraint -> constraint.isSatisfiedBy(current)))
                .forEach(constraint -> {
                    val currentWeight = constraintWeights.getOrDefault(constraint, 1.0);
                    constraintWeights.put(constraint, currentWeight + 1);
                });
    }
}
