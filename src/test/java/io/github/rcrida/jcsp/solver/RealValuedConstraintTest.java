package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
        var solutions = Solver.Factory.INSTANCE.createSolver(problem()).getSolutions().toList();
        assertThat(solutions).hasSize(1);

        Assignment solution = solutions.get(0);
        assertThat(solution.getValue(RENT)).contains(60.0);
        assertThat(solution.getValue(FOOD)).contains(40.0);
        assertThat(solution.getValue(TRANSPORT)).contains(12.0);
    }

    @Test
    void underdetermined_midpointSnapResolvesSystem() {
        // x + y = 7.0 with x, y ∈ [0.0, 5.0]; propagation narrows both to [2.0, 5.0] but neither becomes singleton;
        // PropagationFixpointSolver snaps the widest (x) to midpoint 3.5, then propagation forces y = 3.5
        Variable<Double> x = F.create("x");
        Variable<Double> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 5.0))
                .variableDomain(y, IntervalDomain.of(0.0, 5.0))
                .sumConstraint(Set.of(x, y), Operator.EQ, 7.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal + yVal).isCloseTo(7.0, within(1e-9));
    }

    @Test
    void comparatorConstraint_clipsInterval() {
        // x ∈ [0, 10], x >= 3.0 and x <= 7.0: propagation clips to [3.0, 7.0];
        // sumConstraint x + y = 5.0 with y ∈ [0, 10] forces y to [0, 2] after clipping x.
        Variable<Double> x = F.create("x_cc");
        Variable<Double> y = F.create("y_cc");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(x, Operator.GEQ, 3.0)
                .comparatorConstraint(x, Operator.LEQ, 7.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 10.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isBetween(3.0, 7.0);
        assertThat(xVal + yVal).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void binaryComparatorConstraint_propagatesBetweenIntervals() {
        // x ∈ [0,10], y ∈ [0,10], x <= y, x + y = 6:
        // propagation: x.max = min(10, 10) = 10, y.min = max(0, 0) = 0 (no change from comparator alone)
        // sum propagation: x.max = 6 - y.min = 6, y.max = 6 - x.min = 6
        // comparator re-runs: x.max = min(6, 6) = 6, y.min = max(0, 0) = 0 — converges
        // midpoint snap: x snapped to 3.0, sum forces y = 3.0
        Variable<Double> x = F.create("x_bc");
        Variable<Double> y = F.create("y_bc");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(x, Operator.LEQ, y)
                .sumConstraint(Set.of(x, y), Operator.EQ, 6.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isLessThanOrEqualTo(yVal + 1e-9);
        assertThat(xVal + yVal).isCloseTo(6.0, within(1e-9));
    }

    @Test
    void offsetConstraint_propagatesBetweenIntervals() {
        // x∈[0,10], y∈[0,10], x+3.0=y: propagation clips x to [0,7] and y to [3,10].
        // Sum x+y=10 then forces x=[0,7]∩[0,7]=? → snap x to 3.5, sum forces y=6.5; 3.5+3=6.5 ✓
        Variable<Double> x = F.create("x_off"), y = F.create("y_off");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .offsetConstraint(x, 3.0, Operator.EQ, y)
                .sumConstraint(Set.of(x, y), Operator.EQ, 10.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal + 3.0).isCloseTo(yVal, within(1e-9));
        assertThat(xVal + yVal).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void offsetConstraint_leq_clipsUpperBound() {
        // x∈[0,10], y∈[0,10], x+3<=y: propagation clips x.max to 7; sum x+y=12 resolves both.
        Variable<Double> x = F.create("x_oleq"), y = F.create("y_oleq");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .offsetConstraint(x, 3.0, Operator.LEQ, y)
                .sumConstraint(Set.of(x, y), Operator.EQ, 12.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal + 3.0).isLessThanOrEqualTo(yVal + 1e-9);
        assertThat(xVal + yVal).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void offsetConstraint_infeasible_returnsNoSolutions() {
        // x∈[5,10], y∈[0,3], x+3<=y: x.min+3=8 > y.max=3 → infeasible detected by propagation
        Variable<Double> x = F.create("x_oinf"), y = F.create("y_oinf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(5.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 3.0))
                .offsetConstraint(x, 3.0, Operator.LEQ, y)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void lexConstraint_clipsIntervalAndResolvesWithSum() {
        // x∈[0,10], y∈[0,4], [x] lex<= [y]: propagation clips x.max to y.max=4 → x∈[0,4].
        // sum x+y=6 then narrows both to [2,4]; snap x to 3.0, sum forces y=3.0. x<=y ✓
        Variable<Double> x = F.create("x_lex"), y = F.create("y_lex");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 4.0))
                .lexConstraint(List.of(x), Operator.LEQ, List.of(y))
                .sumConstraint(Set.of(x, y), Operator.EQ, 6.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isLessThanOrEqualTo(yVal + 1e-9);
        assertThat(xVal + yVal).isCloseTo(6.0, within(1e-9));
    }

    @Test
    void lexConstraint_infeasible_returnsNoSolutions() {
        // x∈[5,10], y∈[0,3], [x] lex<= [y]: x.min=5 > y.max=3 → infeasible
        Variable<Double> x = F.create("x_linf"), y = F.create("y_linf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(5.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 3.0))
                .lexConstraint(List.of(x), Operator.LEQ, List.of(y))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void infeasibleBudget_returnsNoSolutions() {
        // rent fixed at 60.0, but food can be at most 30.0 → rent + food <= 90.0 < 100.0
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(RENT, IntervalDomain.of(60.0, 60.0))
                .variableDomain(FOOD, IntervalDomain.of(0.0, 30.0))
                .sumConstraint(Set.of(RENT, FOOD), Operator.EQ, 100.0)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void cumulativeConstraint_intervalStarts_resolvedByPropagation() {
        // Three serial tasks (resource=1, limit=1), each duration=2.0.
        // x1 and x2 are fixed via singleton IntervalDomains, giving them compulsory parts
        // that fill [0,4). Propagation must then tighten x3 from [0,4] to the singleton [4,4],
        // making the whole problem fully determined without any search.
        Variable<Double> x1 = F.create("cx1");
        Variable<Double> x2 = F.create("cx2");
        Variable<Double> x3 = F.create("cx3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntervalDomain.of(0.0, 0.0))
                .variableDomain(x2, IntervalDomain.of(2.0, 2.0))
                .variableDomain(x3, IntervalDomain.of(0.0, 4.0))
                .cumulativeConstraint(List.of(x1, x2, x3), List.of(2.0, 2.0, 2.0), List.of(1.0, 1.0, 1.0), 1.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double s1 = (Double) solution.get().getValue(x1).orElseThrow();
        double s2 = (Double) solution.get().getValue(x2).orElseThrow();
        double s3 = (Double) solution.get().getValue(x3).orElseThrow();
        assertThat(s1).isEqualTo(0.0);
        assertThat(s2).isEqualTo(2.0);
        assertThat(s3).isEqualTo(4.0);
    }
}
