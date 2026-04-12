package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for the LeastConstrainingValueOrderer class.
 * This class verifies the behavior of the 'order' method,
 * which orders domain values according to the Least Constraining Value heuristic.
 */
class LeastConstrainingValueOrdererTest {
    static Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static Domain DOMAIN = DomainObjectSet.builder().value(1).value(2).value(3).build();

    @Test
    void singleBinaryConstraint() {
        // Setup CSP
        Variable A = FACTORY.create("A");
        Variable B = FACTORY.create("B");
        BinaryConstraint constraint = BinaryNotEqualsConstraint.builder().left(A).right(B).build();

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(A, DOMAIN)
                .variableDomain(B, DOMAIN)
                .constraint(constraint)
                .build();

        // Setup assignment
        Assignment assignment = Assignment.of(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = LeastConstrainingValueOrderer.INSTANCE;

        // Verify (Values should be ordered based on how many values they constrain for B)
        assertThat(orderer.order(csp, A, assignment).toList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void multipleBinaryConstraints() {
        // Setup CSP
        Variable A = FACTORY.create("A");
        Variable B = FACTORY.create("B");
        Variable C = FACTORY.create("C");

        BinaryConstraint constraint1 = BinaryNotEqualsConstraint.builder().left(A).right(B).build();
        BinaryConstraint constraint2 = BinaryNotEqualsConstraint.builder().left(A).right(C).build();

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(A, DOMAIN)
                .variableDomain(B, DOMAIN)
                .variableDomain(C, DOMAIN)
                .constraint(constraint1)
                .constraint(constraint2)
                .build();

        // Setup assignment
        Assignment assignment = Assignment.of(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = LeastConstrainingValueOrderer.INSTANCE;

        // Verify (Values should be ordered based on the combined constraints for B and C)
        assertThat(orderer.order(csp, A, assignment).toList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void noConstraints() {
        // Setup CSP with no constraints
        Variable A = FACTORY.create("A");

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(A, DOMAIN)
                .build();

        // Setup assignment
        Assignment assignment = Assignment.of(Map.of());

        // Create system under test
        LeastConstrainingValueOrderer orderer = LeastConstrainingValueOrderer.INSTANCE;

        // Verify (Values should remain in their original order)
        assertThat(orderer.order(csp, A, assignment).toList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void preAssignedVariable() {
        // Setup CSP
        Variable A = FACTORY.create("A");
        Variable B = FACTORY.create("B");

        BinaryConstraint constraint = BinaryNotEqualsConstraint.builder().left(A).right(B).build();

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(A, DOMAIN)
                .variableDomain(B, DOMAIN)
                .constraint(constraint)
                .build();

        // Setup a partial assignment where B is already assigned
        Assignment assignment = Assignment.of(Map.of(B, 1));

        // Create system under test
        LeastConstrainingValueOrderer orderer = LeastConstrainingValueOrderer.INSTANCE;

        // Verify (Values remain ordered based solely on constraints for unassigned neighbors)
        assertThat(orderer.order(csp, A, assignment).toList()).isEqualTo(List.of(1, 2, 3));
    }
}