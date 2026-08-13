package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AmongVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> V1 = F.create("v1_av");
    static final Variable<Integer> V2 = F.create("v2_av");
    static final Variable<Integer> V3 = F.create("v3_av");
    static final Variable<Integer> T = F.create("t_av");

    static AmongVariableConstraint<Integer> of(Operator operator) {
        return AmongVariableConstraint.of(Set.of(V1, V2, V3), Set.of(5, 6), operator, T);
    }

    static Map<Variable<?>, Domain<?>> domains(Domain<Integer> v1, Domain<Integer> v2, Domain<Integer> v3, Domain<Integer> t) {
        return Map.of(V1, v1, V2, v2, V3, v3, T, t);
    }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 6, V3, 3, T, 2)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 6, V3, 3, T, 1)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 3, V3, 4, T, 1)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 6, V3, 5, T, 1)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 6, V3, 3, T, 1)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 3, V3, 4, T, 2)))).isFalse();
    }

    @Test void isSatisfiedBy_targetUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, V2, 6, V3, 3)))).isTrue();
    }

    @Test void isSatisfiedBy_countedVariableUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(V1, 5, T, 0)))).isTrue();
    }

    // --- toString / of() ---

    @Test void testToString() {
        assertThat(of(Operator.LEQ).toString()).contains("among(5, 6) <= t_av");
    }

    @Test void of_createsEquivalentConstraint() {
        assertThat(AmongVariableConstraint.of(Set.of(V1, V2, V3), Set.of(5, 6), Operator.LEQ, T)).isEqualTo(of(Operator.LEQ));
    }

    // --- propagate: LT/GT/NEQ skipped ---

    @Test void propagate_lt_returnsEmptyMap() {
        var result = of(Operator.LT).propagate(domains(
                IntRangeDomain.of(5, 5), DiscreteDomain.of(5, 7), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 3)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_neq_returnsEmptyMap() {
        var result = of(Operator.NEQ).propagate(domains(
                IntRangeDomain.of(5, 5), DiscreteDomain.of(5, 7), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 3)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: LEQ (count <= target) ---

    @Test void propagate_leq_narrowsTargetLowerBoundAndClipsPossible() {
        // V1=5 (definite, within {5,6}), V2 in {5,7} (possible, intersects), V3=3 (impossible) ->
        // definiteCount=1, maxCount=2. target=[0,1]: lower bound raised to 1; since
        // definiteCount(1)==tHi(1), {5,6} values are removed from V2's domain.
        var result = of(Operator.LEQ).propagate(domains(
                IntRangeDomain.of(5, 5), DiscreteDomain.of(5, 7), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 1)
        )).orElseThrow();
        var t = (DiscreteDomain<Integer>) result.get(T);
        assertThat(t.toList()).containsExactly(1);
        var v2 = (DiscreteDomain<Integer>) result.get(V2);
        assertThat(v2.toList()).containsExactly(7);
    }

    @Test void propagate_leq_noChangeWhenAlreadyConsistent() {
        var result = of(Operator.LEQ).propagate(domains(
                DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), IntRangeDomain.of(0, 3)
        )).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_infeasible_definiteCountExceedsTargetMax() {
        assertThat(of(Operator.LEQ).propagate(domains(
                IntRangeDomain.of(5, 5), IntRangeDomain.of(6, 6), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 0)
        ))).isEmpty();
    }

    // --- propagate: GEQ (count >= target) ---

    @Test void propagate_geq_narrowsTargetUpperBoundAndForcesPossible() {
        var result = of(Operator.GEQ).propagate(domains(
                IntRangeDomain.of(5, 5), DiscreteDomain.of(5, 7), IntRangeDomain.of(3, 3), IntRangeDomain.of(2, 5)
        )).orElseThrow();
        var t = (DiscreteDomain<Integer>) result.get(T);
        assertThat(t.toList()).containsExactly(2);
        var v2 = (DiscreteDomain<Integer>) result.get(V2);
        assertThat(v2.toList()).containsExactly(5);
    }

    @Test void propagate_geq_noChangeWhenAlreadyConsistent() {
        var result = of(Operator.GEQ).propagate(domains(
                DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), IntRangeDomain.of(0, 3)
        )).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_geq_infeasible_maxCountBelowTargetMin() {
        assertThat(of(Operator.GEQ).propagate(domains(
                IntRangeDomain.of(1, 1), IntRangeDomain.of(2, 2), IntRangeDomain.of(3, 3), IntRangeDomain.of(3, 3)
        ))).isEmpty();
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_narrowsTargetBothDirections() {
        var result = of(Operator.EQ).propagate(domains(
                IntRangeDomain.of(5, 5), DiscreteDomain.of(5, 7), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 5)
        )).orElseThrow();
        var t = (DiscreteDomain<Integer>) result.get(T);
        assertThat(t.toList()).containsExactlyInAnyOrder(1, 2);
    }

    @Test void propagate_eq_infeasible_definiteCountExceedsTargetMax() {
        assertThat(of(Operator.EQ).propagate(domains(
                IntRangeDomain.of(5, 5), IntRangeDomain.of(6, 6), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 0)
        ))).isEmpty();
    }

    @Test void propagate_eq_infeasible_maxCountBelowTargetMin() {
        assertThat(of(Operator.EQ).propagate(domains(
                IntRangeDomain.of(1, 1), IntRangeDomain.of(2, 2), IntRangeDomain.of(3, 3), IntRangeDomain.of(3, 3)
        ))).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = of(Operator.LEQ).propagateWithReasons(domains(
                DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), IntRangeDomain.of(0, 3)
        ));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void explainInfeasible_calledDirectlyOnFeasibleDomains_returnsEmpty() {
        var result = of(Operator.EQ).explainInfeasible(domains(
                DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), DiscreteDomain.of(5, 7), IntRangeDomain.of(0, 3)
        ));
        assertThat(result).isEmpty();
    }

    @Test void explainInfeasible_leq_allSingleton_attributesDefiniteAndTarget() {
        var result = of(Operator.LEQ).propagateWithReasons(domains(
                IntRangeDomain.of(5, 5), IntRangeDomain.of(6, 6), IntRangeDomain.of(3, 3), IntRangeDomain.of(0, 0)
        ));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(V1, 5, V2, 6, T, 0)));
    }

    @Test void explainInfeasible_leq_targetNotSingleton_fallsThroughToEmpty() {
        var result = of(Operator.LEQ).propagateWithReasons(domains(
                IntRangeDomain.of(5, 5), IntRangeDomain.of(6, 6), IntRangeDomain.of(3, 3), DiscreteDomain.of(0, 1)
        ));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test void explainInfeasible_geq_allSingleton_attributesImpossibleAndTarget() {
        var result = of(Operator.GEQ).propagateWithReasons(domains(
                IntRangeDomain.of(1, 1), IntRangeDomain.of(2, 2), IntRangeDomain.of(3, 3), IntRangeDomain.of(3, 3)
        ));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(V1, 1, V2, 2, V3, 3, T, 3)));
    }

    @Test void explainInfeasible_geq_targetNotSingleton_fallsThroughToEmpty() {
        var result = of(Operator.GEQ).propagateWithReasons(domains(
                IntRangeDomain.of(1, 1), IntRangeDomain.of(2, 2), IntRangeDomain.of(3, 3), DiscreteDomain.of(4, 5)
        ));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
