package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class IntersectionCardinalityConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Set<Integer>> L = F.create("l_ic");
    static final Variable<Set<Integer>> R = F.create("r_ic");

    static Map<Variable<?>, Domain<?>> sets(Set<Integer> lLower, Set<Integer> lUpper, int lMin, int lMax,
                                             Set<Integer> rLower, Set<Integer> rUpper, int rMin, int rMax) {
        return Map.of(L, SetIntervalDomain.of(lLower, lUpper, lMin, lMax),
                       R, SetIntervalDomain.of(rLower, rUpper, rMin, rMax));
    }

    @SuppressWarnings("unchecked")
    static SetIntervalDomain<Integer> left(Map<Variable<?>, Domain<?>> m) { return (SetIntervalDomain<Integer>) m.get(L); }
    @SuppressWarnings("unchecked")
    static SetIntervalDomain<Integer> right(Map<Variable<?>, Domain<?>> m) { return (SetIntervalDomain<Integer>) m.get(R); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_leq_withinBound() {
        assertThat(IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).isSatisfiedBy(Set.of(1, 2), Set.of(2, 3))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).isSatisfiedBy(Set.of(1, 2), Set.of(1, 2, 3))).isFalse();
    }

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(IntersectionCardinalityConstraint.of(L, R, Operator.EQ, 2).isSatisfiedBy(Set.of(1, 2, 3), Set.of(1, 2, 4))).isTrue();
    }

    // --- toString ---

    @Test void toString_format() {
        assertThat(IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).toString())
                .isEqualTo("<(l_ic, r_ic), |l_ic ∩ r_ic| <= 1>");
    }

    // --- propagate: non-SetBoundedDomain sides ---

    @Test void propagate_nonSetBoundedDomainSides_noOp() {
        var lDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).build();
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).build();
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    @Test void propagate_onlyRightNonSetBoundedDomain_noOp() {
        var lDomain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).build();
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: operator guard ---

    @Test void propagate_nonLeqLtOperator_noOp() {
        // definiteCommon (size 2) already exceeds what LEQ(1) would allow, but EQ propagation is
        // unimplemented, so this must no-op rather than leak LEQ-style infeasibility detection.
        var domains = sets(Set.of(1, 2), Set.of(1, 2, 3), 0, 3, Set.of(1, 2), Set.of(1, 2, 4), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.EQ, 1).propagate(domains);
        assertThat(result).contains(Map.of());
    }

    // --- propagate: LEQ ---

    @Test void propagate_leq_infeasibleWhenDefiniteCommonExceedsBound() {
        var domains = sets(Set.of(1, 2), Set.of(1, 2, 3), 0, 3, Set.of(1, 2), Set.of(1, 2, 4), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).propagate(domains);
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_slackRemaining_noOp() {
        var domains = sets(Set.of(1), Set.of(1, 2, 3), 0, 3, Set.of(4), Set.of(1, 4, 5), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 2).propagate(domains);
        assertThat(result).contains(Map.of());
    }

    @Test void propagate_leq_atCapacity_excludesCandidateForcedOnLeftFromRight() {
        var domains = sets(Set.of(1, 2), Set.of(1, 2, 5), 0, 3, Set.of(1), Set.of(1, 2, 3), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(R);
        assertThat(right(Map.of(R, result.get(R))).getUpperBound()).isEqualTo(Set.of(1, 3));
    }

    @Test void propagate_leq_atCapacity_excludesCandidateForcedOnRightFromLeft() {
        var domains = sets(Set.of(1), Set.of(1, 2, 5), 0, 3, Set.of(1, 2), Set.of(1, 2, 3), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(L);
        assertThat(left(Map.of(L, result.get(L))).getUpperBound()).isEqualTo(Set.of(1, 5));
    }

    @Test void propagate_leq_atCapacityButNoUndeterminedCandidates_noOp() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(1), Set.of(1, 3), 0, 2);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 1).propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_infeasibleWhenRightUpperBoundShrinksBelowOwnMinCardinality() {
        // bound 0 means "fully disjoint"; left forces exclusion of {1,2,3} from right's upperBound
        // (right's own lowerBound is empty, so left's own narrowing succeeds cleanly), dropping
        // right's upperBound to size 1 -- below its own minCardinality of 2.
        var domains = sets(Set.of(1, 2, 3), Set.of(1, 2, 3, 5), 0, 4, Set.of(), Set.of(1, 2, 3, 4), 2, 4);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).propagate(domains);
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_infeasibleWhenLeftUpperBoundShrinksBelowOwnMinCardinality() {
        var domains = sets(Set.of(), Set.of(1, 2, 3, 4), 2, 4, Set.of(1, 2, 3), Set.of(1, 2, 3, 5), 0, 4);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).propagate(domains);
        assertThat(result).isEmpty();
    }

    // --- propagate: LT ---

    @Test void propagate_lt_atCapacity_narrows() {
        // LT with bound 2 means maxAllowed = 1, same effective capacity as LEQ(1) above.
        var domains = sets(Set.of(1, 2), Set.of(1, 2, 5), 0, 3, Set.of(1), Set.of(1, 2, 3), 0, 3);
        var result = IntersectionCardinalityConstraint.of(L, R, Operator.LT, 2).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(R);
        assertThat(right(Map.of(R, result.get(R))).getUpperBound()).isEqualTo(Set.of(1, 3));
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_bothSingleton_returnsGroundNogood() {
        var domains = sets(Set.of(1, 2), Set.of(1, 2), 0, 2, Set.of(1, 2), Set.of(1, 2), 0, 2);
        var reason = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).explainInfeasible(domains);
        assertThat(reason).isPresent();
    }

    @Test void explainInfeasible_notBothSingleton_returnsEmpty() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(), Set.of(1, 2, 3), 0, 3);
        var reason = IntersectionCardinalityConstraint.of(L, R, Operator.LEQ, 0).explainInfeasible(domains);
        assertThat(reason).isEmpty();
    }

    // --- end-to-end: via the CSP.Builder helper and the full solver chain ---

    @Test void solvesEndToEnd_viaBuilderHelperAndFullSolverChain() {
        Variable<Set<Integer>> a = F.create("A");
        Variable<Set<Integer>> b = F.create("B");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 2, 2))
                .variableDomain(b, SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 2, 2))
                .intersectionCardinalityConstraint(a, b, Operator.LEQ, 1)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(1, 2));
        assertThat(solution.get().getValue(b)).contains(Set.of(1, 3));
    }
}
