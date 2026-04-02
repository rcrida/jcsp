package org.jcsp.consistency.arc;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.Inference;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.domains.AssignedDomain;
import org.jcsp.variables.Variable;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the Maintaining Arc Consistency (MAC) inference algorithm which is used in constraint satisfaction problems (CSPs).
 * This class ensures arc consistency for a given assignment of a variable by enforcing constraints on binary relations.
 */
public class MAC implements Inference {
    public static final Inference INSTANCE = new MAC();

    private MAC() {}

    /**
     * Applies the Maintaining Arc Consistency (MAC) inference algorithm to enforce arc consistency
     * for a constraint satisfaction problem (CSP) after assigning a value to a variable.
     *
     * @param problem The constraint satisfaction problem to which inference will be applied.
     * @param variable The variable that has been assigned a new value in the CSP.
     * @param assignment The current assignment of values to variables in the CSP.
     * @return An {@code Optional} containing the updated constraint satisfaction problem if inference
     *         is successful and maintains consistency; otherwise, an empty {@code Optional}.
     */
    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable variable, Assignment assignment) {
        val value = assignment.getValue(variable).orElseThrow();
        val allBinaryConstraints = problem.getAllBinaryConstraints().stream()
                .flatMap(c -> Stream.of(c, c.reversed()))
                .collect(Collectors.toSet());
        val variableConstraints = allBinaryConstraints.stream()
                .filter(c -> isBinaryConstraintToX_i(c, variable))
                .filter(c -> isNotAlreadyAssignedX_j(assignment, c))
                .collect(Collectors.toSet());
        val queue = new ArrayDeque<>(variableConstraints);
        return AC3.INSTANCE.applyQueue(
                problem.toBuilder().variableDomain(variable, new AssignedDomain(value)).build(),
                queue,
                allBinaryConstraints);
    }

    private static boolean isBinaryConstraintToX_i(BinaryConstraint constraint, Variable X_i) {
        return constraint.getRight().equals(X_i);
    }

    private static boolean isNotAlreadyAssignedX_j(Assignment assignment, BinaryConstraint constraint) {
        return assignment.getValue(constraint.getLeft()).isEmpty();
    }
}
