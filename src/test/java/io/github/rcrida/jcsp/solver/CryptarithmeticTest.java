package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptarithmeticTest {
    static Domain<Integer> DIGIT_DOMAIN = IntRangeDomain.of(0, 9);
    static Domain<Integer> CARRY_DOMAIN = IntRangeDomain.of(0, 1);
    static Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static Variable<Integer> t = VARIABLE_FACTORY.create("T");
    static Variable<Integer> w = VARIABLE_FACTORY.create("W");
    static Variable<Integer> o = VARIABLE_FACTORY.create("O");
    static Variable<Integer> f = VARIABLE_FACTORY.create("F");
    static Variable<Integer> u = VARIABLE_FACTORY.create("U");
    static Variable<Integer> r = VARIABLE_FACTORY.create("R");
    static Variable<Integer> c1 = VARIABLE_FACTORY.create("C1");
    static Variable<Integer> c2 = VARIABLE_FACTORY.create("C2");
    static Variable<Integer> c3 = VARIABLE_FACTORY.create("C3");

    public static ConstraintSatisfactionProblem twoPlusTwoEqualsFour() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(t, DIGIT_DOMAIN)
                .variableDomain(w, DIGIT_DOMAIN)
                .variableDomain(o, DIGIT_DOMAIN)
                .variableDomain(f, DIGIT_DOMAIN)
                .variableDomain(u, DIGIT_DOMAIN)
                .variableDomain(r, DIGIT_DOMAIN)
                .variableDomain(c1, CARRY_DOMAIN)
                .variableDomain(c2, CARRY_DOMAIN)
                .variableDomain(c3, CARRY_DOMAIN)
                .allDiffConstraint(Set.of(t, w, o, f, u, r))
                .predicateConstraint(Set.of(o, r, c1), assignment -> {
                    val O = (int) assignment.getValue(o).get();
                    val R = (int) assignment.getValue(r).get();
                    val C1 = (int) assignment.getValue(c1).get();
                    return O + O == R + 10 * C1;
                })
                .predicateConstraint(Set.of(c1, w, u, c2), assignment -> {
                    val C1 = (int) assignment.getValue(c1).get();
                    val W = (int) assignment.getValue(w).get();
                    val U = (int) assignment.getValue(u).get();
                    val C2 = (int) assignment.getValue(c2).get();
                    return C1 + W + W == U + 10 * C2;
                })
                .predicateConstraint(Set.of(c2, t, o, c3), assignment -> {
                    val C2 = (int) assignment.getValue(c2).get();
                    val T = (int) assignment.getValue(t).get();
                    val O = (int) assignment.getValue(o).get();
                    val C3 = (int) assignment.getValue(c3).get();
                    return C2 + T + T == O + 10 * C3;
                })
                .predicateConstraint(Set.of(c3, f), assignment -> {
                    val C3 = (int) assignment.getValue(c3).get();
                    val F = (int) assignment.getValue(f).get();
                    return C3 == F;
                })
                .notEqualsConstraint(t, 0)
                .notEqualsConstraint(f, 0)
                .build();
    }

    @Test
    void solution() {
        val csp = twoPlusTwoEqualsFour();
        assertThat(csp.getSearchSpace()).isEqualTo(BigInteger.valueOf(8000000));
        val solver = Solver.Factory.INSTANCE.createSolver();
        val optionalSolution = solver.getSolution(csp);
        System.out.println(optionalSolution);
        assertThat(optionalSolution).hasValueSatisfying(value -> {
            assertThat(value).isEqualTo(Assignment.of(Map.of(
                    t, 7,
                    w, 3,
                    o, 4,
                    f, 1,
                    u, 6,
                    r, 8,
                    c1, 0,
                    c2, 0,
                    c3, 1
            )));
            assertThat(value.getStatistics().getNodesExplored().get()).isLessThanOrEqualTo(477);
            assertThat(value.getStatistics().getConstraintChecks().get()).isLessThanOrEqualTo(1790);
        });
    }

    @Test
    void searchStream() {
        val csp = twoPlusTwoEqualsFour();
        val solver = Solver.Factory.INSTANCE.createSolver();
        val solutions = solver.getSolutions(csp).toList();
        System.out.println(solutions);
        assertThat(solutions).hasSize(7);
    }

    @Test
    void localSolution() {
        val csp = twoPlusTwoEqualsFour();
        val solver = new MinConflictsSolver(500);
        val optionalSolution = solver.getLocalSolution(csp, RandomAssignmentFactory.INSTANCE);
        // don't reliably find a solution because ths search space is sparse and we don't model all of the constraints as binary constraints
        System.out.println(optionalSolution);
    }
}
