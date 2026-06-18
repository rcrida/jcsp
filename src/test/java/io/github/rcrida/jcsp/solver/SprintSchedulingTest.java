package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 4-day sprint contains three tasks scheduled on a 2-person team.
 * <pre>
 *   Task    | Duration | Devs required
 *   --------|----------|---------------
 *   dev1    |    2     |     1
 *   dev2    |    2     |     1
 *   review  |    1     |     2   (full team)
 * </pre>
 *
 * <p>{@code dev1} and {@code dev2} may run concurrently (1+1 = 2 ≤ capacity).
 * {@code review} must run alone (adding any other task would exceed capacity 2).
 *
 * <p>With start-time domains in [0, 2] (all tasks finish within the 4-day horizon),
 * there are exactly 6 valid schedules — enumerated manually to validate the solver.
 *
 * <p>Uses {@link io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint} with the
 * timetabling propagator active in the solver chain.
 */
public class SprintSchedulingTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> DEV1   = F.create("dev1");
    static final Variable<Integer> DEV2   = F.create("dev2");
    static final Variable<Integer> REVIEW = F.create("review");

    static final IntRangeDomain HORIZON = IntRangeDomain.of(0, 2);

    static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(DEV1,   HORIZON)
                .variableDomain(DEV2,   HORIZON)
                .variableDomain(REVIEW, HORIZON)
                .cumulativeConstraint(
                        List.of(DEV1, DEV2, REVIEW),
                        List.of(2,    2,    1),       // durations
                        List.of(1,    1,    2),       // resource requirements
                        2)                            // team capacity
                .build();
    }

    @Test
    void allSolutions() {
        // 6 valid schedules:
        //   review at 0: dev1 and dev2 must start ≥ 1  → (1,1), (1,2), (2,1), (2,2) — 4 schedules
        //   review at 1: both devs must start at 2     → (2,2,1)                    — 1 schedule
        //   review at 2: both devs must start at 0     → (0,0,2)                    — 1 schedule
        val solutions = Solver.Factory.INSTANCE.createSolver(problem()).getSolutions().toList();
        assertThat(solutions).hasSize(6);
        solutions.forEach(a -> System.out.println(
                "dev1=" + a.getValue(DEV1).orElseThrow() +
                " dev2=" + a.getValue(DEV2).orElseThrow() +
                " review=" + a.getValue(REVIEW).orElseThrow()));
    }
}
