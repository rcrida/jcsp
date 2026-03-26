package org.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
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

public class MAC implements Inference {
    public static final Inference INSTANCE = new MAC();

    private MAC() {}

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
