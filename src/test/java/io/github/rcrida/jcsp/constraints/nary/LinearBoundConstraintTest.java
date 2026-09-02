package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinearBoundConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x");
    Variable<Integer> y = F.create("y");

    // 2*x + 3*y == 12
    LinearBoundConstraint<Integer> eq12;

    @BeforeEach
    void setUp() {
        eq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12);
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
        var leq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
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
        assertThat(LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12)).isEqualTo(eq12);
    }

    @Test
    void weightedSum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, (byte) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 3, b, (byte) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 1, b, (byte) 1)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, (short) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 3, b, (short) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 1, b, (short) 1)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, 12L);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3L, b, 2L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1L, b, 1L)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, 12.0f);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0f, b, 2.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0f, b, 1.0f)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, 12.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0, b, 2.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0, b, 1.0)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedBoundType() {
        Variable<Number> a = F.create("a"), b = F.create("b");
        var c = LinearBoundConstraint.<Number>builder()
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
    void solver_infeasibleLinearBoundConstraint_returnsNoSolutions() {
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
        var leq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
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
        var geq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.GEQ, 12);
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
        var c = LinearBoundConstraint.of(Map.of(nx, -1, ny, 1), Operator.EQ, 3);
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
        var c = LinearBoundConstraint.of(Map.of(nx, -1, ny, 1), Operator.GEQ, 3);
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
        var c = LinearBoundConstraint.of(Map.of(nx, -1, ny, 1), Operator.LEQ, 0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, IntRangeDomain.of(0, 5),
                ny, IntRangeDomain.of(3, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(nx)).isEqualTo(IntRangeDomain.of(3, 5));
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var neq = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.NEQ, 12);
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
        var leq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 9),
                y, IntRangeDomain.of(5, 9));
        assertThat(leq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_infeasible() {
        // 2*x + 3*y >= 12, both domains {0..1}: max weighted sum = 5 < 12
        var geq12 = LinearBoundConstraint.of(Map.of(x, 2, y, 3), Operator.GEQ, 12);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 1),
                y, IntRangeDomain.of(0, 1));
        assertThat(geq12.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_zeroCoefficient_skipsVariable() {
        // 0*x + 3*y == 12: x is unconstrained, y fixed to {4}
        Variable<Integer> z = F.create("z");
        var c = LinearBoundConstraint.of(Map.of(x, 0, z, 3), Operator.EQ, 12);
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
        var c = LinearBoundConstraint.of(Map.of(nx, 2, ny, 3), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, DiscreteDomain.of(0, 4),
                ny, DiscreteDomain.of(1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // 1*a + 1*b == 3, a,b∈{0..3}: bounds consistency is already tight (newMax=3, newMin=0 for
        // both), and every value of each variable is reachable via some value of the other
        // (0↔3, 1↔2, 2↔1, 3↔0), so the subset-sum coverage pass finds nothing more to narrow either.
        Variable<Integer> a = F.create("a_nc");
        Variable<Integer> b = F.create("b_nc");
        var c = LinearBoundConstraint.of(Map.of(a, 1, b, 1), Operator.EQ, 3);
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, IntRangeDomain.of(0, 3),
                b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_eq_subsetSumCoverage_narrowsBeyondBoundsConsistency() {
        // 2*x + 3*y == 12, x∈{0..6}, y∈{0..4}: bounds consistency alone is already at a fixpoint
        // here (newMax(x)=floor(12/2)=6, newMax(y)=floor(12/3)=4), but not every numeric value in
        // that range is actually reachable -- e.g. x=1 needs 3*y=10, no integer y. Only x∈{0,3,6}
        // (paired with y=4,2,0 respectively) and y∈{0,2,4} are genuinely GAC-consistent.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 6),
                y, IntRangeDomain.of(0, 4));
        var result = eq12.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(DiscreteDomain.of(0, 3, 6));
        assertThat(result.get().get(y)).isEqualTo(DiscreteDomain.of(0, 2, 4));
    }

    @Test
    void propagate_eq_subsetSumCoverage_gappedDomain_detectsInfeasibility() {
        // x1 + x2 == 4, x1∈{0,3}, x2∈{0,5}: no real combination of live values sums to 4
        // (0+0=0, 0+5=5, 3+0=3, 3+5=8) -- here per-variable interval bounds consistency already
        // narrows x2's own [1,4] range down to nothing (0 and 5 both fall outside it), so this
        // particular case is actually caught by the existing bounds pass, not the new coverage
        // pass; see propagate_eq_subsetSumCoverage_parityInfeasible_neitherVariableSingleton below
        // for a case the bounds pass alone provably cannot catch.
        Variable<Integer> x1 = F.create("x1_ssc");
        Variable<Integer> x2 = F.create("x2_ssc");
        var c = LinearBoundConstraint.of(Map.of(x1, 1, x2, 1), Operator.EQ, 4);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of(0, 3),
                x2, DiscreteDomain.of(0, 5));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_subsetSumCoverage_parityInfeasible_neitherVariableSingleton() {
        // 2*x1 + 2*x2 == 7, x1,x2∈{0,1,3}: any sum of two even contributions is even, so 7 (odd)
        // is categorically unreachable -- but the bounds pass alone only narrows each domain to
        // {1,3} (removing 0, which falls outside the derived [1,3] interval) without emptying
        // either one, so this infeasibility is only found by the subset-sum coverage pass itself.
        Variable<Integer> x1 = F.create("x1_par");
        Variable<Integer> x2 = F.create("x2_par");
        var c = LinearBoundConstraint.of(Map.of(x1, 2, x2, 2), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of(0, 1, 3),
                x2, DiscreteDomain.of(0, 1, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_subsetSumCoverage_negativeCoefficientAndValues() {
        // x1 - x2 == 1, x1∈{-3,0,2}, x2∈{-4,1,3}: only (2,1) and (-3,-4) actually sum-differ by 1
        // (2-1=1, -3-(-4)=1); 0 has no partner (0-(-4)=4, 0-1=-1, 0-3=-3).
        Variable<Integer> x1 = F.create("x1_neg");
        Variable<Integer> x2 = F.create("x2_neg");
        var c = LinearBoundConstraint.of(Map.of(x1, 1, x2, -1), Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of(-3, 0, 2),
                x2, DiscreteDomain.of(-4, 1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x1)).isEqualTo(DiscreteDomain.of(-3, 2));
        assertThat(result.get().get(x2)).isEqualTo(DiscreteDomain.of(-4, 1));
    }

    @Test
    void propagate_eq_subsetSumCoverage_threeTerm_jointlyInfeasibleDespiteEveryVariablePassingBounds() {
        // x1 + x2 + x3 == 10, x1∈{2,4,8}, x2∈{0,7}, x3∈{0,4}: every one of the 12 combinations
        // (2+0+0, 2+0+4, ..., 8+7+4) sums to something other than 10, but each variable's own
        // interval-bounds check independently passes (e.g. x1's derived range is [-1,10], which
        // contains all of {2,4,8}) -- only the subset-sum coverage pass, reasoning about actual
        // combinations across all three variables jointly, catches this.
        //
        // Deliberately a LinkedHashMap, not Map.of(...): this specific x1/x2/x3 term ordering is
        // what forces the DP's backward-iteration branch for the middle term (see
        // SubsetSumCoveragePropagation's own coverage), and Map.of's iteration order for 3+ entries
        // is randomized per JVM launch (JEP 269's ImmutableCollections salt) -- a Map.of(...) here
        // would make that branch's coverage flaky across separate `mvn verify` runs.
        Variable<Integer> x1 = F.create("x1_3t");
        Variable<Integer> x2 = F.create("x2_3t");
        Variable<Integer> x3 = F.create("x3_3t");
        Map<Variable<Integer>, Integer> coefficients = new LinkedHashMap<>();
        coefficients.put(x1, 1);
        coefficients.put(x2, 1);
        coefficients.put(x3, 1);
        var c = LinearBoundConstraint.of(coefficients, Operator.EQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of(2, 4, 8),
                x2, DiscreteDomain.of(0, 7),
                x3, DiscreteDomain.of(0, 4));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_subsetSumCoverage_guardExceeded_fallsBackToBoundsOnly() {
        // Coefficients/domain wide enough that totalMax-totalMin+1 exceeds
        // SubsetSumCoveragePropagation.MAX_REACHABLE_RANGE -- the coverage pass must be skipped
        // (not attempted, not crash) and only the existing bounds-consistency pass applies.
        Variable<Integer> big1 = F.create("big1");
        Variable<Integer> big2 = F.create("big2");
        var c = LinearBoundConstraint.of(Map.of(big1, 1, big2, 1), Operator.EQ, 300_000);
        var domains = Map.<Variable<?>, Domain<?>>of(
                big1, IntRangeDomain.of(0, 300_000),
                big2, IntRangeDomain.of(0, 300_000));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty(); // bounds pass alone: newMax=300000 for both, already tight
    }

    // --- propagate() : Double / IntervalDomain ---

    @Test
    void propagateDouble_eq_tightensBounds() {
        // 2*dx + 3*dy == 12, dy fixed at 2.0: 2*dx == 6 → dx == 3.0
        Variable<Double> dx = F.create("dx");
        Variable<Double> dy = F.create("dy");
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.LEQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.GEQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.EQ, 3.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 0.0, dz, 3.0), Operator.EQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.LEQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.GEQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.GEQ, 3.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, -1.0, dy, 1.0), Operator.LEQ, 0.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 2.0, dy, 3.0), Operator.EQ, 12.0);
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
        var c = LinearBoundConstraint.of(Map.of(dx, 1.0, dy, 1.0), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, IntervalDomain.of(0.0, 10.0),
                dy, DiscreteDomain.of(2.0, 8.0));
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
        var c = LinearBoundConstraint.of(Map.of(fx, 2.0f, fy, 3.0f), Operator.EQ, 12.0f);
        var fxDomain = DiscreteDomain.<Float>builder();
        for (float v = 0f; v <= 9f; v++) fxDomain.value(v);
        var domains = Map.<Variable<?>, Domain<?>>of(
                fx, fxDomain.build(),
                fy, DiscreteDomain.of(2.0f));
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
        var c = LinearBoundConstraint.of(Map.of(dx, 1.0, dy, 1.0), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                dx, DiscreteDomain.of(0.0, 1.0),
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
        // x=5, y=5 (both singleton): 2*5 + 3*5 = 35 != 12 → infeasible; each singleton domain is
        // a degenerate [5,5] range, so RangeNogoodConstraint.fromCurrentBounds cites it directly.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 5),
                y, IntRangeDomain.of(5, 5));
        var result = eq12.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                x, IntervalDomain.of(5, 5), y, IntervalDomain.of(5, 5))));
    }

    @Test
    void propagateWithReasons_notAllSingleton_initialCheckInfeasible_citesCurrentBounds() {
        // 2*x + 3*y == 12, both domains {5..9}: min weighted sum = 25 > 12 → infeasible; neither
        // is pinned, but both domains are gapless ranges, so RangeNogoodConstraint.fromCurrentBounds
        // can still cite each variable's whole current domain as the (sound) reason.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(5, 9),
                y, IntRangeDomain.of(5, 9));
        var result = eq12.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                x, IntervalDomain.of(5, 9), y, IntervalDomain.of(5, 9))));
    }

    @Test
    void propagateWithReasons_onePinned_perVariablePrunedToEmpty_citesExactValueSet() {
        // 2*x + 3*y == 7, x∈{0,4} (gapped), y∈{1} (singleton): x must equal 2, which is absent
        // from {0,4} → infeasible; x has no singleton value to blame, so RangeNogoodConstraint
        // (which can't safely cite a gapped domain as a range either) falls back to citing every
        // variable's exact current value set instead of returning no explanation at all.
        Variable<Integer> nx = F.create("nx");
        Variable<Integer> ny = F.create("ny");
        var c = LinearBoundConstraint.of(Map.of(nx, 2, ny, 3), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                nx, DiscreteDomain.of(0, 4),
                ny, DiscreteDomain.of(1));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                nx, Set.of(0, 4), ny, Set.of(1))));
    }

    @Test
    void propagateWithReasons_subsetSumCoverageWipeout_neitherVariableSingleton_citesExactValueSet() {
        // Same parity-infeasible scenario as propagate_eq_subsetSumCoverage_parityInfeasible_
        // neitherVariableSingleton: the bounds pass alone can't detect it (narrows both domains to
        // {1,3}, neither emptied), so this specifically exercises the subset-sum coverage pass's
        // own infeasibility detection -- and it must do so while both original domains are still
        // non-singleton (3 live values each) and gapped (RangeNogoodConstraint can't cite either
        // as a range), forcing the fallback to ValueSetNogoodConstraint.
        Variable<Integer> x1 = F.create("x1_wc");
        Variable<Integer> x2 = F.create("x2_wc");
        var c = LinearBoundConstraint.of(Map.of(x1, 2, x2, 2), Operator.EQ, 7);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of(0, 1, 3),
                x2, DiscreteDomain.of(0, 1, 3));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                x1, Set.of(0, 1, 3), x2, Set.of(0, 1, 3))));
    }
}
