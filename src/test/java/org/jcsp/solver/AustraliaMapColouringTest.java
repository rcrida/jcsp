package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.DefaultInference;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.EnumDomain;
import org.jcsp.search.BacktrackingSearch;
import org.jcsp.search.order.LeastConstrainingValueOrderer;
import org.jcsp.search.selector.MinimumRemainingValuesSelector;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AustraliaMapColouringTest {
    enum Colour {
        RED, GREEN, BLUE
    }

    public static Domain DOMAIN = EnumDomain.allOf(Colour.class);
    static Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};
    static Variable WA = VARIABLE_FACTORY.create("WA", DOMAIN);
    static Variable NT = VARIABLE_FACTORY.create("NT", DOMAIN);
    static Variable Q = VARIABLE_FACTORY.create("Q", DOMAIN);
    static Variable NSW = VARIABLE_FACTORY.create("NSW", DOMAIN);
    static Variable V = VARIABLE_FACTORY.create("V", DOMAIN);
    static Variable SA = VARIABLE_FACTORY.create("SA", DOMAIN);
    static Variable T = VARIABLE_FACTORY.create("T", DOMAIN);

    public static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variables(Set.of(WA, NT, Q, NSW, V, SA, T))
                .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(WA).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(NT).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(Q).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(NSW).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(V).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(WA).right(NT).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(NT).right(Q).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(Q).right(NSW).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(NSW).right(V).build())
                .build();
    }

    @Test
    void solution() {
        val csp = problem();
        val solver = new SolverImpl(DefaultInference.INSTANCE, new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer(), DefaultInference.INSTANCE));
        val optionalSolution = solver.getSolution(csp);
        System.out.println(optionalSolution);
        assertThat(optionalSolution).hasValueSatisfying(value ->
                assertThat(value).isIn(
                        new Assignment(Map.of(
                                WA, Colour.RED, NT, Colour.GREEN, Q, Colour.RED, NSW, Colour.GREEN, V, Colour.RED, SA, Colour.BLUE, T, Colour.RED)),
                        new Assignment(Map.of(
                                WA, Colour.RED, NT, Colour.BLUE, Q, Colour.RED, NSW, Colour.BLUE, V, Colour.RED, SA, Colour.GREEN, T, Colour.RED)),
                        new Assignment(Map.of(
                                WA, Colour.BLUE, NT, Colour.RED, Q, Colour.BLUE, NSW, Colour.RED, V, Colour.BLUE, SA, Colour.GREEN, T, Colour.RED)),
                        new Assignment(Map.of(
                                WA, Colour.GREEN, NT, Colour.RED, Q, Colour.GREEN, NSW, Colour.RED, V, Colour.GREEN, SA, Colour.BLUE, T, Colour.RED)),
                        new Assignment(Map.of(
                                WA, Colour.GREEN, NT, Colour.BLUE, Q, Colour.GREEN, NSW, Colour.BLUE, V, Colour.GREEN, SA, Colour.RED, T, Colour.RED)),
                        new Assignment(Map.of(
                                WA, Colour.BLUE, NT, Colour.GREEN, Q, Colour.BLUE, NSW, Colour.GREEN, V, Colour.BLUE, SA, Colour.RED, T, Colour.RED))
                ));
    }

    @Test
    void searchStream() {
        val csp = problem();
        val solver = new SolverImpl(DefaultInference.INSTANCE, new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer(), DefaultInference.INSTANCE));
        assertThat(solver.getSolutions(csp)).hasSize(18);
    }
}
