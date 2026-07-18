package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void absoluteDifferenceConstraint_leq_narrowsAndResolves() {
        // x∈[0,10], y∈[0,10], |x-y|<=2, x+y=12:
        // proximity: x∈[0-2,10+2]=[0,10] unchanged, y∈[0-2,10+2] unchanged (no narrowing from proximity alone)
        // sum propagation: x∈[2,10], y∈[2,10]; then proximity re-runs: no further change
        // snap x to midpoint 6.0, sum forces y=6.0; |6-6|=0 <= 2 ✓
        Variable<Double> x = F.create("x_ad"), y = F.create("y_ad");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .absoluteDifferenceConstraint(x, y, Operator.LEQ, 2.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 12.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.abs(xVal - yVal)).isLessThanOrEqualTo(2.0 + 1e-9);
        assertThat(xVal + yVal).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void absoluteDifferenceConstraint_leq_infeasible() {
        // x∈[0,2], y∈[7,10], |x-y|<=3: min dist = 7-2=5 > 3 → infeasible by propagation
        Variable<Double> x = F.create("x_adinf"), y = F.create("y_adinf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 2.0))
                .variableDomain(y, IntervalDomain.of(7.0, 10.0))
                .absoluteDifferenceConstraint(x, y, Operator.LEQ, 3.0)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void absoluteDifferenceConstraint_leq_clipsProximityThenResolvesWithLinear() {
        // x∈[0,10], y∈[6,10], |x-y|<=1: x∈[5,10]∩[0,10]=[5,10], y unchanged
        // linearConstraint: 2x+y=15: y=15-2x; with x∈[5,10] → y∈[-5,5]∩[6,10]=empty? No wait:
        // Actually x∈[5,10], y∈[6,10], |x-y|<=1 means x∈[5,11]∩[0,10]=[5,10], y∈[4,11]∩[6,10]=[6,10]
        // linear: 2x+y=15; y=15-2x; x∈[5,10]→y∈[-5,5]∩[6,10]=empty → infeasible.
        // Let's use x+y=14 instead: x∈[5,10],y∈[6,10]: x=14-y∈[4,8]∩[5,10]=[5,8]; y=14-x∈[6,9]∩[6,10]=[6,9]
        // snap x=6.5, sum y=7.5; |6.5-7.5|=1<=1 ✓
        Variable<Double> x = F.create("x_adlin"), y = F.create("y_adlin");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(6.0, 10.0))
                .absoluteDifferenceConstraint(x, y, Operator.LEQ, 1.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 14.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.abs(xVal - yVal)).isLessThanOrEqualTo(1.0 + 1e-9);
        assertThat(xVal + yVal).isCloseTo(14.0, within(1e-9));
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
    void maxConstraint_eq_forcedToSingleton() {
        // x∈[0,10], y∈[0,3], max(x,y)==5: propagation clips x to [0,5], then forces x=[5,5]
        // (only x can reach 5 after clip); y stays [0,3] and gets snapped to midpoint 1.5
        Variable<Double> x = F.create("mx_eq_x"), y = F.create("mx_eq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 3.0))
                .maxConstraint(Set.of(x, y), Operator.EQ, 5.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.max(xVal, yVal)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void maxConstraint_leq_clipsAndResolvesWithSum() {
        // x∈[0,10], y∈[0,10], max(x,y)<=8, x+y=10:
        // max propagation clips both to [0,8]; sum narrows both to [2,8]; snap x to 5.0, y=5.0
        Variable<Double> x = F.create("mx_leq_x"), y = F.create("mx_leq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .maxConstraint(Set.of(x, y), Operator.LEQ, 8.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 10.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.max(xVal, yVal)).isLessThanOrEqualTo(8.0 + 1e-9);
        assertThat(xVal + yVal).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void maxConstraint_geq_forcesMinAndResolvesWithSum() {
        // x∈[0,10], y∈[0,3], max(x,y)>=6, x+y=8:
        // only x can reach 6; x.min raised to 6; sum: y=8-x∈[8-10,8-6]=[−2,2]∩[0,3]=[0,2]
        // snap x to 8.0, sum forces y=0.0; max(8,0)=8>=6 ✓
        Variable<Double> x = F.create("mx_geq_x"), y = F.create("mx_geq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 3.0))
                .maxConstraint(Set.of(x, y), Operator.GEQ, 6.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 8.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.max(xVal, yVal)).isGreaterThanOrEqualTo(6.0 - 1e-9);
        assertThat(xVal + yVal).isCloseTo(8.0, within(1e-9));
    }

    @Test
    void maxConstraint_infeasible_returnsNoSolutions() {
        // x∈[6,10], y∈[7,9], max(x,y)<=5: globalMin=max(6,7)=7 > 5 → infeasible by propagation
        Variable<Double> x = F.create("mx_inf_x"), y = F.create("mx_inf_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(6.0, 10.0))
                .variableDomain(y, IntervalDomain.of(7.0, 9.0))
                .maxConstraint(Set.of(x, y), Operator.LEQ, 5.0)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void minConstraint_eq_forcedToSingleton() {
        // x∈[0,10], y∈[7,10], min(x,y)==5: raise x.min to 5→[5,10]; only x has min=5 → x=[5,5]
        Variable<Double> x = F.create("mn_eq_x"), y = F.create("mn_eq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(7.0, 10.0))
                .minConstraint(Set.of(x, y), Operator.EQ, 5.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.min(xVal, yVal)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void minConstraint_geq_raisesMinAndResolvesWithSum() {
        // x∈[0,10], y∈[0,10], min(x,y)>=3, x+y=8:
        // raise both mins to 3; sum narrows both to [3,5]; snap x to 4.0, sum forces y=4.0
        Variable<Double> x = F.create("mn_geq_x"), y = F.create("mn_geq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .minConstraint(Set.of(x, y), Operator.GEQ, 3.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 8.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.min(xVal, yVal)).isGreaterThanOrEqualTo(3.0 - 1e-9);
        assertThat(xVal + yVal).isCloseTo(8.0, within(1e-9));
    }

    @Test
    void minConstraint_leq_forcesMaxDownAndResolvesWithSum() {
        // x∈[0,10], y∈[6,10], min(x,y)<=4: only x can reach <=4; x.max clips to 4 → x∈[0,4]
        // sum x+y=6: x.max=min(4, 6-6)=0; x=[0,0], y=6.0
        Variable<Double> x = F.create("mn_leq_x"), y = F.create("mn_leq_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(6.0, 10.0))
                .minConstraint(Set.of(x, y), Operator.LEQ, 4.0)
                .sumConstraint(Set.of(x, y), Operator.EQ, 6.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(Math.min(xVal, yVal)).isLessThanOrEqualTo(4.0 + 1e-9);
        assertThat(xVal + yVal).isCloseTo(6.0, within(1e-9));
    }

    @Test
    void minConstraint_infeasible_returnsNoSolutions() {
        // x∈[0,3], y∈[0,4], min(x,y)>=5: smallest max=3 < 5 → infeasible by propagation
        Variable<Double> x = F.create("mn_inf_x"), y = F.create("mn_inf_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 3.0))
                .variableDomain(y, IntervalDomain.of(0.0, 4.0))
                .minConstraint(Set.of(x, y), Operator.GEQ, 5.0)
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

    @Test
    void divisionConstraint_intervalDomain_resolvedByPropagation() {
        // x is fixed at 6.0; y ∈ [1.0, 8.0]; x/y == 3.0
        // LEQ: newXMax=3*8=24>=6 no change; newYMin=6/3=2>1 → y.min raised to 2
        // GEQ: newXMin=3*2=6 no change (using updated yMin); newYMax=6/3=2 < 8 → y.max clipped to 2
        // → y = [2.0, 2.0]
        Variable<Double> x = F.create("dvx");
        Variable<Double> y = F.create("dvy");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(6.0, 6.0))
                .variableDomain(y, IntervalDomain.of(1.0, 8.0))
                .divisionConstraint(x, y, Operator.EQ, 3.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isCloseTo(6.0, within(1e-9));
        assertThat(yVal).isCloseTo(2.0, within(1e-9));
        assertThat(xVal / yVal).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void increasingConstraint_intervalDomain_disjointRangesAlwaysSatisfied() {
        // x∈[0,4], y∈[6,10]: x<=y holds for every possible pairing. IncreasingConstraint's own
        // OrderingPropagation pass leaves both domains untouched here (x's max of 4 already sits
        // below y's min of 6, so neither bound needs tightening) rather than snapping to a
        // specific point, but the constraint is genuinely satisfied at any pair of values drawn
        // from the two disjoint ranges, not merely at whatever point each gets snapped to.
        Variable<Double> x = F.create("inc_x"), y = F.create("inc_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 4.0))
                .variableDomain(y, IntervalDomain.of(6.0, 10.0))
                .increasingConstraint(List.of(x, y))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isLessThanOrEqualTo(yVal);
    }

    @Test
    void increasingConstraint_intervalDomain_infeasible_returnsNoSolutions() {
        // x∈[6,10], y∈[0,4]: x<=y is impossible for any pairing (x.min > y.max).
        // IncreasingConstraint's own OrderingPropagation pass catches this directly: x's running
        // min-floor (6) exceeds y's running max-ceiling (4), so propagate() reports infeasible
        // during preprocessing, before any bisection/snapping is needed.
        Variable<Double> x = F.create("inc_inf_x"), y = F.create("inc_inf_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(6.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 4.0))
                .increasingConstraint(List.of(x, y))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void decreasingConstraint_intervalDomain_disjointRangesAlwaysSatisfied() {
        // x∈[6,10], y∈[0,4]: x>=y holds for every possible pairing.
        Variable<Double> x = F.create("dec_x"), y = F.create("dec_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(6.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 4.0))
                .decreasingConstraint(List.of(x, y))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isGreaterThanOrEqualTo(yVal);
    }

    @Test
    void productConstraint_intervalDomain_resolvedByPropagation() {
        // x is fixed at 2.0; y ∈ [1.0, 8.0]; product == 6.0
        // LEQ clips y.max to k*y.min/productMin = 6*1/2 = 3.0;
        // GEQ raises y.min to k*y.max/productMax = 6*8/16 = 3.0 → y = [3.0, 3.0]
        Variable<Double> x = F.create("prx");
        Variable<Double> y = F.create("pry");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(2.0, 2.0))
                .variableDomain(y, IntervalDomain.of(1.0, 8.0))
                .productConstraint(Set.of(x, y), Operator.EQ, 6.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isCloseTo(2.0, within(1e-9));
        assertThat(yVal).isCloseTo(3.0, within(1e-9));
        assertThat(xVal * yVal).isCloseTo(6.0, within(1e-9));
    }

    @Test
    void unaryPredicateConstraint_intervalDomain_wholeRangeSatisfied() {
        // x∈[10,20]: predicate x>=5.0 holds across the whole domain, so NodeConsistency's
        // DiscreteDomain-only gate means no propagation ever touches x; correctness rests entirely
        // on the final isSatisfiedBy check after x is snapped to its midpoint.
        Variable<Double> x = F.create("up_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(10.0, 20.0))
                .predicateConstraint(x, (Double v) -> v >= 5.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        assertThat(xVal).isGreaterThanOrEqualTo(5.0);
    }

    @Test
    void unaryPredicateConstraint_intervalDomain_infeasible_returnsNoSolutions() {
        // x∈[0,3]: predicate x>=5.0 is false for every value in the domain, not just the midpoint.
        Variable<Double> x = F.create("up_xinf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 3.0))
                .predicateConstraint(x, (Double v) -> v >= 5.0)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void biPredicateConstraint_intervalDomain_disjointRangesAlwaysSatisfied() {
        // x∈[0,4], y∈[6,10]: x<y holds for every possible pairing.
        Variable<Double> x = F.create("bp_x"), y = F.create("bp_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 4.0))
                .variableDomain(y, IntervalDomain.of(6.0, 10.0))
                .biPredicateConstraint(x, y, (Double a, Double b) -> a < b)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal).isLessThan(yVal);
    }

    @Test
    void biPredicateConstraint_intervalDomain_infeasible_returnsNoSolutions() {
        // x∈[6,10], y∈[0,4]: x<y is impossible for any pairing.
        Variable<Double> x = F.create("bp_xinf"), y = F.create("bp_yinf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(6.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 4.0))
                .biPredicateConstraint(x, y, (Double a, Double b) -> a < b)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void predicateConstraint_nary_intervalDomain_satisfied() {
        // x,y,z∈[1,2]: sum of three positive values is always positive, regardless of midpoints.
        Variable<Double> x = F.create("pc_x"), y = F.create("pc_y"), z = F.create("pc_z");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(1.0, 2.0))
                .variableDomain(y, IntervalDomain.of(1.0, 2.0))
                .variableDomain(z, IntervalDomain.of(1.0, 2.0))
                .predicateConstraint(Set.of(x, y, z), a ->
                        (Double) a.getValue(x).orElseThrow()
                                + (Double) a.getValue(y).orElseThrow()
                                + (Double) a.getValue(z).orElseThrow() > 0.0)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
    }

    @Test
    void reifyConstraint_intervalDomain_bodyAlwaysTrue_forcesIndicatorTrue() {
        // x∈[10,20]: body "x>=5.0" holds for the whole domain, so the only consistent indicator
        // value is true. ReifiedConstraint isn't Propagatable and its body is never registered as
        // a top-level constraint, so this is resolved purely by search plus the final isSatisfiedBy
        // check, not by any dedicated propagator.
        Variable<Double> x = F.create("reif_x");
        Variable<Boolean> b = F.create("reif_b");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(10.0, 20.0))
                .variableDomain(b, BooleanDomain.INSTANCE)
                .reifyConstraint(b, UnaryComparatorConstraint.of(x, Operator.GEQ, 5.0))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(b)).contains(true);
    }

    @Test
    void reifyConstraint_intervalDomain_bodyAlwaysFalse_forcesIndicatorFalse() {
        // x∈[0,3]: body "x>=5.0" is false across the whole domain, so indicator must be false.
        Variable<Double> x = F.create("reif_xf");
        Variable<Boolean> b = F.create("reif_bf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 3.0))
                .variableDomain(b, BooleanDomain.INSTANCE)
                .reifyConstraint(b, UnaryComparatorConstraint.of(x, Operator.GEQ, 5.0))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(b)).contains(false);
    }

    @Test
    void impliesConstraint_intervalDomain_indicatorTrue_bodyMustHold() {
        // indicator pinned true via equalsConstraint; body "x>=5.0" holds unconditionally across
        // x's whole domain, so the problem is satisfiable.
        Variable<Double> x = F.create("impl_x");
        Variable<Boolean> b = F.create("impl_b");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(10.0, 20.0))
                .variableDomain(b, BooleanDomain.INSTANCE)
                .equalsConstraint(b, true)
                .impliesConstraint(b, UnaryComparatorConstraint.of(x, Operator.GEQ, 5.0))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(b)).contains(true);
    }

    @Test
    void impliesConstraint_intervalDomain_indicatorTrue_bodyViolated_infeasible() {
        // indicator pinned true; body "x>=5.0" is false across x's whole domain -> infeasible.
        Variable<Double> x = F.create("impl_xinf");
        Variable<Boolean> b = F.create("impl_binf");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 3.0))
                .variableDomain(b, BooleanDomain.INSTANCE)
                .equalsConstraint(b, true)
                .impliesConstraint(b, UnaryComparatorConstraint.of(x, Operator.GEQ, 5.0))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void naryElementConstraint_intervalDomain_resolvedBySearchOverIndex() {
        // result and vars are singleton IntervalDomains; index (discrete, {1,2,3}) is the only
        // unresolved variable. Element propagation is a no-op here (result/vars aren't
        // DiscreteDomain), so search must try each index value and the final isSatisfiedBy check
        // picks out index=2, the only one whose vars[index-1] equals result.
        Variable<Integer> index = F.create("ne_index");
        Variable<Double> result = F.create("ne_result");
        Variable<Double> v1 = F.create("ne_v1"), v2 = F.create("ne_v2"), v3 = F.create("ne_v3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(index, IntRangeDomain.of(1, 3))
                .variableDomain(result, IntervalDomain.of(20.0, 20.0))
                .variableDomain(v1, IntervalDomain.of(10.0, 10.0))
                .variableDomain(v2, IntervalDomain.of(20.0, 20.0))
                .variableDomain(v3, IntervalDomain.of(30.0, 30.0))
                .elementVariableConstraint(index, result, List.of(v1, v2, v3))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(index)).contains(2);
    }

    @Test
    void naryElementConstraint_intervalDomain_infeasible_returnsNoSolutions() {
        // result (25.0) matches none of vars' singleton values (10, 20, 30) for any index choice.
        Variable<Integer> index = F.create("ne_index_inf");
        Variable<Double> result = F.create("ne_result_inf");
        Variable<Double> v1 = F.create("ne_v1i"), v2 = F.create("ne_v2i"), v3 = F.create("ne_v3i");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(index, IntRangeDomain.of(1, 3))
                .variableDomain(result, IntervalDomain.of(25.0, 25.0))
                .variableDomain(v1, IntervalDomain.of(10.0, 10.0))
                .variableDomain(v2, IntervalDomain.of(20.0, 20.0))
                .variableDomain(v3, IntervalDomain.of(30.0, 30.0))
                .elementVariableConstraint(index, result, List.of(v1, v2, v3))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void reifyConstraint_bodyIncompatibleWithIntervalDomain_rejectedAtBuildTime() {
        // AllDiffConstraint is explicitly rejected over IntervalDomain when used directly
        // (density-of-reals argument — see CONTINUOUS_COMPATIBLE_CONSTRAINTS). Wrapping it inside
        // reifyConstraint must not let it bypass that check: the body is never registered as a
        // top-level constraint, so validateContinuousCompatibility has to recurse into it.
        Variable<Double> x = F.create("reif_ad_x"), y = F.create("reif_ad_y");
        Variable<Boolean> b = F.create("reif_ad_b");
        var builder = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .variableDomain(b, BooleanDomain.INSTANCE)
                .reifyConstraint(b, AllDiffConstraint.<Double>builder().variables(Set.of(x, y)).build());
        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }
}
