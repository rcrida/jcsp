package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classic 2-job, 2-machine job-shop scheduling problem, optimized for makespan.
 * <pre>
 *   Job 1: machine A (3) -> machine B (2)
 *   Job 2: machine B (2) -> machine A (4)
 * </pre>
 *
 * <p>Within-job precedence is a {@code BinaryOffsetConstraint}. The disjunctive
 * "two operations on the same machine cannot overlap" rule has no dedicated constraint
 * type, but is exactly a {@link CumulativeConstraint} with a resource requirement of 1
 * per task and a capacity of 1 — a unary/disjunctive resource is just a cumulative one
 * with limit 1, so each machine's two operations are modelled with a single
 * {@code cumulativeConstraint} call rather than a hand-rolled boolean ordering indicator.
 *
 * <p>Machine loads (A: 3+4=7, B: 2+2=4) and job loads (job1: 3+2=5, job2: 2+4=6) give a
 * static lower bound of 7 on the makespan — achieved by running op11 and op22 back-to-back
 * on machine A with no idle time, so 7 is also the true optimum.
 *
 * <p>Uses {@link Solver.Factory#createSolver(ConstraintSatisfactionProblem, java.util.function.ToDoubleFunction)}
 * (discrete branch-and-bound); the objective is called on partial assignments during search,
 * so {@link #makespan} is written as an admissible lower bound rather than a value that
 * requires every variable to be assigned.
 */
public class JobShopSchedulingTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final int DUR_11 = 3; // job1, machine A
    static final int DUR_12 = 2; // job1, machine B
    static final int DUR_21 = 2; // job2, machine B
    static final int DUR_22 = 4; // job2, machine A

    static final IntRangeDomain HORIZON = IntRangeDomain.of(0, 7);

    static final Variable<Integer> START_11 = F.create("start11");
    static final Variable<Integer> START_12 = F.create("start12");
    static final Variable<Integer> START_21 = F.create("start21");
    static final Variable<Integer> START_22 = F.create("start22");

    static final int STATIC_LOWER_BOUND = 7;

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(START_11, HORIZON)
            .variableDomain(START_12, HORIZON)
            .variableDomain(START_21, HORIZON)
            .variableDomain(START_22, HORIZON)
            // within-job precedence
            .offsetConstraint(START_11, DUR_11, Operator.LEQ, START_12)
            .offsetConstraint(START_21, DUR_21, Operator.LEQ, START_22)
            // machine A: op11 and op22 must not overlap (unary resource, capacity 1)
            .cumulativeConstraint(List.of(START_11, START_22), List.of(DUR_11, DUR_22), List.of(1, 1), 1)
            // machine B: op12 and op21 must not overlap
            .cumulativeConstraint(List.of(START_12, START_21), List.of(DUR_12, DUR_21), List.of(1, 1), 1)
            .build();

    static double makespan(Assignment a) {
        double bound = STATIC_LOWER_BOUND;
        bound = Math.max(bound, completion(a, START_11, DUR_11));
        bound = Math.max(bound, completion(a, START_12, DUR_12));
        bound = Math.max(bound, completion(a, START_21, DUR_21));
        bound = Math.max(bound, completion(a, START_22, DUR_22));
        return bound;
    }

    static int completion(Assignment a, Variable<Integer> start, int duration) {
        return a.getValue(start).map(s -> s + duration).orElse(0);
    }

    @Test
    void optimize_findsMinimumMakespan() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, JobShopSchedulingTest::makespan).getSolution();
        assertThat(result).isPresent();
        assertThat(makespan(result.get())).isEqualTo(STATIC_LOWER_BOUND);
    }

    @Test
    void optimalSolutionRespectsPrecedenceAndMachineExclusion() {
        val solution = Solver.Factory.INSTANCE.createSolver(CSP, JobShopSchedulingTest::makespan).getSolution().orElseThrow();
        int s11 = solution.getValue(START_11).orElseThrow();
        int s12 = solution.getValue(START_12).orElseThrow();
        int s21 = solution.getValue(START_21).orElseThrow();
        int s22 = solution.getValue(START_22).orElseThrow();

        assertThat(s11 + DUR_11).isLessThanOrEqualTo(s12);
        assertThat(s21 + DUR_21).isLessThanOrEqualTo(s22);
        assertThat(s11 + DUR_11 <= s22 || s22 + DUR_22 <= s11).isTrue();
        assertThat(s12 + DUR_12 <= s21 || s21 + DUR_21 <= s12).isTrue();
    }

    @Test
    void getSolutions_returnsImprovingMakespans() {
        val improving = Solver.Factory.INSTANCE.createSolver(CSP, JobShopSchedulingTest::makespan).getSolutions().toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(makespan(improving.get(i))).isLessThan(makespan(improving.get(i - 1)));
        }
        assertThat(makespan(improving.getLast())).isEqualTo(STATIC_LOWER_BOUND);
    }
}
