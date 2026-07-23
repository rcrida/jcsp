package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DisjointConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Set<Integer>> L = F.create("l_disjoint");
    static final Variable<Set<Integer>> R = F.create("r_disjoint");

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

    @Test void isSatisfiedBy_trueWhenDisjoint() {
        assertThat(DisjointConstraint.of(L, R).isSatisfiedBy(Set.of(1, 2), Set.of(3, 4))).isTrue();
    }

    @Test void isSatisfiedBy_trueWhenBothEmpty() {
        assertThat(DisjointConstraint.of(L, R).isSatisfiedBy(Set.of(), Set.of())).isTrue();
    }

    @Test void isSatisfiedBy_falseWhenOverlapping() {
        assertThat(DisjointConstraint.of(L, R).isSatisfiedBy(Set.of(1, 2), Set.of(2, 3))).isFalse();
    }

    // --- toString ---

    @Test void toString_format() {
        assertThat(DisjointConstraint.of(L, R).toString()).isEqualTo("<(l_disjoint, r_disjoint), l_disjoint disjoint r_disjoint>");
    }

    // --- propagate: non-SetBoundedDomain sides ---

    @Test void propagate_nonSetBoundedDomainSides_noOp() {
        var lDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).build();
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(2)).build();
        var result = DisjointConstraint.of(L, R).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    @Test void propagate_onlyRightNonSetBoundedDomain_noOp() {
        var lDomain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1, 2, 3)).build();
        var result = DisjointConstraint.of(L, R).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: narrowing ---

    @Test void propagate_excludesRightUpperBoundElementForcedInLeft() {
        var domains = sets(Set.of(1), Set.of(1, 2, 3), 0, 3, Set.of(), Set.of(1, 2, 3, 4), 0, 4);
        var result = DisjointConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(R);
        assertThat(right(Map.of(R, result.get(R))).getUpperBound()).isEqualTo(Set.of(2, 3, 4));
    }

    @Test void propagate_excludesLeftUpperBoundElementForcedInRight() {
        var domains = sets(Set.of(), Set.of(1, 2, 3, 4), 0, 4, Set.of(2), Set.of(1, 2, 3), 0, 3);
        var result = DisjointConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(L);
        assertThat(left(Map.of(L, result.get(L))).getUpperBound()).isEqualTo(Set.of(1, 3, 4));
    }

    @Test void propagate_noChangeWhenAlreadyDisjoint() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(3), Set.of(3, 4), 0, 2);
        var result = DisjointConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: infeasibility ---

    @Test void propagate_infeasibleWhenBothSidesForceSameElement() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(1), Set.of(1, 3), 0, 2);
        var result = DisjointConstraint.of(L, R).propagate(domains);
        assertThat(result).isEmpty();
    }

    @Test void propagate_infeasibleWhenRightUpperBoundShrinksBelowOwnMinCardinality() {
        // left forces exclusion of {1,2} from right's upperBound (size 3, unaffected by right's
        // own lowerBound which is empty, so left's own narrowing succeeds cleanly); removing them
        // drops right's upperBound to size 1, below right's own minCardinality of 2. Not preempted
        // by left's check, since that only depends on right's lowerBound (empty here).
        var domains = sets(Set.of(1, 2), Set.of(1, 2, 3, 4), 0, 4, Set.of(), Set.of(1, 2, 3), 2, 3);
        var result = DisjointConstraint.of(L, R).propagate(domains);
        assertThat(result).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_bothSingleton_returnsGroundNogood() {
        var domains = sets(Set.of(1), Set.of(1), 0, 1, Set.of(1), Set.of(1), 0, 1);
        var reason = DisjointConstraint.of(L, R).explainInfeasible(domains);
        assertThat(reason).isPresent();
    }

    @Test void explainInfeasible_notBothSingleton_returnsSetBoundsNogood() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(), Set.of(1, 2, 3), 0, 3);
        var reason = DisjointConstraint.of(L, R).explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(SetBoundsNogoodConstraint.class);
    }

    // --- end-to-end: via the CSP.Builder helper and the full solver chain ---

    @Test void solvesEndToEnd_viaBuilderHelperAndFullSolverChain() {
        Variable<Set<Integer>> a = F.create("A");
        Variable<Set<Integer>> b = F.create("B");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 2, 2))
                .variableDomain(b, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1))
                .disjointConstraint(a, b)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(1, 2));
        assertThat(solution.get().getValue(b)).contains(Set.of(3));
    }
}
