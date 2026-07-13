package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinearConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x");
    Variable<Integer> y = F.create("y");

    // 2*x + 3*y == 12
    LinearConstraint<Integer> eq12;

    @BeforeEach
    void setUp() {
        eq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12);
    }

    @Test
    void weightedSum_satisfied() {
        // 2*0 + 3*4 = 12
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 4)))).isTrue();
        // 2*3 + 3*2 = 12
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 2)))).isTrue();
    }

    @Test
    void weightedSum_notSatisfied() {
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isFalse(); // 2+3=5
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 3)))).isFalse(); // 4+9=13
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 3)))).isTrue();
    }

    @Test
    void leq_satisfied() {
        var leq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 4)))).isTrue();
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isTrue();
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 5, y, 1)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(eq12.toString()).isEqualTo("<(x, y), 2*x + 3*y == 12>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LinearConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12)).isEqualTo(eq12);
    }

    @Test
    void weightedSum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, (byte) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 3, b, (byte) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 1, b, (byte) 1)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, (short) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 3, b, (short) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 1, b, (short) 1)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, 12L);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3L, b, 2L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1L, b, 1L)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, 12.0f);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0f, b, 2.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0f, b, 1.0f)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, 12.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0, b, 2.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0, b, 1.0)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedBoundType() {
        Variable<Number> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.<Number>builder()
                .variables(java.util.Set.of(a, b))
                .coefficients(Map.of(a, (Number) 2, b, (Number) 3))
                .bound(new AtomicInteger(12))
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, 3, b, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported bound type");
    }

    @Test
    void solver_infeasibleLinearConstraint_returnsNoSolutions() {
        // 2*x + 3*y == 50, domain {0..1}: max sum = 5 < 50 → infeasible detected by propagation
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 1))
                .variableDomain(y, IntRangeDomain.of(0, 1))
                .linearConstraint(Map.of(x, 2, y, 3), Operator.EQ, 50)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void solver_findsExactSolutions() {
        // 2*x + 3*y == 12, domain {0..4}: solutions are (0,4) and (3,2)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 4))
                .variableDomain(y, IntRangeDomain.of(0, 4))
                .linearConstraint(Map.of(x, 2, y, 3), Operator.EQ, 12)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }

    // --- propagate() ---

    @Test
    void propagate_eq_tightensBounds() {
        // 2*x + 3*y == 12, y fixed at 2: 2*x == 6 → x == 3
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                y, IntRangeDomain.of(2, 2));
        var result = eq12.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(3, 3));
    }

    @Test
    void propagate_leq_tightensUpperBound() {
        // 2*x + 3*y <= 12, y fixed at 2: 2*x <= 6 → x <= 3
        var leq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                y, IntRangeDomain.of(2, 2));
        var result = leq12.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(0, 3));
    }

    @Test
    void propagate_geq_tightensLowerBound() {
        // 2*x + 3*y >= 12, y fixed at 2: 2*x >= 6 → x >= 3
        var geq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.GEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                y, IntRangeDomain.of(2, 2));
        var result = geq12.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(3, 9));
    }

    @Test
    void propagate_negativeCoefficient_eq_tightensBounds() {
        // -x + y == 3, x∈{0..5}, y∈{0..5} → x pruned to {0..2}, y pruned to {3..5}
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearConstraint.of(Map.of(nx, -1, ny, 1), Operator.EQ, 3);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, IntRangeDomain.of(0, 5),
                ny, IntRangeDomain.of(0, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(nx)).isEqualTo(IntRangeDomain.of(0, 2));
        assertThat(result.get().get(ny)).isEqualTo(IntRangeDomain.of(3, 5));
    }

    @Test
    void propagate_negativeCoefficient_geq_tightensUpperBound() {
        // -x + y >= 3, x∈{0..5}, y fixed at {5}
        // newMax(x) = floor((3-5)/-1) = floor(2) = 2 → x pruned to {0..2}
        // newMin(x) = MIN_VALUE (GEQ → no lower constraint on negative-coeff variable)
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearConstraint.of(Map.of(nx, -1, ny, 1), Operator.GEQ, 3);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, IntRangeDomain.of(0, 5),
                ny, IntRangeDomain.of(5, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(nx)).isEqualTo(IntRangeDomain.of(0, 2));
    }

    @Test
    void propagate_negativeCoefficient_leq_tightensLowerBound() {
        // -x + y <= 0, x∈{0..5}, y fixed at {3}
        // newMin(x) = ceil((0-3)/-1) = ceil(3) = 3 → x pruned to {3..5}
        // newMax(x) = MAX_VALUE (LEQ → no upper constraint on negative-coeff variable)
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearConstraint.of(Map.of(nx, -1, ny, 1), Operator.LEQ, 0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, IntRangeDomain.of(0, 5),
                ny, IntRangeDomain.of(3, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(nx)).isEqualTo(IntRangeDomain.of(3, 5));
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var neq = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.NEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                y, IntRangeDomain.of(0, 9));
        var result = neq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_kBelowMin() {
        // 2*x + 3*y == 12, both domains {5..9}: min weighted sum = 25 > 12
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 9),
                y, IntRangeDomain.of(5, 9));
        assertThat(eq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_kAboveMax() {
        // 2*x + 3*y == 12, both domains {0..1}: max weighted sum = 2+3 = 5 < 12
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 1),
                y, IntRangeDomain.of(0, 1));
        assertThat(eq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_infeasible() {
        // 2*x + 3*y <= 12, both domains {5..9}: min weighted sum = 25 > 12
        var leq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 9),
                y, IntRangeDomain.of(5, 9));
        assertThat(leq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_infeasible() {
        // 2*x + 3*y >= 12, both domains {0..1}: max weighted sum = 5 < 12
        var geq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.GEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 1),
                y, IntRangeDomain.of(0, 1));
        assertThat(geq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_zeroCoefficient_skipsVariable() {
        // 0*x + 3*y == 12: x is unconstrained, y fixed to {4}
        Variable<Integer> z = F.create("z");
        var c = LinearConstraint.of(Map.of(x, 0, z, 3), Operator.EQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                z, IntRangeDomain.of(0, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(x);
        assertThat(result.get().get(z)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_gappedDomain_perVariableEmptyDetected() {
        // 2*x + 3*y == 7, x∈{0,4} (gapped), y∈{1}
        // Global: totalMin=3, totalMax=11, k=7 → feasible
        // Per-var: x must be exactly 2 (floor((7-3)/2)=2, ceil((7-3)/2)=2), but 2∉{0,4} → infeasible
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearConstraint.of(Map.of(nx, 2, ny, 3), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, DomainObjectSet.<Integer>builder().value(0).value(4).build(),
                ny, DomainObjectSet.<Integer>builder().value(1).build());
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // Domains already at propagation fixpoint: x∈{0..6}, y∈{0..4}
        // newMax(x) = floor(12/2) = 6, newMax(y) = floor(12/3) = 4 — no further pruning
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 6),
                y, IntRangeDomain.of(0, 4));
        var result = eq12.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate() : Double / IntervalDomain ---

    @Test
    void propagateDouble_eq_tightensBounds() {
        // 2*dx + 3*dy == 12, dy fixed at 2.0: 2*dx == 6 → dx == 3.0
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 9.0),
                dy, IntervalDomain.of(2.0, 2.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(3.0, 3.0));
        assertThat(result.get()).doesNotContainKey(dy);
    }

    @Test
    void propagateDouble_leq_tightensUpperBound() {
        // 2*dx + 3*dy <= 12, dy fixed at 2.0: 2*dx <= 6 → dx <= 3.0
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.LEQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 9.0),
                dy, IntervalDomain.of(2.0, 2.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(0.0, 3.0));
    }

    @Test
    void propagateDouble_geq_tightensLowerBound() {
        // 2*dx + 3*dy >= 12, dy fixed at 2.0: 2*dx >= 6 → dx >= 3.0
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.GEQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 9.0),
                dy, IntervalDomain.of(2.0, 2.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(3.0, 9.0));
    }

    @Test
    void propagateDouble_negativeCoefficient_eq_tightensBounds() {
        // -dx + dy == 3, dx∈[0,5], dy∈[0,5] → dx narrowed to [0,2], dy narrowed to [3,5]
        Variable<Double> dx = F.create("ndx");
        Variable<Double> dy = F.create("ndy");
        var c = LinearConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.EQ, 3.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 5.0),
                dy, IntervalDomain.of(0.0, 5.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(0.0, 2.0));
        assertThat(result.get().get(dy)).isEqualTo(IntervalDomain.of(3.0, 5.0));
    }

    @Test
    void propagateDouble_zeroCoefficient_skipsVariable() {
        // 0*dx + 3*dz == 12: dx is unconstrained, dz narrowed to 4.0
        Variable<Double> dx = F.create("dx2");
        Variable<Double> dz = F.create("dz");
        var c = LinearConstraint.of(Map.of(dx, 0.0, dz, 3.0), Operator.EQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 9.0),
                dz, IntervalDomain.of(0.0, 9.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(dx);
        assertThat(result.get().get(dz)).isEqualTo(IntervalDomain.of(4.0, 4.0));
    }

    @Test
    void propagateDouble_eq_infeasible_kBelowMin() {
        // 2*dx + 3*dy == 12, both domains [5,9]: min weighted sum = 25 > 12
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(5.0, 9.0),
                dy, IntervalDomain.of(5.0, 9.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_eq_infeasible_kAboveMax() {
        // 2*dx + 3*dy == 12, both domains [0,1]: max weighted sum = 5 < 12
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 1.0),
                dy, IntervalDomain.of(0.0, 1.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_leq_infeasible() {
        // 2*dx + 3*dy <= 12, both domains [5,9]: min weighted sum = 25 > 12
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.LEQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(5.0, 9.0),
                dy, IntervalDomain.of(5.0, 9.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_geq_infeasible() {
        // 2*dx + 3*dy >= 12, both domains [0,1]: max weighted sum = 5 < 12
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.GEQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 1.0),
                dy, IntervalDomain.of(0.0, 1.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_negativeCoefficient_geq_tightensUpperBound() {
        // -dx + dy >= 3, dx∈[0,5], dy fixed at 5.0
        // newMax(dx) = (3-5)/-1 = 2 → dx narrowed to [0,2]
        // newMin(dx) = NEGATIVE_INFINITY (GEQ → no lower constraint on negative-coeff variable)
        Variable<Double> dx = F.create("ndx");
        Variable<Double> dy = F.create("ndy");
        var c = LinearConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.GEQ, 3.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 5.0),
                dy, IntervalDomain.of(5.0, 5.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(0.0, 2.0));
    }

    @Test
    void propagateDouble_negativeCoefficient_leq_tightensLowerBound() {
        // -dx + dy <= 0, dx∈[0,5], dy fixed at 3.0
        // newMin(dx) = (0-3)/-1 = 3 → dx narrowed to [3,5]
        // newMax(dx) = POSITIVE_INFINITY (LEQ → no upper constraint on negative-coeff variable)
        Variable<Double> dx = F.create("ndx");
        Variable<Double> dy = F.create("ndy");
        var c = LinearConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.LEQ, 0.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 5.0),
                dy, IntervalDomain.of(3.0, 3.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(3.0, 5.0));
    }

    @Test
    void propagateDouble_noChange_returnsEmptyMap() {
        // Domains already at propagation fixpoint: dx∈[0,6], dy∈[0,4]
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 6.0),
                dy, IntervalDomain.of(0.0, 4.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagateDouble_mixedIntervalAndEnumerableOperands() {
        // dx is an IntervalDomain, dy is a plain enumerable Domain<Double>; dx + dy == 10
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 1.0, dy, 1.0), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 10.0),
                dy, DomainObjectSet.<Double>builder().value(2.0).value(8.0).build());
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(dx)).isEqualTo(IntervalDomain.of(2.0, 8.0));
        assertThat(result.get()).doesNotContainKey(dy);
    }

    @Test
    void propagateFloat_eq_tightensEnumerableDomain() {
        // 2.0f*fx + 3.0f*fy == 12.0f, fy fixed at 2.0f: 2*fx == 6 → fx == 3.0f
        Variable<Float> fx = F.create("fx");
        Variable<Float> fy = F.create("fy");
        var c = LinearConstraint.of(Map.of(fx, 2.0f, fy, 3.0f), Operator.EQ, 12.0f);
        var fxDomain = DomainObjectSet.<Float>builder();
        for (float v = 0f; v <= 9f; v++) fxDomain.value(v);
        var domains = Map.<Variable<?>, Domain<?>>of(
                fx, fxDomain.build(),
                fy, DomainObjectSet.<Float>builder().value(2.0f).build());
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        DiscreteDomain<Float> fxResult = (DiscreteDomain<Float>) result.get().get(fx);
        assertThat(fxResult.toList()).containsExactly(3.0f);
    }

    @Test
    void propagateDouble_enumerableOperandPrunedToEmpty_infeasible() {
        // dx enumerable {0.0, 1.0}, dy∈[9.2, 9.8]; dx + dy == 10
        // Globally feasible (totalMin=9.2, totalMax=10.8), but dx must narrow to [0.2, 0.8],
        // which excludes both 0.0 and 1.0 → infeasible per-variable.
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearConstraint.of(Map.of(dx, 1.0, dy, 1.0), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, DomainObjectSet.<Double>builder().value(0.0).value(1.0).build(),
                dy, IntervalDomain.of(9.2, 9.8));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 9),
                y, IntRangeDomain.of(0, 9));
        var result = eq12.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_allSingleton_infeasible_attributesAll() {
        // x=5, y=5 (both singleton): 2*5 + 3*5 = 35 != 12 → infeasible; both concrete values are
        // a sound, self-contained explanation.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 5),
                y, IntRangeDomain.of(5, 5));
        var result = eq12.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(x, 5, y, 5)));
    }

    @Test
    void propagateWithReasons_notAllSingleton_initialCheckInfeasible_returnsEmptyReason() {
        // 2*x + 3*y == 12, both domains {5..9}: min weighted sum = 25 > 12 → infeasible, but
        // neither is pinned, so an unlisted open-domain variable can't be ruled out.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 9),
                y, IntRangeDomain.of(5, 9));
        var result = eq12.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_onePinned_perVariablePrunedToEmpty_returnsEmptyReason() {
        // 2*x + 3*y == 7, x∈{0,4} (gapped), y∈{1} (singleton): x must equal 2, which is absent
        // from {0,4} → infeasible; x has no singleton value to blame, so even though y is
        // pinned, the explanation can't be sound without x.
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearConstraint.of(Map.of(nx, 2, ny, 3), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, DomainObjectSet.<Integer>builder().value(0).value(4).build(),
                ny, DomainObjectSet.<Integer>builder().value(1).build());
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
