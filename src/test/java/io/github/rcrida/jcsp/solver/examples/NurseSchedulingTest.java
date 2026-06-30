package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.Automaton;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A nurse works a 5-day schedule; each day is assigned Day (D), Night (N), or Rest (R).
 * Two workplace rules are encoded as a DFA and enforced via {@code regularConstraint}:
 * <ol>
 *   <li>After a Night shift the next day must be Rest.</li>
 *   <li>At most 2 consecutive working days (Day or Night).</li>
 * </ol>
 *
 * <p>DFA states:
 * <pre>
 *   0 – fresh / after Rest      D→1  N→2  R→0
 *   1 – after 1 consecutive Day D→3  N→2  R→0
 *   2 – must rest (after N)          R→0
 *   3 – must rest (after D→D)        R→0
 * </pre>
 * All four states are accepting. A forward-backward DP confirms 79 valid 5-day schedules.
 */
public class NurseSchedulingTest {

    enum Shift { D, N, R }

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int DAYS = 5;

    static final List<Variable<Shift>> SCHEDULE = IntStream.range(0, DAYS)
            .mapToObj(i -> F.<Shift>create("day" + (i + 1)))
            .toList();

    // State 2 covers "must rest" after a Night shift (rule 1).
    // State 3 covers "must rest" after two consecutive Day shifts (rule 2).
    // D→N from state 1 reaches state 2, satisfying both rules simultaneously.
    static final Automaton<Shift> DFA = Automaton.of(
            4,
            0,
            Set.of(0, 1, 2, 3),
            Map.of(
                    0, Map.of(Shift.D, 1, Shift.N, 2, Shift.R, 0),
                    1, Map.of(Shift.D, 3, Shift.N, 2, Shift.R, 0),
                    2, Map.of(Shift.R, 0),
                    3, Map.of(Shift.R, 0)
            )
    );

    static ConstraintSatisfactionProblem buildProblem() {
        var builder = ConstraintSatisfactionProblem.builder();
        SCHEDULE.forEach(v -> builder.variableDomain(v, EnumDomain.allOf(Shift.class)));
        builder.regularConstraint(SCHEDULE, DFA);
        return builder.build();
    }

    @Test
    void allValidSchedules_satisfyShiftRules() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildProblem()).getSolutions().toList();

        assertThat(solutions).hasSize(79);

        solutions.forEach(assignment -> {
            List<Shift> shifts = SCHEDULE.stream()
                    .map(v -> assignment.getValue(v).orElseThrow())
                    .toList();

            for (int i = 0; i < shifts.size() - 1; i++) {
                if (shifts.get(i) == Shift.N) {
                    assertThat(shifts.get(i + 1))
                            .as("day %d is Night so day %d must be Rest", i + 1, i + 2)
                            .isEqualTo(Shift.R);
                }
            }

            for (int i = 0; i < shifts.size() - 2; i++) {
                boolean threeConsecutiveWork = shifts.get(i) != Shift.R
                        && shifts.get(i + 1) != Shift.R
                        && shifts.get(i + 2) != Shift.R;
                assertThat(threeConsecutiveWork)
                        .as("days %d-%d must not all be work shifts", i + 1, i + 3)
                        .isFalse();
            }
        });
    }
}
