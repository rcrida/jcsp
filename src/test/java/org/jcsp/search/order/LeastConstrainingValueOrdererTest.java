package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.relations.BinaryNotEqualsRelation;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the LeastConstrainingValueOrderer class.
 * This class verifies the behavior of the 'order' method,
 * which orders domain values according to the Least Constraining Value heuristic.
 */
class LeastConstrainingValueOrdererTest {
    static Variable.Factory FACTORY = new Variable.Factory() {};

    @Test
    void singleBinaryConstraint() {
        // Setup CSP
        Variable A = FACTORY.create("A", DomainObjectSet.builder().value(1).value(2).value(3).build());
        Variable B = FACTORY.create("B", DomainObjectSet.builder().value(1).value(2).value(3).build());
        BinaryConstraint constraint = BinaryConstraint.of(A, B, BinaryNotEqualsRelation.builder().left(A).right(B).build());

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variable(A)
                .variable(B)
                .constraint(constraint)
                .build();

        // Setup assignment
        Assignment assignment = new Assignment(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = new LeastConstrainingValueOrderer();

        // Execute
        List<?> orderedValues = orderer.order(csp, A, assignment);

        // Verify (Values should be ordered based on how many values they constrain for B)
        assertEquals(List.of(1, 2, 3), orderedValues);
    }

    @Test
    void multipleBinaryConstraints() {
        // Setup CSP
        Variable A = FACTORY.create("A", DomainObjectSet.builder().value(1).value(2).value(3).build());
        Variable B = FACTORY.create("B", DomainObjectSet.builder().value(1).value(2).value(3).build());
        Variable C = FACTORY.create("C", DomainObjectSet.builder().value(1).value(2).value(3).build());

        BinaryConstraint constraint1 = BinaryConstraint.of(A, B, BinaryNotEqualsRelation.builder().left(A).right(B).build());
        BinaryConstraint constraint2 = BinaryConstraint.of(A, C, BinaryNotEqualsRelation.builder().left(A).right(C).build());

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variable(A)
                .variable(B)
                .variable(C)
                .constraint(constraint1)
                .constraint(constraint2)
                .build();

        // Setup assignment
        Assignment assignment = new Assignment(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = new LeastConstrainingValueOrderer();

        // Execute
        List<?> orderedValues = orderer.order(csp, A, assignment);

        // Verify (Values should be ordered based on the combined constraints for B and C)
        assertEquals(List.of(1, 2, 3), orderedValues);
    }

    @Test
    void noConstraints() {
        // Setup CSP with no constraints
        Variable A = FACTORY.create("A", DomainObjectSet.builder().value(1).value(2).value(3).build());

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variable(A)
                .build();

        // Setup assignment
        Assignment assignment = new Assignment(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = new LeastConstrainingValueOrderer();

        // Execute
        List<?> orderedValues = orderer.order(csp, A, assignment);

        // Verify (Values should remain in their original order)
        assertEquals(List.of(1, 2, 3), orderedValues);
    }

    @Test
    void preAssignedVariable() {
        // Setup CSP
        Variable A = FACTORY.create("A", DomainObjectSet.builder().value(1).value(2).value(3).build());
        Variable B = FACTORY.create("B", DomainObjectSet.builder().value(1).value(2).value(3).build());

        BinaryConstraint constraint = BinaryConstraint.of(A, B, BinaryNotEqualsRelation.builder().left(A).right(B).build());

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variable(A)
                .variable(B)
                .constraint(constraint)
                .build();

        // Setup a partial assignment where B is already assigned
        Assignment assignment = new Assignment(Map.of(B, 1));

        // Create system under test
        LeastConstrainingValueOrderer orderer = new LeastConstrainingValueOrderer();

        // Execute
        List<?> orderedValues = orderer.order(csp, A, assignment);

        // Verify (Values remain ordered based solely on constraints for unassigned neighbors)
        assertEquals(List.of(1, 2, 3), orderedValues);
    }
}