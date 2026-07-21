package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SubsetConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Set<Integer>> L = F.create("l_subset");
    static final Variable<Set<Integer>> R = F.create("r_subset");

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

    @Test void isSatisfiedBy_trueWhenLeftIsSubsetOfRight() {
        assertThat(SubsetConstraint.of(L, R).isSatisfiedBy(Set.of(1, 2), Set.of(1, 2, 3))).isTrue();
    }

    @Test void isSatisfiedBy_trueWhenEqual() {
        assertThat(SubsetConstraint.of(L, R).isSatisfiedBy(Set.of(1, 2), Set.of(1, 2))).isTrue();
    }

    @Test void isSatisfiedBy_falseWhenLeftHasExtraElement() {
        assertThat(SubsetConstraint.of(L, R).isSatisfiedBy(Set.of(1, 4), Set.of(1, 2, 3))).isFalse();
    }

    // --- toString ---

    @Test void toString_format() {
        assertThat(SubsetConstraint.of(L, R).toString()).isEqualTo("<(l_subset, r_subset), l_subset subsetOf r_subset>");
    }

    // --- propagate: non-SetBoundedDomain sides ---

    @Test void propagate_nonSetBoundedDomainSides_noOp() {
        var lDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).value(Set.of(1, 2)).build();
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1, 2, 3)).build();
        var result = SubsetConstraint.of(L, R).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    @Test void propagate_onlyRightNonSetBoundedDomain_noOp() {
        var lDomain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        var rDomain = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1, 2, 3)).build();
        var result = SubsetConstraint.of(L, R).propagate(Map.of(L, lDomain, R, rDomain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: narrowing ---

    @Test void propagate_forcesRightLowerBoundFromLeft() {
        var domains = sets(Set.of(1), Set.of(1, 2, 3), 0, 3, Set.of(), Set.of(1, 2, 3), 0, 3);
        var result = SubsetConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(R);
        assertThat(right(Map.of(R, result.get(R))).getLowerBound()).isEqualTo(Set.of(1));
    }

    @Test void propagate_restrictsLeftUpperBoundFromRight() {
        var domains = sets(Set.of(), Set.of(1, 2, 3, 4), 0, 4, Set.of(), Set.of(1, 2, 3), 0, 3);
        var result = SubsetConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(L);
        var newLeft = left(Map.of(L, result.get(L)));
        assertThat(newLeft.getUpperBound()).isEqualTo(Set.of(1, 2, 3));
        assertThat(newLeft.getMaxCardinality()).isEqualTo(3);
    }

    @Test void propagate_raisesRightMinCardinalityFromLeft() {
        var domains = sets(Set.of(), Set.of(1, 2, 3), 2, 3, Set.of(), Set.of(1, 2, 3, 4, 5), 0, 5);
        var result = SubsetConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(R);
        assertThat(right(Map.of(R, result.get(R))).getMinCardinality()).isEqualTo(2);
    }

    @Test void propagate_capsLeftMaxCardinalityFromRight() {
        // right's upperBound is a superset of left's, so only the cardinality cap should change --
        // isolates cardinality narrowing from bound narrowing.
        var domains = sets(Set.of(), Set.of(1, 2), 0, 5, Set.of(), Set.of(1, 2, 3, 4, 5), 0, 2);
        var result = SubsetConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(L);
        var newLeft = left(Map.of(L, result.get(L)));
        assertThat(newLeft.getUpperBound()).isEqualTo(Set.of(1, 2));
        assertThat(newLeft.getMaxCardinality()).isEqualTo(2);
    }

    @Test void propagate_noChangeWhenAlreadyConsistent() {
        var domains = sets(Set.of(1), Set.of(1, 2), 1, 2, Set.of(1), Set.of(1, 2, 3), 1, 3);
        var result = SubsetConstraint.of(L, R).propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: infeasibility ---

    @Test void propagate_infeasibleWhenRightCannotAccommodateForcedElement() {
        // left's lowerBound forces element 1 into right, but right's own upperBound excludes it.
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(), Set.of(2, 3), 0, 2);
        var result = SubsetConstraint.of(L, R).propagate(domains);
        assertThat(result).isEmpty();
    }

    @Test void propagate_infeasibleWhenRightCardinalityCannotAccommodateLeftMinimum() {
        // left's (∅, {1,2,3}, 3, 3) construction itself triggers SetIntervalDomain's own
        // domain-intrinsic tightening (upperBound.size() == minCardinality), so left is actually
        // already the singleton {1,2,3} before propagate() ever runs -- forcing all three elements
        // into right, whose maxCardinality of 2 can't accommodate them.
        var domains = sets(Set.of(), Set.of(1, 2, 3), 3, 3, Set.of(), Set.of(1, 2, 3, 4, 5), 0, 2);
        var result = SubsetConstraint.of(L, R).propagate(domains);
        assertThat(result).isEmpty();
    }

    @Test void propagate_infeasibleWhenLeftUpperBoundShrinksBelowOwnMinCardinality() {
        // left's upperBound has 4 candidates (size > minCardinality, so no self-tightening at
        // construction, unlike the test above) and right's upperBound has 5 (large enough that
        // right's own cardinality check passes on its own), but the two upperBounds only overlap
        // in 2 elements -- below left's own minCardinality of 3. Not preempted by right's check,
        // since that only ever compares against right's own upperBound size, never against the
        // intersection with left's.
        var domains = sets(Set.of(), Set.of(1, 2, 3, 4), 3, 4, Set.of(), Set.of(1, 2, 5, 6, 7), 0, 5);
        var result = SubsetConstraint.of(L, R).propagate(domains);
        assertThat(result).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_bothSingleton_returnsGroundNogood() {
        var domains = sets(Set.of(1, 2), Set.of(1, 2), 0, 2, Set.of(3), Set.of(3), 0, 1);
        var reason = SubsetConstraint.of(L, R).explainInfeasible(domains);
        assertThat(reason).isPresent();
    }

    @Test void explainInfeasible_notBothSingleton_returnsEmpty() {
        var domains = sets(Set.of(1), Set.of(1, 2), 0, 2, Set.of(), Set.of(1, 2, 3), 0, 3);
        var reason = SubsetConstraint.of(L, R).explainInfeasible(domains);
        assertThat(reason).isEmpty();
    }

    // --- end-to-end: via the CSP.Builder helper and the full solver chain ---

    /**
     * No branching/resolution solver exists yet for set variables (only propagation), so this
     * relies entirely on {@code SubsetConstraint} propagation plus {@link SetIntervalDomain}'s own
     * cardinality tightening to fully resolve both variables to singletons: A is pinned to {1, 2}
     * from construction; propagating {@code A ⊆ B} forces {1, 2} into B's lower bound, at which
     * point B's own {@code |lowerBound| == maxCardinality} tightening collapses its upper bound to
     * match, resolving it too -- without ever needing search.
     */
    @Test void solvesEndToEnd_viaBuilderHelperAndFullSolverChain() {
        Variable<Set<Integer>> a = F.create("A");
        Variable<Set<Integer>> b = F.create("B");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 2, 2))
                .variableDomain(b, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 2, 2))
                .subsetConstraint(a, b)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(1, 2));
        assertThat(solution.get().getValue(b)).contains(Set.of(1, 2));
    }
}
