package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-valued (continuous) variables via {@link IntervalDomain}: rent is fixed at 60.0, and
 * food/transport are unknowns over {@code [0, 100]}. A {@code sumConstraint} forces
 * {@code rent + food == 100}, and a {@code linearConstraint} forces
 * {@code rent + 5*transport == 120}. Both unknowns are resolved to singleton intervals
 * purely by SumConstraint/LinearConstraint bounds propagation — no backtracking search needed.
 */
public class RealValuedConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> RENT = F.create("rent");
    static final Variable<Double> FOOD = F.create("food");
    static final Variable<Double> TRANSPORT = F.create("transport");

    static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(RENT, IntervalDomain.of(60.0, 60.0))
                .variableDomain(FOOD, IntervalDomain.of(0.0, 100.0))
                .variableDomain(TRANSPORT, IntervalDomain.of(0.0, 100.0))
                .sumConstraint(Set.of(RENT, FOOD), Operator.EQ, 100.0)
                .linearConstraint(Map.of(RENT, 1.0, TRANSPORT, 5.0), Operator.EQ, 120.0)
                .build();
    }

    @Test
    void solvedEntirelyByBoundsPropagation() {
        var solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(problem()).toList();
        assertThat(solutions).hasSize(1);

        Assignment solution = solutions.get(0);
        assertThat(solution.getValue(RENT)).contains(60.0);
        assertThat(solution.getValue(FOOD)).contains(40.0);
        assertThat(solution.getValue(TRANSPORT)).contains(12.0);
    }

    @Test
    void infeasibleBudget_returnsNoSolutions() {
        // rent fixed at 60.0, but food can be at most 30.0 → rent + food <= 90.0 < 100.0
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(RENT, IntervalDomain.of(60.0, 60.0))
                .variableDomain(FOOD, IntervalDomain.of(0.0, 30.0))
                .sumConstraint(Set.of(RENT, FOOD), Operator.EQ, 100.0)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).isEmpty();
    }
}
