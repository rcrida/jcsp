package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.EnumDomain;
import org.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AustraliaMapColouringTest {
    public enum Colour {
        RED, GREEN, BLUE
    }

    public static Domain DOMAIN = EnumDomain.allOf(Colour.class);
    static Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    public static Variable WA = VARIABLE_FACTORY.create("WA");
    public static Variable NT = VARIABLE_FACTORY.create("NT");
    public static Variable Q = VARIABLE_FACTORY.create("Q");
    public static Variable NSW = VARIABLE_FACTORY.create("NSW");
    public static Variable V = VARIABLE_FACTORY.create("V");
    public static Variable SA = VARIABLE_FACTORY.create("SA");
    public static Variable T = VARIABLE_FACTORY.create("T");

    public static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, DOMAIN)
                .variableDomain(NT, DOMAIN)
                .variableDomain(Q, DOMAIN)
                .variableDomain(NSW, DOMAIN)
                .variableDomain(V, DOMAIN)
                .variableDomain(SA, DOMAIN)
                .variableDomain(T, DOMAIN)
                .notEqualsConstraint(SA, WA)
                .notEqualsConstraint(SA, NT)
                .notEqualsConstraint(SA, Q)
                .notEqualsConstraint(SA, NSW)
                .notEqualsConstraint(SA, V)
                .notEqualsChainConstraint(List.of(WA, NT, Q, NSW, V))
                .build();
    }

    @Test
    void solution() {
        val csp = problem();
        assertThat(csp.getSearchSpace()).isEqualTo(BigInteger.valueOf(2187));
        val optionalSolution = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        System.out.println(optionalSolution);
        assertThat(optionalSolution).hasValueSatisfying(value ->
                assertThat(value).isIn(
                        Assignment.of(Map.of(
                                WA, Colour.RED, NT, Colour.GREEN, Q, Colour.RED, NSW, Colour.GREEN, V, Colour.RED, SA, Colour.BLUE, T, Colour.RED)),
                        Assignment.of(Map.of(
                                WA, Colour.RED, NT, Colour.BLUE, Q, Colour.RED, NSW, Colour.BLUE, V, Colour.RED, SA, Colour.GREEN, T, Colour.RED)),
                        Assignment.of(Map.of(
                                WA, Colour.BLUE, NT, Colour.RED, Q, Colour.BLUE, NSW, Colour.RED, V, Colour.BLUE, SA, Colour.GREEN, T, Colour.RED)),
                        Assignment.of(Map.of(
                                WA, Colour.GREEN, NT, Colour.RED, Q, Colour.GREEN, NSW, Colour.RED, V, Colour.GREEN, SA, Colour.BLUE, T, Colour.RED)),
                        Assignment.of(Map.of(
                                WA, Colour.GREEN, NT, Colour.BLUE, Q, Colour.GREEN, NSW, Colour.BLUE, V, Colour.GREEN, SA, Colour.RED, T, Colour.RED)),
                        Assignment.of(Map.of(
                                WA, Colour.BLUE, NT, Colour.GREEN, Q, Colour.BLUE, NSW, Colour.GREEN, V, Colour.BLUE, SA, Colour.RED, T, Colour.RED))
                ));
    }

    @Test
    void searchStream() {
        val csp = problem();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(18);
    }

    @Test
    void localSolution() {
        val csp = problem();
        val solver = new MinConflictsSolver(500);
        val optionalSolution = solver.getLocalSolution(csp, new RandomAssignmentFactory());
        System.out.println(optionalSolution);
    }
}
