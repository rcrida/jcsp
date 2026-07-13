package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.EnumDomain;
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
public class GlobalCardinalityConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Color> v4;

    // 4 vars: exactly 2 RED, 1 GREEN, 1 BLUE
    GlobalCardinalityConstraint<Color> constraint;

    @BeforeEach
    void setUp() {
        constraint = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1));
    }

    @Test
    void exactCounts_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void wrongCount_notSatisfied() {
        // 3 RED, 1 GREEN — but BLUE count is 0, not 1
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED, v4, Color.GREEN)))).isFalse();
    }

    @Test
    void countExceeded_notSatisfied() {
        // 3 RED already exceeds 2 — fails even with one variable unassigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED)))).isFalse();
    }

    @Test
    void partialAssignment_belowLimit_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED)))).isTrue();
    }

    @Test
    void openGcc_underCount_notSatisfied() {
        // Only RED and GREEN are tracked (BLUE is free / unconstrained).
        // With 1 RED assigned and 2 required, no value exceeds its limit mid-assignment,
        // so early failure doesn't fire — the mismatch is only detected when all vars assigned.
        var openGcc = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 2));
        assertThat(openGcc.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.GREEN, v3, Color.BLUE, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString())
                .isEqualTo("<(v1, v2, v3, v4), GlobalCardinality({BLUE=1, GREEN=1, RED=2})>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1)))
                .isEqualTo(constraint);
    }

    // --- propagate() ---

    static final Domain<Color> RED_ONLY    = EnumDomain.of(Color.RED);
    static final Domain<Color> GREEN_ONLY  = EnumDomain.of(Color.GREEN);
    static final Domain<Color> ALL         = EnumDomain.allOf(Color.class); // RED, GREEN, BLUE
    static final Domain<Color> RED_GREEN   = EnumDomain.of(Color.RED, Color.GREEN);
    static final Domain<Color> GREEN_BLUE  = EnumDomain.of(Color.GREEN, Color.BLUE);

    @Test
    void propagate_definiteQuotaReached_removesValueFromPossibles() {
        // RED==2: v1, v2 definite RED → definiteCount==2==n → remove RED from v3's domain
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY, v3, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v3)).isEqualTo(GREEN_BLUE);
    }

    @Test
    void propagate_maxCountEqualsN_forcesPossiblesToValue() {
        // RED==2: v1 definite RED, v2 possible (RED,GREEN), v3 impossible (GREEN,BLUE)
        // definiteCount=1, maxCount=1+1=2==n → force v2 to {RED}
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_BLUE);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(RED_ONLY);
    }

    @Test
    void propagate_infeasible_tooManyDefinites() {
        // RED==1: v1, v2 both definite RED → definiteCount=2 > n=1 → infeasible
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_tooFewPossible() {
        // RED==2: v1, v2 both impossible (no RED in domain) → maxCount=0 < n=2 → infeasible
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_BLUE, v2, GREEN_BLUE);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // RED==1: v1 definite RED (quota met), v2 impossible → no possibles to update
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, GREEN_BLUE);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_neitherQuotaMet_noChange() {
        // RED==1: v1, v2 both possible (no definites) → definiteCount=0, maxCount=2 — neither equals n=1
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, ALL, v2, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_multipleValues_bothProcessed() {
        // RED==2, GREEN==1: v1, v2 definite RED (quota met) → remove RED from v3, v4;
        // GREEN possibles v3, v4 unaffected (maxCount=2 != n=1)
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3, v4), Map.of(Color.RED, 2, Color.GREEN, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY, v3, ALL, v4, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v3)).isEqualTo(GREEN_BLUE);
        assertThat(result.get().get(v4)).isEqualTo(GREEN_BLUE);
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, ALL);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_tooManyDefinites_attributesDefiniteVars() {
        // RED==1: v1, v2 both definite RED → definiteCount=2 > n=1. Every definite variable is
        // trivially singleton by construction, so both are attributed directly.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, Color.RED, v2, Color.RED)));
    }

    @Test
    void propagateWithReasons_tooFewPossible_allImpossibleSingleton_attributesThem() {
        // RED==2: v1={GREEN}, v2={BLUE} → maxCount=0 < n=2; both impossible variables are
        // singleton, so both are attributed.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_ONLY, v2, EnumDomain.of(Color.BLUE));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, Color.GREEN, v2, Color.BLUE)));
    }

    @Test
    void propagateWithReasons_tooFewPossible_notAllSingleton_returnsEmptyReason() {
        // RED==2: v1, v2 both {GREEN,BLUE} → maxCount=0 < n=2, but neither impossible variable
        // is pinned to a specific value, so no sound reason can be formed.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_BLUE, v2, GREEN_BLUE);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_maxCountEqualsNButNotDefiniteCount_narrowsThenReturnsEmpty() {
        // RED==2: v1 definite RED (definiteCount=1), v2 possible {RED,GREEN}, v3 impossible
        // {GREEN,BLUE} → maxCount=1+1=2==n, but definiteCount=1!=n=2, so this exercises the
        // "else if (maxCount == n)" narrowing branch (forcing v2 to {RED}) rather than the
        // "if (definiteCount == n)" branch exercised by the other explainInfeasible tests. No
        // infeasibility is ever found for this single tracked value, so the terminal fallback
        // returns an empty reason.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_BLUE);
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_neitherNarrowingConditionMet_fallsThroughUnchanged() {
        // RED==2: v1 definite RED (definiteCount=1), v2 and v3 both possible {RED,GREEN}
        // (maxCount=1+2=3). Neither definiteCount==n(2) nor maxCount==n(2) holds — 1!=2 and
        // 3!=2 — so this value falls through without narrowing anything, exercising the case
        // where neither of propagate()'s two narrowing conditions fires.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, RED_GREEN);
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_neitherConditionHolds_returnsEmptyReason() {
        // Direct unit test of the terminal fallback: propagate() would never actually call this
        // with domains satisfying neither infeasibility condition (definiteCount=1 is not > n=1;
        // maxCount=2 is not < n=1), but explainInfeasible's own contract must still fall back to
        // an empty reason rather than assume one of the two conditions always holds.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN);
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_multipleValues_narrowingFromEarlierValueFeedsIntoLater_attributesBoth() {
        // RED==1, GREEN==1 (LinkedHashMap fixes iteration order: RED before GREEN).
        // v1={RED} (definite RED), v2={RED,GREEN} (possible for RED), v3={GREEN} (definite GREEN).
        // Processing RED first: definiteCount(RED)=1==n → v2 is narrowed to {GREEN}, becoming
        // definite for GREEN. Only then does GREEN's definiteCount reach 2 > n=1 — the explanation
        // must replay RED's narrowing rather than judging GREEN against the original domains,
        // where v2 was still merely possible (and so wouldn't have been attributed).
        var cardinalities = new java.util.LinkedHashMap<Color, Integer>();
        cardinalities.put(Color.RED, 1);
        cardinalities.put(Color.GREEN, 1);
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), cardinalities);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_ONLY);
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(
                GroundNogoodConstraint.of(Map.of(v2, Color.GREEN, v3, Color.GREEN)));
    }

    @Test
    void solver_exactDistribution() {
        // 4 vars over {RED, GREEN, BLUE}: exactly 2 RED, 1 GREEN, 1 BLUE.
        // Solutions: C(4,2) × C(2,1) × 1 = 6 × 2 = 12.
        Variable<Color> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Color> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Color> x3 = Variable.Factory.INSTANCE.create("x3");
        Variable<Color> x4 = Variable.Factory.INSTANCE.create("x4");
        var domain = EnumDomain.allOf(Color.class);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain)
                .variableDomain(x3, domain).variableDomain(x4, domain)
                .globalCardinalityConstraint(
                        Set.of(x1, x2, x3, x4),
                        Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(12);
    }
}
