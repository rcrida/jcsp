package io.github.rcrida.jcsp.solver.backtrackingsearch;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ForcedAssignmentTest {

    private final Variable.Factory VF = new Variable.Factory();

    @Test
    void returnsTheSameAssignmentWhenNothingIsForced() {
        Variable<Integer> x = VF.create("fx");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        Assignment assignment = Assignment.empty();

        assertThat(ForcedAssignment.extend(csp, assignment)).containsSame(assignment);
    }

    @Test
    void assignsEverySingletonDiscreteVariableInOneStep() {
        Variable<Integer> x = VF.create("gx");
        Variable<Integer> y = VF.create("gy");
        Variable<Integer> z = VF.create("gz");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(2, 2))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(x, y)
                .build();

        Optional<Assignment> extended = ForcedAssignment.extend(csp, Assignment.empty());

        assertThat(extended).isPresent();
        assertThat(extended.get()).isEqualTo(Assignment.of(Map.of(x, 1, y, 2)));
    }

    @Test
    void leavesAnAlreadyAssignedSingletonAlone() {
        Variable<Integer> x = VF.create("hx");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .build();
        Assignment assignment = Assignment.of(Map.of(x, 1));

        assertThat(ForcedAssignment.extend(csp, assignment)).containsSame(assignment);
    }

    @Test
    void leavesASingletonContinuousVariableAlone() {
        Variable<Double> t = VF.create("it");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(t, new IntervalDomain(2.0, 2.0))
                .build();
        Assignment assignment = Assignment.empty();

        assertThat(ForcedAssignment.extend(csp, assignment)).containsSame(assignment);
    }

    @Test
    void rejectsAnExtensionThatViolatesAConstraintAndCountsTheBacktrack() {
        Variable<Integer> x = VF.create("jx");
        Variable<Integer> y = VF.create("jy");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        Assignment assignment = Assignment.empty();

        assertThat(ForcedAssignment.extend(csp, assignment)).isEmpty();
        assertThat(assignment.getStatistics().getBacktracks()).isEqualTo(1);
    }
}
