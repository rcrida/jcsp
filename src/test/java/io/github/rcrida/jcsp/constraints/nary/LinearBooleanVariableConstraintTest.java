package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinearBooleanVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Boolean> b1 = F.create("b1");
    Variable<Boolean> b2 = F.create("b2");
    Variable<Integer> t = F.create("t");

    // 2*b1 + 3*b2 == t
    LinearBooleanVariableConstraint<Integer> eq;

    @BeforeEach
    void setUp() {
        eq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, t);
    }

    // --- isSatisfiedBy() ---

    @Test
    void weightedSumEqualsTarget_satisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, true, t, 5)))).isTrue();
    }

    @Test
    void weightedSumMismatchesTarget_notSatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, true, t, 4)))).isFalse();
    }

    @Test
    void targetUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, true)))).isTrue();
    }

    @Test
    void coefficientVariableUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(b1, true, t, 5)))).isTrue();
    }

    // --- propagate() ---

    @Test
    void propagate_narrowsVariableFromTargetAndOtherVariable() {
        // b1 open; b2 fixed true (contributes 3); target fixed at 5 -> b1 forced true (2+3=5)
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(5, 5));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b1)).isEqualTo(BooleanDomain.TRUE_ONLY);
        assertThat(result.get()).doesNotContainKey(b2);
        assertThat(result.get()).doesNotContainKey(t);
    }

    @Test
    void propagate_narrowsTargetFromVariables() {
        // b1=true, b2=true (both singleton) -> 2+3=5 -> target forced to exactly 5
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(0, 50));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(5, 5));
        assertThat(result.get()).doesNotContainKey(b1);
        assertThat(result.get()).doesNotContainKey(b2);
    }

    @Test
    void propagate_leq_narrowsTargetUpperBound() {
        var leq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.LEQ, t);
        // b1=true, b2=true (fixed, contribute 5) -> target's lower bound narrowed to 5, upper untouched
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(0, 50));
        var result = leq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(5, 50));
    }

    @Test
    void propagate_geq_narrowsTargetLowerBound() {
        var geq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.GEQ, t);
        // b1=true, b2=true (fixed, contribute 5) -> target's upper bound narrowed to 5, lower untouched
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(0, 50));
        var result = geq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(0, 5));
    }

    @Test
    void propagate_infeasible_noOverlapPossible() {
        // 2*b1+3*b2 maxes out at 5, but target starts at 100 (EQ, "0 > totalMax" side of the check)
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE, t, IntRangeDomain.of(100, 200));
        assertThat(eq.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_totalMinAboveZero() {
        // b1=true, b2=true (fixed, contribute 5), target capped at 1 -- EQ's "0 < totalMin" side
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(0, 1));
        assertThat(eq.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_infeasible() {
        var leq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.LEQ, t);
        // b1=true, b2=true (fixed, contribute 5), target capped at 1: 5 <= t is unreachable
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(0, 1));
        assertThat(leq.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_infeasible() {
        var geq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.GEQ, t);
        // 2*b1+3*b2 maxes out at 5, but target starts at 100: 5 >= t is unreachable
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE, t, IntRangeDomain.of(100, 200));
        assertThat(geq.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_perVariableInfeasibility_beyondGlobalCheck() {
        // 10*b1 + 1*b2 == t, both open, target fixed at 5: globally reachable in [-5,6] terms, but
        // neither b1=false (max 1) nor b1=true (min 10) can reach exactly 5 given b2 alone as the
        // rest -- genuinely unreachable (only 0,1,10,11 are achievable sums).
        var c = LinearBooleanVariableConstraint.of(Map.of(b1, 10, b2, 1), Operator.EQ, t);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE, t, IntRangeDomain.of(5, 5));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_targetNarrowedToEmpty_infeasible() {
        // b1=true, b2=true (fixed, contribute 5); target's domain is gapped {0, 100} -- narrowing
        // to exactly 5 (as EQ requires) excludes both values, unlike a contiguous IntRangeDomain.
        Variable<Integer> gappedTarget = F.create("gappedTarget");
        var c = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, gappedTarget);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                gappedTarget, DiscreteDomain.of(0, 100));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var neq = LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.NEQ, t);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE, t, IntRangeDomain.of(0, 10));
        var result = neq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_alreadyResolved_notReDerivedEveryRound() {
        // Everything already consistent and fixed: no further narrowing reported.
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(5, 5));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(5, 5));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_allSingleton_attributesAll() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY,
                t, IntRangeDomain.of(100, 100));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(b1, true, b2, true, t, 100)));
    }

    @Test
    void explainInfeasible_notAllSingleton_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.INSTANCE,
                t, IntRangeDomain.of(1000, 1000));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- toString() / of() ---

    @Test
    void testToString() {
        assertThat(eq.toString()).isEqualTo("<(b1, b2, t), 2*b1 + 3*b2 == t>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LinearBooleanVariableConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, t)).isEqualTo(eq);
    }

    @Test
    void solver_cspBuilder_linearBooleanConstraint_variableTargetOverload() {
        // Exercises ConstraintSatisfactionProblemBuilder#linearBooleanConstraint(Map, Operator,
        // Variable<N>) specifically, as opposed to LinearBooleanVariableConstraint.of(...) directly.
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(b1, BooleanDomain.INSTANCE)
                .variableDomain(b2, BooleanDomain.INSTANCE)
                .variableDomain(t, IntRangeDomain.of(0, 10))
                .linearBooleanConstraint(Map.of(b1, 2, b2, 3), Operator.EQ, t)
                .build();
        // All four (b1,b2) combinations produce distinct t values (0,2,3,5), all within [0,10].
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(4);
    }

    // --- numeric type dispatch (off target's assigned value) ---

    @Test
    void weightedSum_byte() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Byte> target = F.create("t");
        var c = LinearBooleanVariableConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, (byte) 5)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, (byte) 4)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Short> target = F.create("t");
        var c = LinearBooleanVariableConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, (short) 5)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, (short) 4)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Long> target = F.create("t");
        var c = LinearBooleanVariableConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 5L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 4L)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Float> target = F.create("t");
        var c = LinearBooleanVariableConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 5.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 4.0f)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Double> target = F.create("t");
        var c = LinearBooleanVariableConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 5.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, 4.0)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedTargetType() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Number> target = F.create("t");
        var c = LinearBooleanVariableConstraint.<Number>builder()
                .variables(Set.of(a, b, target))
                .coefficients(Map.<Variable<Boolean>, Number>of(a, 2, b, 3))
                .target(target)
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true, target, new AtomicInteger(5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported type");
    }
}
