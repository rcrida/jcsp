package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.AllDiffConstraint;
import org.jcsp.constraints.ExpressionConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.search.BacktrackingSearch;
import org.jcsp.search.order.LeastConstrainingValueOrderer;
import org.jcsp.search.selector.MinimumRemainingValuesSelector;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptarithmeticTest {
    static Domain DIGIT_DOMAIN = new IntRangeDomain(0, 9);
    static Domain CARRY_DOMAIN = new IntRangeDomain(0, 1);
    static Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};
    static Variable t = VARIABLE_FACTORY.create("T", DIGIT_DOMAIN);
    static Variable w = VARIABLE_FACTORY.create("W", DIGIT_DOMAIN);
    static Variable o = VARIABLE_FACTORY.create("O", DIGIT_DOMAIN);
    static Variable f = VARIABLE_FACTORY.create("F", DIGIT_DOMAIN);
    static Variable u = VARIABLE_FACTORY.create("U", DIGIT_DOMAIN);
    static Variable r = VARIABLE_FACTORY.create("R", DIGIT_DOMAIN);
    static Variable c1 = VARIABLE_FACTORY.create("C1", CARRY_DOMAIN);
    static Variable c2 = VARIABLE_FACTORY.create("C2", CARRY_DOMAIN);
    static Variable c3 = VARIABLE_FACTORY.create("C3", CARRY_DOMAIN);

    public static ConstraintSatisfactionProblem twoPlusTwoEqualsFour() {
        return ConstraintSatisfactionProblem.builder()
                .variables(Set.of(t, w, o, f, u, r, c1, c2, c3))
                .constraint(AllDiffConstraint.builder().variables(Set.of(t, w, o, f, u, r)).build())
                .constraint(ExpressionConstraint.builder().variables(Set.of(o, r, c1)).expression(assignment -> {
                    val O = (int) assignment.getValue(o).get();
                    val R = (int) assignment.getValue(r).get();
                    val C1 = (int) assignment.getValue(c1).get();
                    return O + O == R + 10 * C1;
                }).build())
                .constraint(ExpressionConstraint.builder().variables(Set.of(c1, w, u, c2)).expression(assignment -> {
                    val C1 = (int) assignment.getValue(c1).get();
                    val W = (int) assignment.getValue(w).get();
                    val U = (int) assignment.getValue(u).get();
                    val C2 = (int) assignment.getValue(c2).get();
                    return C1 + W + W == U + 10 * C2;
                }).build())
                .constraint(ExpressionConstraint.builder().variables(Set.of(c2, t, o, c3)).expression(assignment -> {
                    val C2 = (int) assignment.getValue(c2).get();
                    val T = (int) assignment.getValue(t).get();
                    val O = (int) assignment.getValue(o).get();
                    val C3 = (int) assignment.getValue(c3).get();
                    return C2 + T + T == O + 10 * C3;
                }).build())
                .constraint(ExpressionConstraint.builder().variables(Set.of(c3, f)).expression(assignment -> {
                    val C3 = (int) assignment.getValue(c3).get();
                    val F = (int) assignment.getValue(f).get();
                    return C3 == F;
                }).build())
                .build();
    }

    @Test
    void solution() {
        val csp = twoPlusTwoEqualsFour();
        val solver = new SolverImpl(new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer()));
        val optionalSolution = solver.getSolution(csp);
        System.out.println(optionalSolution);
        assertThat(optionalSolution).contains(new Assignment(Map.of(
                t, 1,
                w, 3,
                o, 2,
                f, 0,
                u, 6,
                r, 4,
                c1, 0,
                c2, 0,
                c3, 0
        )));
    }

    @Test
    void searchStream() {
        val csp = twoPlusTwoEqualsFour();
        val solver = new SolverImpl(new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer()));
        assertThat(solver.getSolutions(csp)).hasSize(19);
    }
}
