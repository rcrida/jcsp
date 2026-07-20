package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class NValueConstraintTest {
    @Mock Variable<Integer> v1;
    @Mock Variable<Integer> v2;
    @Mock Variable<Integer> v3;
    @Mock Variable<Integer> v4;
    @Mock Variable<Integer> count;

    // 3 tracked vars, count == number of distinct values among them
    NValueConstraint<Integer> constraint;

    @BeforeEach
    void setUp() {
        constraint = NValueConstraint.of(Set.of(v1, v2, v3), count);
    }

    // --- isSatisfiedBy() ---

    @Test
    void countUnassigned_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1)))).isTrue();
    }

    @Test
    void fullyAssigned_exactMatch_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3, count, 3)))).isTrue();
    }

    @Test
    void fullyAssigned_mismatch_notSatisfied() {
        // v1, v2, v3 = 1, 1, 2 -> 2 distinct, but count says 3
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 1, v3, 2, count, 3)))).isFalse();
    }

    @Test
    void partialAssignment_alreadyExceedsTarget_earlyFailure() {
        // v1, v2 = 1, 2 -> 2 distinct already, but count says 1; v3 still unassigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, count, 1)))).isFalse();
    }

    @Test
    void partialAssignment_belowTarget_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, count, 2)))).isTrue();
    }

    // --- propagate() ---

    @Test
    void propagate_boundNarrowingOnly_noForcing() {
        // v1={1} definite; v2,v3={2,3} open -> lowerBound=1, upperBound=3 -> count{1,2,3,4} loses 4
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), v3, IntRangeDomain.of(2, 3),
                count, IntRangeDomain.of(1, 4));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnly(Map.entry(count, IntRangeDomain.of(1, 3)));
    }

    @Test
    void propagate_kEqualsDefiniteCount_forcesOpenVarsToDefiniteValues() {
        // v1={1} definite; v2,v3={1,2} open -> lowerBound=1, upperBound=2; count={1}==definiteCount(1)
        // -> v2, v3 forced to {1}
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(1, 2), v3, IntRangeDomain.of(1, 2),
                count, IntRangeDomain.of(1, 1));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnly(
                Map.entry(v2, IntRangeDomain.of(1, 1)), Map.entry(v3, IntRangeDomain.of(1, 1)));
    }

    @Test
    void propagate_kEqualsUpperBound_variableCountCapped_forcesOpenVarsAwayFromDefiniteValues() {
        // v1={1} definite; v2={1,2}, v3={1,3} open -> openValues={2,3}, maxNew=2, upperBound=3;
        // count={3}==upperBound, openVars.size(2)<=openValues.size(2) -> v2,v3 lose value 1
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(1, 2), v3, new IntRangeDomain(Set.of(1, 3)),
                count, IntRangeDomain.of(3, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnly(
                Map.entry(v2, IntRangeDomain.of(2, 2)), Map.entry(v3, IntRangeDomain.of(3, 3)));
    }

    @Test
    void propagate_kEqualsDefiniteCount_mixOfChangedAndUnchangedOpenVars() {
        // v1={1}, v2={2} definite -> definiteValues={1,2}; v3={1,2} open, already wholly subset
        // of definiteValues (intersecting changes nothing); v4={1,3} open (intersecting drops the
        // 3). Both open vars are guaranteed to be visited here (no early return, since neither
        // empties), unlike propagate_infeasible_forcingEmptiesOpenVariable_removeCase where an
        // early return can skip a later variable depending on Set iteration order -- this test
        // deterministically covers the "domain already subset, nothing changes" branch regardless
        // of iteration order.
        var c = NValueConstraint.of(Set.of(v1, v2, v3, v4), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(1, 2),
                v4, new IntRangeDomain(Set.of(1, 3)), count, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnly(Map.entry(v4, IntRangeDomain.of(1, 1)));
    }

    @Test
    void propagate_kEqualsUpperBound_mixOfChangedAndUnchangedOpenVars() {
        // v1={1} definite; v2={1,2} open (loses value 1, changes); v3={5,6} open (no overlap with
        // definiteValues at all, unaffected). Neither var empties, so both are guaranteed to be
        // visited regardless of Set iteration order -- deterministically covers the "unaffected"
        // branch of narrowOpenAwayFromDefinite, unlike
        // propagate_infeasible_forcingEmptiesOpenVariable_removeCase where an early return can
        // skip a later variable depending on iteration order.
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(1, 2), v3, IntRangeDomain.of(5, 6),
                count, IntRangeDomain.of(3, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnly(Map.entry(v2, IntRangeDomain.of(2, 2)));
    }

    @Test
    void propagate_upperBoundValueSupplyCapped_noForcing() {
        // v1={1} definite; v2,v3,v4={1,2} open (3 open vars, only 1 new value '2' reachable) ->
        // maxNew=min(3,1)=1, upperBound=2; count={2}==upperBound but openVars(3) > openValues(1)
        // -> forcing condition fails, nothing changes
        var c = NValueConstraint.of(Set.of(v1, v2, v3, v4), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(1, 2), v3, IntRangeDomain.of(1, 2),
                v4, IntRangeDomain.of(1, 2), count, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_kStrictlyBetweenBounds_neitherForcingConditionMet_noChange() {
        // v1={1} definite -> lowerBound=1; v2={2,3}, v3={4,5} open -> openValues={2,3,4,5},
        // maxNew=2, upperBound=3. count={2} is already singleton and strictly between lowerBound
        // and upperBound (not equal to either), so neither forcing branch's condition holds.
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), v3, IntRangeDomain.of(4, 5),
                count, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_infeasible_countDomainWiped() {
        // v1={1}, v2={2}, v3={3} all definite -> lowerBound=upperBound=3; count={1,2} both < 3
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(3, 3),
                count, IntRangeDomain.of(1, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_forcingEmptiesOpenVariable_intersectCase() {
        // v1={1} definite; v2={2,3} open, no overlap with definiteValues -> lowerBound=1,
        // upperBound=2; count={1}==definiteCount -> v2 forced to {2,3}∩{1}=∅
        var c = NValueConstraint.of(Set.of(v1, v2), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), count, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_forcingEmptiesOpenVariable_removeCase() {
        // v1={1}, v2={2} definite -> definiteValues={1,2}; v3={1,2} open (wholly subset of
        // definiteValues, contributes no new values), v4={3,4} open (contributes {3,4}) ->
        // openValues={3,4}, maxNew=2, upperBound=4; count={4}==upperBound, openVars(2)<=
        // openValues(2) -> v3 forced to {1,2}-{1,2}=∅
        var c = NValueConstraint.of(Set.of(v1, v2, v3, v4), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(1, 2),
                v4, IntRangeDomain.of(3, 4), count, IntRangeDomain.of(4, 4));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), v3, IntRangeDomain.of(2, 3),
                count, IntRangeDomain.of(1, 4));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_countWipedAllBelow_definiteVarsAndCountSingleton_attributesBoth() {
        // v1={1}, v2={2}, v3={3} definite -> lowerBound=upperBound=3; count={2}<3
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(3, 3),
                count, IntRangeDomain.of(2, 2));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, 1, v2, 2, v3, 3, count, 2)));
    }

    @Test
    void explainInfeasible_countWipedAllBelow_countNotSingleton_returnsEmptyReason() {
        // Same definite vars, but count={1,2} isn't singleton -> allSingletonReason can't cite it
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(3, 3),
                count, IntRangeDomain.of(1, 2));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_countWipedAllAbove_noOpenVars_attributesDefiniteVarsAndCount() {
        // v1={1}, v2={2}, v3={3} definite, no open vars -> upperBound=3; count={4}>3
        var c = NValueConstraint.of(Set.of(v1, v2, v3), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 2), v3, IntRangeDomain.of(3, 3),
                count, IntRangeDomain.of(4, 4));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, 1, v2, 2, v3, 3, count, 4)));
    }

    @Test
    void explainInfeasible_countWipedAllAbove_openVarsNotSingleton_returnsEmptyReason() {
        // v1={1} definite; v2={2,3} open -> upperBound=2; count={5}>2, but the above-side reason
        // must cite v2 too (open vars affect the upper bound), and v2 isn't singleton
        var c = NValueConstraint.of(Set.of(v1, v2), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), count, IntRangeDomain.of(5, 5));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_countWipedGapSpanningRange_returnsEmptyReason() {
        // v1={1} definite; v2={2,3} open -> lowerBound=1, upperBound=2; count={0,5} excluded from
        // both sides (0 < lowerBound, 5 > upperBound) -- no single-sided reason applies
        var c = NValueConstraint.of(Set.of(v1, v2), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), count, new IntRangeDomain(Set.of(0, 5)));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_forcingEmptiesOpenVariable_neverExplainable_returnsEmptyReason() {
        // Same domains as propagate_infeasible_forcingEmptiesOpenVariable_intersectCase: count
        // survives narrowing (doesn't get wiped), so the infeasibility comes from a forcing step
        // instead -- structurally unexplainable as a ground reason (see explainInfeasible's Javadoc)
        var c = NValueConstraint.of(Set.of(v1, v2), count);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(2, 3), count, IntRangeDomain.of(1, 1));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- toString() / of() ---

    @Test
    void testToString() {
        var c = NValueConstraint.of(Set.of(v1, v2), count);
        assertThat(c.toString()).isEqualTo("<(count, v1, v2), count = NValue({v1, v2})>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(NValueConstraint.of(Set.of(v1, v2, v3), count)).isEqualTo(constraint);
    }

    // --- solver integration ---

    @Test
    void solver_exactlyTwoDistinctValuesAmongThreeBinaryVariables() {
        // 3 vars over {1,2}, no other constraints, count fixed to 2 -> every assignment except
        // all-1 and all-3 satisfies (2^3 - 2 = 6 solutions)
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        Variable<Integer> n = Variable.Factory.INSTANCE.create("n");
        var domain = IntRangeDomain.of(1, 2);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .variableDomain(n, IntRangeDomain.of(2, 2))
                .nValueConstraint(Set.of(x1, x2, x3), n)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(6);
    }
}
