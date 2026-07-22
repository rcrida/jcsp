package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryOffsetConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static final Variable<Number> LEFT = VARIABLE_FACTORY.create("left");
    static final Variable<Number> RIGHT = VARIABLE_FACTORY.create("right");

    static Stream<Arguments> isSatisfiedBy() {
        return Stream.of(
                Arguments.of(5, Operator.EQ, null, null, true),
                Arguments.of(5, Operator.EQ, null, 19, true),
                Arguments.of(5, Operator.EQ, 0, null, true),
                Arguments.of(5, Operator.EQ, 0, 5, true),
                Arguments.of(5, Operator.EQ, 0, 6, false),
                Arguments.of((byte) 5, Operator.EQ, (byte) 0, (byte) 5, true),
                Arguments.of((short) 5, Operator.EQ, (short) 0, (short) 5, true),
                Arguments.of(5L, Operator.EQ, 0L, 5L, true),
                Arguments.of(5.0f, Operator.EQ, 0.0f, 5.0f, true),
                Arguments.of(5.0, Operator.EQ, 0.0, 5.0, true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy(Number offset, Operator operator, Object left, Object right, boolean expected) {
        val constraint = BinaryOffsetConstraint.of(LEFT, offset, operator, RIGHT);
        val assignmentBuilder = Assignment.builder();
        if (left != null) {
            assignmentBuilder.value(LEFT, left);
        }
        if (right != null) {
            assignmentBuilder.value(RIGHT, right);
        }
        assertThat(constraint.isSatisfiedBy(assignmentBuilder.build())).isEqualTo(expected);
    }

    @Test
    void isSatisfiedBy_unsupportedNumberSubtype() {
        val constraint = BinaryOffsetConstraint.<Number>builder().left(LEFT).right(RIGHT).offset(5).operator(Operator.EQ).build();
        Number unknown = new Number() {
            @Override public int intValue() { return 0; }
            @Override public long longValue() { return 0; }
            @Override public float floatValue() { return 0; }
            @Override public double doubleValue() { return 0; }
        };
        assertThatThrownBy(() -> constraint.isSatisfiedBy(unknown, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported value type: " + unknown.getClass());
    }

    static Stream<Arguments> testToString() {
        return Stream.of(
                Arguments.of((byte) 5, "<(left, right), left + 5 == right>"),
                Arguments.of((byte) -5, "<(left, right), left - 5 == right>"),
                Arguments.of((short) 5, "<(left, right), left + 5 == right>"),
                Arguments.of((short) -5, "<(left, right), left - 5 == right>"),
                Arguments.of(5, "<(left, right), left + 5 == right>"),
                Arguments.of(-5, "<(left, right), left - 5 == right>"),
                Arguments.of(5L, "<(left, right), left + 5 == right>"),
                Arguments.of(-5L, "<(left, right), left - 5 == right>"),
                Arguments.of(5.0f, "<(left, right), left + 5.0 == right>"),
                Arguments.of(-5.0f, "<(left, right), left - 5.0 == right>"),
                Arguments.of(5.0, "<(left, right), left + 5.0 == right>"),
                Arguments.of(-5.0, "<(left, right), left - 5.0 == right>")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testToString(Number offset, String expected) {
        assertThat(BinaryOffsetConstraint.of(LEFT, offset, Operator.EQ, RIGHT)).asString().isEqualTo(expected);
    }

    @Test
    void toString_unsupportedValue() {
        val constraint = BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(new AtomicInteger(0)).operator(Operator.EQ).build();
        assertThatThrownBy(() -> constraint.toString())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported offset type: class java.util.concurrent.atomic.AtomicInteger");
    }

    @Test
    void negatedOffset_unsupportedValue() {
        val constraint = BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(new AtomicInteger(0)).operator(Operator.EQ).build();
        assertThatThrownBy(() -> constraint.negatedOffset())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported offset type: class java.util.concurrent.atomic.AtomicInteger");
    }

    @Test
    void of_createsEquivalentConstraint() {
        val expected = BinaryOffsetConstraint.<Number>builder().left(LEFT).right(RIGHT).offset(5).operator(Operator.EQ).build();
        assertThat(BinaryOffsetConstraint.of(LEFT, (Number) 5, Operator.EQ, RIGHT)).isEqualTo(expected);
    }

    // propagate() tests for IntervalDomain — offset=3.0, L+3 op R
    static final Variable<Double> L = Variable.Factory.INSTANCE.create("l_off");
    static final Variable<Double> R = Variable.Factory.INSTANCE.create("r_off");
    static final double O = 3.0;

    static Map<Variable<?>, Domain<?>> domains(double lLo, double lHi, double rLo, double rHi) {
        return Map.of(L, IntervalDomain.of(lLo, lHi), R, IntervalDomain.of(rLo, rHi));
    }
    static IntervalDomain left(Map<Variable<?>, Domain<?>> m)  { return (IntervalDomain) m.get(L); }
    static IntervalDomain right(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(R); }

    @Test void propagate_neq_noChange() {
        var result = BinaryOffsetConstraint.of(L, O, Operator.NEQ, R).propagate(domains(0, 10, 0, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_leq_clipsLeftMaxOnly() {
        // L=[0,10], R=[3,8], L+3<=R: L.max=min(10,8-3)=5; R.min=max(3,0+3)=3 unchanged
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagate(domains(0, 10, 3, 8)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(5.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_leq_clipsRightMinOnly() {
        // L=[0,5], R=[0,10], L+3<=R: L.max=min(5,10-3)=5 unchanged; R.min=max(0,0+3)=3
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagate(domains(0, 5, 0, 10)).orElseThrow();
        assertThat(right(result).getMin()).isEqualTo(3.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_lt_sameAsLeq() {
        var result = BinaryOffsetConstraint.of(L, O, Operator.LT, R).propagate(domains(0, 10, 3, 8)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_geq_clipsLeftMinOnly() {
        // L=[0,10], R=[5,8], L+3>=R: L.min=max(0,5-3)=2; R.max=min(8,10+3)=8 unchanged
        var result = BinaryOffsetConstraint.of(L, O, Operator.GEQ, R).propagate(domains(0, 10, 5, 8)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(2.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_geq_clipsRightMaxOnly() {
        // L=[5,10], R=[0,20], L+3>=R: L.min=max(5,0-3)=5 unchanged; R.max=min(20,10+3)=13
        var result = BinaryOffsetConstraint.of(L, O, Operator.GEQ, R).propagate(domains(5, 10, 0, 20)).orElseThrow();
        assertThat(right(result).getMax()).isEqualTo(13.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_gt_sameAsGeq() {
        var result = BinaryOffsetConstraint.of(L, O, Operator.GT, R).propagate(domains(0, 10, 5, 8)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(2.0);
    }

    @Test void propagate_eq_clipsLeftMinAndRightMax() {
        // L=[0,10], R=[5,15], L+3==R: L.min=max(0,5-3)=2; L.max=min(10,15-3)=10 unchanged
        //                              R.min=max(5,0+3)=5 unchanged; R.max=min(15,10+3)=13
        var result = BinaryOffsetConstraint.of(L, O, Operator.EQ, R).propagate(domains(0, 10, 5, 15)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(2.0);
        assertThat(right(result).getMax()).isEqualTo(13.0);
    }

    @Test void propagate_infeasible() {
        // L=[5,10], R=[0,3], L+3<=R: L.max=min(10,3-3)=0 < L.min=5 → infeasible
        assertThat(BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagate(domains(5, 10, 0, 3))).isEmpty();
    }

    @Test void propagate_noChange() {
        // L=[0,5], R=[5,10], L+3<=R: L.max=min(5,10-3)=5 unchanged; R.min=max(5,0+3)=5 unchanged
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagate(domains(0, 5, 5, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowsRightMin() {
        // il=[0,5], ir=[0,10], il+3<=ir: il.max=min(5,10-3)=7->5 unchanged; ir.min=max(0,0+3)=3 (prunes 0,1,2)
        Variable<Integer> il = Variable.Factory.INSTANCE.create("il_off"), ir = Variable.Factory.INSTANCE.create("ir_off");
        var result = BinaryOffsetConstraint.of(il, 3, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(0, 5), ir, IntRangeDomain.of(0, 10))).orElseThrow();
        assertThat(result.containsKey(il)).isFalse();
        assertThat(((DiscreteDomain<Integer>) result.get(ir)).toList()).containsExactly(3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test void propagate_bothDiscrete_infeasible() {
        // il=[5,10], ir=[0,3], il+3<=ir: il.max=min(10,3-3)=0 < il.min=5 -> infeasible
        Variable<Integer> il = Variable.Factory.INSTANCE.create("il_off2"), ir = Variable.Factory.INSTANCE.create("ir_off2");
        var result = BinaryOffsetConstraint.of(il, 3, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(5, 10), ir, IntRangeDomain.of(0, 3)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedDomain_infeasible() {
        // l={0,10} (gap domain), r={4,5}, l+0==r: l narrows to [max(0,4),min(10,5)]=[4,5], a
        // non-empty numeric range, but neither 0 nor 10 lies in it -> narrowing empties l even
        // though the fast newLMin<=newLMax check alone would not have caught this
        Variable<Integer> l = Variable.Factory.INSTANCE.create("l_off_gap"), r = Variable.Factory.INSTANCE.create("r_off_gap");
        var lDomain = NumericDiscreteDomain.of(0, 10);
        var rDomain = NumericDiscreteDomain.of(4, 5);
        var result = BinaryOffsetConstraint.of(l, 0, Operator.EQ, r).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedRightDomain_infeasible() {
        // l={4,5}, r={0,10} (gap domain), l+0==r: l is unchanged (already within [4,5]), but r
        // narrows to [4,5] and neither 0 nor 10 lies in it -> narrowing empties r this time
        Variable<Integer> l = Variable.Factory.INSTANCE.create("l_off_gap2"), r = Variable.Factory.INSTANCE.create("r_off_gap2");
        var lDomain = NumericDiscreteDomain.of(4, 5);
        var rDomain = NumericDiscreteDomain.of(0, 10);
        var result = BinaryOffsetConstraint.of(l, 0, Operator.EQ, r).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_leftBounded_rightDiscrete_clipsLeft() {
        // L=Interval[0,10], R={8.0}, L+3<=R: L.max=min(10,8-3)=5
        var discrete = DomainObjectSet.<Double>builder().value(8.0).build();
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R)
                .propagate(Map.of(L, IntervalDomain.of(0.0, 10.0), R, discrete)).orElseThrow();
        assertThat(((IntervalDomain) result.get(L)).getMax()).isEqualTo(5.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_leftDiscrete_rightBounded_clipsRight() {
        // L={2.0,5.0}, R=Interval[0,10], L+3>=R: R.max=min(10,5+3)=8
        var discrete = DomainObjectSet.<Double>builder().value(2.0).value(5.0).build();
        var result = BinaryOffsetConstraint.of(L, O, Operator.GEQ, R)
                .propagate(Map.of(L, discrete, R, IntervalDomain.of(0.0, 10.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(R)).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    // propagateWithReasons() tests

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagateWithReasons(domains(0, 10, 3, 8));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void propagateWithReasons_infeasible_leftSingleton_attributesLeft() {
        // L=[5,5] (singleton), R=[0,3], L+3<=R is infeasible. Only L can be blamed on a specific
        // value; R is a genuine open range with nothing to pin the conflict on.
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagateWithReasons(domains(5, 5, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(L, 5.0)));
    }

    @Test void propagateWithReasons_infeasible_bothSingleton_attributesBoth() {
        // L=[5,5], R=[1,1]: L+3<=R is infeasible with both sides pinned to a concrete value.
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagateWithReasons(domains(5, 5, 1, 1));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(L, 5.0, R, 1.0)));
    }

    @Test void propagateWithReasons_infeasible_neitherSingleton_returnsEmptyReason() {
        // L=[5,10], R=[0,3]: infeasible, but neither side is pinned to a single value, so no
        // variable-value pair can be blamed — matches propagate_infeasible() above.
        var result = BinaryOffsetConstraint.of(L, O, Operator.LEQ, R).propagateWithReasons(domains(5, 10, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
