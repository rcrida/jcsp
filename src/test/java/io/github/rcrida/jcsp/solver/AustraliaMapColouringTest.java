package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AustraliaMapColouringTest {
    public enum Colour {
        RED, GREEN, BLUE
    }

    public static Domain<Colour> DOMAIN = EnumDomain.allOf(Colour.class);
    static Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    public static Variable<Colour> WA = VARIABLE_FACTORY.create("WA");
    public static Variable<Colour> NT = VARIABLE_FACTORY.create("NT");
    public static Variable<Colour> Q = VARIABLE_FACTORY.create("Q");
    public static Variable<Colour> NSW = VARIABLE_FACTORY.create("NSW");
    public static Variable<Colour> V = VARIABLE_FACTORY.create("V");
    public static Variable<Colour> SA = VARIABLE_FACTORY.create("SA");
    public static Variable<Colour> T = VARIABLE_FACTORY.create("T");

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
        assertThat(optionalSolution).hasValueSatisfying(value -> {
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
                                    WA, Colour.BLUE, NT, Colour.GREEN, Q, Colour.BLUE, NSW, Colour.GREEN, V, Colour.BLUE, SA, Colour.RED, T, Colour.RED)));
                    assertThat(value.getStatistics().getNodesExplored().get()).isLessThanOrEqualTo(6);
                    assertThat(value.getStatistics().getConstraintChecks().get()).isLessThanOrEqualTo(418);
                }
        );
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
        val optionalSolution = solver.getLocalSolution(csp, RandomAssignmentFactory.INSTANCE);
        System.out.println(optionalSolution);
    }
}
