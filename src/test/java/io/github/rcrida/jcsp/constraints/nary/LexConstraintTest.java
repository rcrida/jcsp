package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LexConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x1 = F.create("x1");
    Variable<Integer> x2 = F.create("x2");
    Variable<Integer> y1 = F.create("y1");
    Variable<Integer> y2 = F.create("y2");

    LexConstraint<Integer> leq;
    LexConstraint<Integer> lt;

    @BeforeEach
    void setUp() {
        leq = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        lt  = LexConstraint.of(List.of(x1, x2), Operator.LT,  List.of(y1, y2));
    }

    @Test
    void firstDiffers_left_less_satisfied() {
        // (1, 9) lex< (2, 0): first position 1 < 2 → true regardless of rest
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 9, y1, 2, y2, 0)))).isTrue();
    }

    @Test
    void firstDiffers_left_greater_notSatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 0, y1, 1, y2, 9)))).isFalse();
    }

    @Test
    void secondDiffers_left_less_satisfied() {
        // (1, 2) lex< (1, 3): first equal, second 2 < 3
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 2, y1, 1, y2, 3)))).isTrue();
    }

    @Test
    void secondDiffers_left_greater_notSatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 3, y1, 1, y2, 2)))).isFalse();
    }

    @Test
    void allEqual_leq_satisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 2, y1, 2, y2, 2)))).isTrue();
    }

    @Test
    void allEqual_lt_notSatisfied() {
        // strict less: equal sequences do not satisfy
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 2, y1, 2, y2, 2)))).isFalse();
    }

    @Test
    void lt_firstDiffers_left_less_satisfied() {
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 5, y1, 2, y2, 0)))).isTrue();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, y1, 1)))).isTrue();
        // Even if left first position > right first, if right second is unknown we're optimistic
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 5)))).isTrue();
    }

    @Test
    void of_unequalLengths_asserts() {
        assertThatThrownBy(() -> LexConstraint.of(List.of(x1), Operator.LEQ, List.of(y1, y2)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(leq.toString()).isEqualTo("<(x1, x2, y1, y2), [x1, x2] <= [y1, y2]>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2))).isEqualTo(leq);
    }

    // --- propagate() ---

    @Test
    void propagate_firstPosition_notLast_prunesLesserOnly() {
        // [x1,x2] <= [y1,y2]: x1=[1..5], y1=[3..4] → not forced equal at position 0 (not last)
        // lesser=x1 pruned to <= max(y1)=4; greater=y1 unchanged (already >= min(x1)=1)
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(3, 4),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x1);
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(1, 4));
    }

    @Test
    void propagate_firstPosition_notLast_prunesGreaterOnly() {
        // [x1,x2] <= [y1,y2]: x1=[1..2], y1=[0..5] → not forced equal at position 0
        // greater=y1 pruned to >= min(x1)=1; lesser=x1 unchanged (already <= max(y1)=5)
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2), y1, IntRangeDomain.of(0, 5),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(y1);
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(1, 5));
    }

    @Test
    void propagate_firstPosition_strictOperator_notLast_isNonStrict() {
        // [x1,x2] < [y1,y2]: strict operator, but position 0 is not the last position,
        // so pruning is non-strict (<=) just like the LEQ case
        var c = LexConstraint.of(List.of(x1, x2), Operator.LT, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(3, 4),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(1, 4));
    }

    @Test
    void propagate_singletonsUnequal_notLast_noChange() {
        // [x1,x2] <= [y1,y2]: x1={2}, y1={3} are both singletons but unequal — not "forced equal",
        // so pruning proceeds, but both values already satisfy 2 <= 3 → no change
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(3, 3),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_lastPosition_leq_prunesBothBounds() {
        // [x1] <= [y1]: x1=[1..5], y1=[3..4] → not forced equal, last position, non-strict
        var c = LexConstraint.of(List.of(x1), Operator.LEQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(3, 4));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(1, 4));
        assertThat(result.get()).doesNotContainKey(y1);
    }

    @Test
    void propagate_lastPosition_lt_strictlyPrunesBothBounds() {
        // [x1] < [y1]: x1=[1..5], y1=[3..5] → strict at last position
        var c = LexConstraint.of(List.of(x1), Operator.LT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(3, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(1, 4));
        assertThat(result.get()).doesNotContainKey(y1);
    }

    @Test
    void propagate_infeasible_pruneEmptiesDomain() {
        // [x1] < [y1]: x1={5} (definite), y1=[1..5] → strict, x1 must be < max(y1)=5 but x1==5
        var c = LexConstraint.of(List.of(x1), Operator.LT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(5, 5), y1, IntRangeDomain.of(1, 5));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_allForcedEqual_lt_infeasible() {
        // [x1,x2] < [y1,y2]: every position is a singleton equal pair → 0 < 0 is false → infeasible
        var c = LexConstraint.of(List.of(x1, x2), Operator.LT, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(3, 3), y2, IntRangeDomain.of(3, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_allForcedEqual_leq_noChange() {
        // [x1,x2] <= [y1,y2]: every position is a singleton equal pair → 0 <= 0 is true → satisfied
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(3, 3), y2, IntRangeDomain.of(3, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_geq_swapsPairRoles() {
        // [x1] >= [y1]: x1=[3..4], y1=[1..5] → swapped: lesser=y1, greater=x1, non-strict, last position
        var c = LexConstraint.of(List.of(x1), Operator.GEQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(3, 4), y1, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(1, 4));
        assertThat(result.get()).doesNotContainKey(x1);
    }

    @Test
    void propagate_gt_swapsPairRoles_strict() {
        // [x1] > [y1]: x1=[1..4], y1=[1..5] → swapped: lesser=y1, greater=x1, strict, last position
        var c = LexConstraint.of(List.of(x1), Operator.GT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 4), y1, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(1, 3));
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(2, 4));
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var c = LexConstraint.of(List.of(x1), Operator.EQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate() with IntervalDomain ---

    static final Variable<Double> a1 = Variable.Factory.INSTANCE.create("a1");
    static final Variable<Double> b1 = Variable.Factory.INSTANCE.create("b1");

    @Test
    void propagate_interval_leq_clipsLesserMax() {
        // [a1] <= [b1]: a1=[0,10], b1=[3,8] → a1.max clips to 8; b1.min=3 already >= a1.min=0, no change
        var c = LexConstraint.of(List.of(a1), Operator.LEQ, List.of(b1));
        var result = c.propagate(Map.of(a1, IntervalDomain.of(0.0, 10.0), b1, IntervalDomain.of(3.0, 8.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(a1)).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(b1)).isFalse();
    }

    @Test
    void propagate_interval_leq_clipsGreaterMin() {
        // [a1] <= [b1]: a1=[2,7], b1=[0,8] → b1.min clips to 2; a1.max=7 <= b1.max=8, no change to a1
        var c = LexConstraint.of(List.of(a1), Operator.LEQ, List.of(b1));
        var result = c.propagate(Map.of(a1, IntervalDomain.of(2.0, 7.0), b1, IntervalDomain.of(0.0, 8.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(b1)).getMin()).isEqualTo(2.0);
        assertThat(result.containsKey(a1)).isFalse();
    }

    @Test
    void propagate_interval_noChange() {
        // [a1] <= [b1]: a1=[0,5], b1=[5,10] → a1.max=5<=b1.max=10 (no clip), b1.min=5>=a1.min=0 (no clip)
        var c = LexConstraint.of(List.of(a1), Operator.LEQ, List.of(b1));
        var result = c.propagate(Map.of(a1, IntervalDomain.of(0.0, 5.0), b1, IntervalDomain.of(5.0, 10.0)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_interval_infeasible() {
        // [a1] <= [b1]: a1=[5,10], b1=[0,3] → a1 clips to max 3, but a1.min=5 > 3 → infeasible
        var c = LexConstraint.of(List.of(a1), Operator.LEQ, List.of(b1));
        assertThat(c.propagate(Map.of(a1, IntervalDomain.of(5.0, 10.0), b1, IntervalDomain.of(0.0, 3.0)))).isEmpty();
    }

    static final Variable<Double> a2 = Variable.Factory.INSTANCE.create("a2");
    static final Variable<Double> b2 = Variable.Factory.INSTANCE.create("b2");

    @Test
    void propagate_interval_forcedEqual_skipsToNextPosition() {
        // [a1, a2] <= [b1, b2]: position 0 both singleton [4,4]==[4,4] → skip to position 1 (intervals)
        // position 1: a2=[0,10], b2=[3,8] → a2.max clips to 8
        var c = LexConstraint.of(List.of(a1, a2), Operator.LEQ, List.of(b1, b2));
        var result = c.propagate(Map.of(
                a1, IntervalDomain.of(4.0, 4.0), b1, IntervalDomain.of(4.0, 4.0),
                a2, IntervalDomain.of(0.0, 10.0), b2, IntervalDomain.of(3.0, 8.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(a2)).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(a1)).isFalse();
        assertThat(result.containsKey(b1)).isFalse();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_otherOperator_returnsEmptyReason() {
        var c = LexConstraint.of(List.of(x1), Operator.EQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(1, 5));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_bothSingletonWrongOrder_attributesBoth() {
        // [x1] <= [y1]: x1={5}, y1={3} — both singleton, non-strict, last position: 5 > 3 → infeasible
        var c = LexConstraint.of(List.of(x1), Operator.LEQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(5, 5), y1, IntRangeDomain.of(3, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x1, 5, y1, 3)));
    }

    @Test
    void explainInfeasible_lesserSingletonGreaterNotSingleton_returnsEmpty() {
        // Same domains as propagate_infeasible_pruneEmptiesDomain: x1={5} is singleton but
        // y1=[1,5] is not — citing only x1 would be unsound, since a wider y1 domain (a value > 5)
        // could still satisfy x1 < y1 in a different branch/restart.
        var c = LexConstraint.of(List.of(x1), Operator.LT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(5, 5), y1, IntRangeDomain.of(1, 5));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_notInfeasibleAtPosition_returnsEmptyReason() {
        // Same domains as propagate_firstPosition_notLast_prunesLesserOnly: not forced equal at
        // position 0, but the position isn't actually infeasible (propagate() would narrow, not
        // fail) — direct unit test of that early-return branch.
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(3, 4),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_strictOperator_notLastPosition_isNonStrict() {
        // [x1,x2] < [y1,y2]: strict operator, but position 0 (not last) is the decision point, so
        // strictHere is false there — covers the strict=true/i!=last combination of strictHere's
        // short-circuit, distinct from the single-position (i==last) strict tests above.
        // x1={5}, y1={3}, both singleton, unequal: lesserMin(5) > greaterMax(3) → infeasible.
        var c = LexConstraint.of(List.of(x1, x2), Operator.LT, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(5, 5), y1, IntRangeDomain.of(3, 3),
                x2, IntRangeDomain.of(0, 9), y2, IntRangeDomain.of(0, 9));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x1, 5, y1, 3)));
    }

    @Test
    void explainInfeasible_strictOperator_notInfeasibleAtPosition_returnsEmptyReason() {
        // [x1] < [y1]: strict, last position, but not infeasible — lesserMin(1) < greaterMax(8),
        // so the strict comparison's false outcome is exercised (distinct from the strict+infeasible
        // tests above, which only cover the true outcome of this comparison).
        var c = LexConstraint.of(List.of(x1), Operator.LT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 3), y1, IntRangeDomain.of(5, 8));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_forcedEqualThenInfeasible_skipsToNextPosition() {
        // [x1,x2] <= [y1,y2]: position 0 forced equal (x1=y1=2, both singleton) → skipped via
        // continue; position 1 (last, non-strict) has x2={5}, y2={3}, both singleton, wrong order.
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(5, 5), y2, IntRangeDomain.of(3, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x2, 5, y2, 3)));
    }

    @Test
    void explainInfeasible_allForcedEqual_lt_attributesAll() {
        // Same domains as propagate_allForcedEqual_lt_infeasible: every position forced equal,
        // so reaching the terminal 0 < 0 check requires all four variables already singleton.
        var c = LexConstraint.of(List.of(x1, x2), Operator.LT, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(3, 3), y2, IntRangeDomain.of(3, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(
                Map.of(x1, 2, y1, 2, x2, 3, y2, 3)));
    }

    @Test
    void explainInfeasible_allForcedEqual_leq_returnsEmptyReason() {
        // Same domains as propagate_allForcedEqual_leq_noChange: every position forced equal,
        // 0 <= 0 is true → feasible, terminal branch returns empty.
        var c = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(3, 3), y2, IntRangeDomain.of(3, 3));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_geqSwapsPairRoles_bothSingleton_attributesBoth() {
        // [x1] >= [y1]: swapped so lesser=y1, greater=x1. x1={2}, y1={4} → lesser=4, greater=2:
        // lesserMin(4) > greaterMax(2) → infeasible.
        var c = LexConstraint.of(List.of(x1), Operator.GEQ, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(4, 4));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x1, 2, y1, 4)));
    }

    @Test
    void explainInfeasible_gtSwapsPairRoles_strict_bothSingleton_attributesBoth() {
        // [x1] > [y1]: swapped so lesser=y1, greater=x1, strict, last position.
        // x1={2}, y1={5} (unequal, so not skipped as forced-equal) → lesser=y1(5), greater=x1(2):
        // lesserMin(5) >= greaterMax(2) → infeasible (strict).
        var c = LexConstraint.of(List.of(x1), Operator.GT, List.of(y1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(5, 5));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x1, 2, y1, 5)));
    }

    @Test
    void solver_lexLeq_solutionCount() {
        // [a,b] leq [c,d] over domain {1,2,3}: there are 9 possible (a,b) pairs and 9 (c,d) pairs.
        // Pairs where [a,b] <= [c,d]: (9*9 + 9) / 2 = 45.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        Variable<Integer> c = F.create("c"), d = F.create("d");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain).variableDomain(b, domain)
                .variableDomain(c, domain).variableDomain(d, domain)
                .lexConstraint(List.of(a, b), Operator.LEQ, List.of(c, d))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(45);
    }
}
