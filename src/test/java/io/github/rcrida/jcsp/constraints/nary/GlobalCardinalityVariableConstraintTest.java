package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlobalCardinalityVariableConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Integer> redCount;
    @Mock Variable<Integer> greenCount;

    static final Domain<Color> RED_ONLY = EnumDomain.of(Color.RED);
    static final Domain<Color> RED_GREEN = EnumDomain.of(Color.RED, Color.GREEN);
    static final Domain<Color> GREEN_BLUE = EnumDomain.of(Color.GREEN, Color.BLUE);
    static final Domain<Color> ALL = EnumDomain.allOf(Color.class);

    @Test
    void isSatisfiedBy_countsMatchTargets_isTrue() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, redCount));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, redCount, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_countsMismatchTarget_isFalse() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, redCount));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, redCount, 1)))).isFalse();
    }

    @Test
    void isSatisfiedBy_partialAssignment_optimisticallySatisfied() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, redCount));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
    }

    @Test
    void propagate_narrowsCountedVariables_viaFlowBasedGac() {
        // RED target is pinned to 2: v1, v2 already RED exhausts it -- v3 can't be RED either.
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, RED_ONLY, v2, RED_ONLY, v3, ALL, redCount, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v3)).isEqualTo(GREEN_BLUE);
    }

    @Test
    void propagate_narrowsTargetVariable_toDefinitePossibleRange() {
        // v1 is pinned RED (definite); v2, v3 could be RED or GREEN (possible). redCount's own
        // domain (0..3) narrows to [1,3] -- at least the one definite RED, at most all three.
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, RED_ONLY, v2, RED_GREEN, v3, RED_GREEN, redCount, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(redCount)).isEqualTo(IntRangeDomain.of(1, 3));
    }

    @Test
    void propagate_targetAlreadyTight_noFurtherNarrowing() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2), Map.of(Color.RED, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_GREEN, v2, RED_GREEN, redCount, IntRangeDomain.of(0, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(redCount);
    }

    @Test
    void propagate_infeasible_stepAAndStepBNarrowSelfReferentialVariableToDisjointRanges() {
        // Real state captured from MagicSequence-008-ca hitting the actual bug: pass 1 (flow GAC,
        // reasoning about x0 as a counted variable -- "what number sits at position 0") narrows x0
        // to {3}; pass 2 (definite/possible bound, reasoning about x0 as the target for value 0 --
        // "how many zeros exist") independently narrows the same variable to {4}. Both passes are
        // individually non-empty, but {3} and {4} share no value -- the intersection must be
        // detected as empty, not silently resolved by one pass overwriting the other.
        Variable<Integer> x0 = Variable.Factory.INSTANCE.create("gccv-mseq-x0");
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("gccv-mseq-x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("gccv-mseq-x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("gccv-mseq-x3");
        Variable<Integer> x4 = Variable.Factory.INSTANCE.create("gccv-mseq-x4");
        Variable<Integer> x5 = Variable.Factory.INSTANCE.create("gccv-mseq-x5");
        Variable<Integer> x6 = Variable.Factory.INSTANCE.create("gccv-mseq-x6");
        Variable<Integer> x7 = Variable.Factory.INSTANCE.create("gccv-mseq-x7");
        Set<Variable<Integer>> xs = Set.of(x0, x1, x2, x3, x4, x5, x6, x7);
        Map<Integer, Variable<Integer>> targets = Map.of(0, x0, 1, x1, 2, x2, 3, x3, 4, x4, 5, x5, 6, x6, 7, x7);
        var c = GlobalCardinalityVariableConstraint.of(xs, targets);

        var domains = Map.<Variable<?>, Domain<?>>ofEntries(
                Map.entry(x0, NumericDiscreteDomain.of(3, 4)),
                Map.entry(x1, NumericDiscreteDomain.of(0, 1, 2, 3, 4)),
                Map.entry(x2, NumericDiscreteDomain.of(0, 1, 2, 3)),
                Map.entry(x3, NumericDiscreteDomain.of(0, 1, 2)),
                Map.entry(x4, NumericDiscreteDomain.of(0)),
                Map.entry(x5, NumericDiscreteDomain.of(0)),
                Map.entry(x6, NumericDiscreteDomain.of(0)),
                Map.entry(x7, NumericDiscreteDomain.of(0)));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_narrowedRangeFallsInTargetsGap() {
        // v1 is definite for value 5 (pinned {5}); v2 is possible ({5,6}). definiteCount=1,
        // maxCount=2, so the achievable-range argument narrows the target to [1,2] -- a valid,
        // non-inverted range. But the target's own current domain is {0,3} (gappy, e.g. already
        // pruned by an earlier round), which contains neither 1 nor 2: the range being
        // non-inverted does NOT guarantee it contains an actual present value. This is the real
        // bug MagicSequence-008-ca found before propagate() added the explicit emptiness check.
        Variable<Integer> v1 = org.mockito.Mockito.mock(Variable.class);
        Variable<Integer> v2 = org.mockito.Mockito.mock(Variable.class);
        var five = IntRangeDomain.of(5, 5);
        var fiveOrSix = IntRangeDomain.of(5, 6);
        var gappyTarget = NumericDiscreteDomain.of(0, 3);
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2), Map.of(5, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, five, v2, fiveOrSix, redCount, gappyTarget);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_targetUnreachable() {
        // redCount pinned to 2, but neither variable can even take RED.
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2), Map.of(Color.RED, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_BLUE, v2, GREEN_BLUE, redCount, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_pigeonholeJointOverSubscription_notCaughtByIndependentCounts() {
        // v1, v2, v3 all restricted to {RED, GREEN} only (no escape value); redCount and
        // greenCount both pinned to 1. 3 variables can only ever be split across 2 total target
        // slots -- infeasible by pigeonhole, even though neither target's own count is
        // individually violated: exactly the ADR-0016 motivating example, generalised to variable
        // targets. A decomposition into two independent CountVariableConstraints (one per value)
        // would NOT catch this -- each in isolation is satisfiable (e.g. 2 RED + 1 GREEN, or 1 RED
        // + 2 GREEN both satisfy count(RED)<=some value and count(GREEN)<=some value individually
        // when checked alone) only the joint flow network sees all three variables competing for
        // the same two slots at once.
        var c = GlobalCardinalityVariableConstraint.of(
                Set.of(v1, v2, v3), Map.of(Color.RED, redCount, Color.GREEN, greenCount));
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, RED_GREEN, v2, RED_GREEN, v3, RED_GREEN,
                redCount, IntRangeDomain.of(1, 1), greenCount, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();

        // Confirm the claim: each CountVariableConstraint independently, over these same domains,
        // reports no infeasibility -- the joint reasoning is what this class adds.
        var redOnly = CountVariableConstraint.of(Set.of(v1, v2, v3), Color.RED, io.github.rcrida.jcsp.constraints.Operator.EQ, redCount);
        var greenOnly = CountVariableConstraint.of(Set.of(v1, v2, v3), Color.GREEN, io.github.rcrida.jcsp.constraints.Operator.EQ, greenCount);
        assertThat(redOnly.propagate(domains)).isPresent();
        assertThat(greenOnly.propagate(domains)).isPresent();
    }

    @Test
    void explainInfeasible_pigeonholeOverEnumValues_noSoundCitationAvailable() {
        // Same pigeonhole scenario as propagate()'s own infeasibility test above -- mirrors
        // GlobalCardinalityConstraintTest#explainInfeasible_hallViolation_nonSingletonEnumViolator_noSoundCitationAvailable:
        // v1/v2/v3 aren't singleton and Color isn't numeric, so neither the ground nor the range
        // fallback has anything sound to cite. Pre-existing GCC limitation, not new here.
        var c = GlobalCardinalityVariableConstraint.of(
                Set.of(v1, v2, v3), Map.of(Color.RED, redCount, Color.GREEN, greenCount));
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, RED_GREEN, v2, RED_GREEN, v3, RED_GREEN,
                redCount, IntRangeDomain.of(1, 1), greenCount, IntRangeDomain.of(1, 1));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_pigeonholeOverNumericValues_citesViolatingSubsetAndAllTargets() {
        // Same pigeonhole shape as above, but over numeric (Integer) tracked values so
        // RangeNogoodConstraint.fromCurrentBounds has a sound citation to fall back to.
        Variable<Integer> n1 = org.mockito.Mockito.mock(Variable.class);
        Variable<Integer> n2 = org.mockito.Mockito.mock(Variable.class);
        Variable<Integer> n3 = org.mockito.Mockito.mock(Variable.class);
        var oneOrTwo = IntRangeDomain.of(1, 2);
        var c = GlobalCardinalityVariableConstraint.of(
                Set.of(n1, n2, n3), Map.of(1, redCount, 2, greenCount));
        var domains = Map.<Variable<?>, Domain<?>>of(
                n1, oneOrTwo, n2, oneOrTwo, n3, oneOrTwo,
                redCount, IntRangeDomain.of(1, 1), greenCount, IntRangeDomain.of(1, 1));
        assertThat(c.explainInfeasible(domains)).isPresent();
    }

    @Test
    void explainInfeasible_allCitedVariablesSingleton_returnsGroundReason() {
        // Both counted variables are pinned to the same tracked value (5), and the target itself
        // is pinned to 1 -- 2 variables forced through a capacity-1 edge, infeasible. Every cited
        // variable (the violating subset plus the target) is singleton, so allSingletonReason
        // succeeds and the ground-reason branch is taken directly, unlike the range-fallback
        // branch the pigeonhole tests above exercise.
        Variable<Integer> x1 = org.mockito.Mockito.mock(Variable.class);
        Variable<Integer> x2 = org.mockito.Mockito.mock(Variable.class);
        var five = IntRangeDomain.of(5, 5);
        var c = GlobalCardinalityVariableConstraint.of(Set.of(x1, x2), Map.of(5, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, five, x2, five, redCount, IntRangeDomain.of(1, 1));
        assertThat(c.explainInfeasible(domains)).isPresent();
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2), Map.of(Color.RED, redCount));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, ALL, v2, ALL, redCount, IntRangeDomain.of(0, 2));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void getRelation_containsTargetNames() {
        var c = GlobalCardinalityVariableConstraint.of(Set.of(v1, v2), Map.of(Color.RED, redCount));
        assertThat(c.getRelation()).contains("RED");
    }

    @Test
    void propagate_variableIsBothCountedAndItsOwnTarget_intersectsBothPasses() {
        // Tracked value 1, target = x1 itself. x2, x3 are both pinned to 1 (definite), consuming
        // exactly target's current hi=2 -- pass 1 (flow GAC) removes 1 from x1's own domain
        // ({0,1,2} -> {0,2}, no remaining capacity for a third variable at value 1). Pass 2
        // (definite/possible bound) separately narrows x1-as-target to [2,2] (definiteCount=2 from
        // x2/x3, maxCount=3 since x1 itself is "possible"). Neither subsumes the other -- {0,2} and
        // {2} must be intersected down to {2}, not one overwriting the other.
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("gccv-x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("gccv-x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("gccv-x3");
        var c = GlobalCardinalityVariableConstraint.of(Set.of(x1, x2, x3), Map.of(1, x1));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(0, 2), x2, IntRangeDomain.of(1, 1), x3, IntRangeDomain.of(1, 1));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(2, 2));
    }

    @Test
    void selfReferential_magicSequence_solvesAndVariablesActAsBothCountedAndTarget() {
        // Length-4 magic sequence: x[i] == the number of occurrences of i in x itself. occurs and
        // countedVariables are literally the same variables -- the case propagate()'s two passes
        // (flow-based GAC on countedVariables, definite/possible narrowing on targets) must merge
        // via intersection for, not overwrite.
        Variable<Integer> x0 = Variable.Factory.INSTANCE.create("x0");
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        Set<Variable<Integer>> xs = Set.of(x0, x1, x2, x3);
        Map<Integer, Variable<Integer>> targets = Map.of(0, x0, 1, x1, 2, x2, 3, x3);

        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x0, IntRangeDomain.of(0, 4))
                .variableDomain(x1, IntRangeDomain.of(0, 4))
                .variableDomain(x2, IntRangeDomain.of(0, 4))
                .variableDomain(x3, IntRangeDomain.of(0, 4))
                .globalCardinalityVariableConstraint(xs, targets)
                .build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        var a = solution.get();
        for (int value = 0; value < 4; value++) {
            int finalValue = value;
            long count = xs.stream().filter(v -> a.getValue(v).orElseThrow() == finalValue).count();
            assertThat(a.getValue(targets.get(value)).orElseThrow()).as("value=%d", value).isEqualTo((int) count);
        }
    }
}
