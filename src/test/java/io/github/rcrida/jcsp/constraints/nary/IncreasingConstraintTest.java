package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class IncreasingConstraintTest {
    @Mock Variable<Integer> v1;
    @Mock Variable<Integer> v2;
    @Mock Variable<Integer> v3;
    @Mock Variable<Integer> v4;

    IncreasingConstraint<Integer> constraint;

    @BeforeEach
    void setUp() {
        constraint = IncreasingConstraint.of(List.of(v1, v2, v3, v4));
    }

    @Test
    void nonDecreasing_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 2, v4, 5)))).isTrue();
    }

    @Test
    void strictlyIncreasing_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3, v4, 4)))).isTrue();
    }

    @Test
    void allEqual_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 3, v3, 3, v4, 3)))).isTrue();
    }

    @Test
    void decreasing_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 4, v2, 3, v3, 2, v4, 1)))).isFalse();
    }

    @Test
    void singleViolation_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 3, v3, 2, v4, 4)))).isFalse();
    }

    @Test
    void partialAssignment_satisfiedOptimistically() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 5)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v3, 3)))).isTrue();
    }

    @Test
    void partialAssignment_assignedPairViolated_notSatisfied() {
        // v1 and v2 are both assigned and v1 > v2 — caught even though v3 and v4 are unknown
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 2)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(v1, v2, v3, v4), increasing>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(IncreasingConstraint.of(List.of(v1, v2, v3, v4))).isEqualTo(constraint);
    }

    @Test
    void getAsBinaryConstraints_returnsLeqPairs() {
        assertThat(constraint.getAsBinaryConstraints()).hasSize(3); // one per consecutive pair
    }

    @Test
    void solver_nonDecreasingSequences() {
        // Count non-decreasing (v1 <= v2 <= v3) sequences over domain {1, 2, 3}.
        // Equivalent to combinations with repetition: C(3+3-1, 3) = C(5,3) = 10.
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .increasingConstraint(List.of(x1, x2, x3))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(10);
    }

    // propagate()/explainInfeasible() tests

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // Already bounds-consistent: v1=[0,3], v2=[3,6], v3=[6,9], v4=[9,12] — nothing to narrow.
        var result = constraint.propagate(Map.of(
                v1, IntRangeDomain.of(0, 3), v2, IntRangeDomain.of(3, 6),
                v3, IntRangeDomain.of(6, 9), v4, IntRangeDomain.of(9, 12)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_discrete_narrowsNonAdjacentRunningBounds() {
        // v1=[0,10], v2=[5,10], v3=[0,5], v4=[0,10]: v2's min (5) raises the running floor past
        // v3, and v3's max (5) lowers the running ceiling past v2 — narrowing all four positions.
        var result = constraint.propagate(Map.of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(5, 10),
                v3, IntRangeDomain.of(0, 5), v4, IntRangeDomain.of(0, 10))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(v1)).toList()).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
        assertThat(((DiscreteDomain<Integer>) result.get(v2)).toList()).containsExactly(5);
        assertThat(((DiscreteDomain<Integer>) result.get(v3)).toList()).containsExactly(5);
        assertThat(((DiscreteDomain<Integer>) result.get(v4)).toList()).containsExactlyInAnyOrder(5, 6, 7, 8, 9, 10);
    }

    @Test
    void propagate_discrete_narrowingEmptiesGappedMiddleDomain_infeasible() {
        // v1={4} forces the running floor to 4 by the time it reaches v2; v3={5} forces the
        // running ceiling to 5 back through v2 — v2's gap domain {0,10} has neither value in
        // [4,5], so narrowing empties it even though the coarse newMin<=newMax check alone
        // (4<=5) would not have caught this.
        var v1Domain = new IntRangeDomain(Set.of(4));
        var v2Domain = new IntRangeDomain(Set.of(0, 10));
        var v3Domain = new IntRangeDomain(Set.of(5));
        var result = constraint.propagate(Map.of(v1, v1Domain, v2, v2Domain, v3, v3Domain, v4, IntRangeDomain.of(0, 10)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_bounded_narrowsOneSide_andLeavesOtherUnchanged() {
        // L=[0,10], R=[3,8], L<=R: L narrows to [0,8] (real IntervalDomain#withBounds narrowing);
        // R is already within [3,8] so narrow() returns Optional.empty() for it (no update).
        Variable<Double> l = Variable.Factory.INSTANCE.create("l");
        Variable<Double> r = Variable.Factory.INSTANCE.create("r");
        var chain = IncreasingConstraint.of(List.of(l, r));
        var result = chain.propagate(Map.of(l, IntervalDomain.of(0.0, 10.0), r, IntervalDomain.of(3.0, 8.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(l)).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(r)).isFalse();
    }

    @Test
    void propagate_bounded_narrowsLowerBound() {
        // L=[2,3], R=[0,10], L<=R: R's min is raised to 2 (L's min); R's max (10) is already the
        // widest allowed since nothing later in the chain constrains it further — exercises the
        // branch where a BoundedDomain's *lower* bound (not just upper) is what gets clipped.
        Variable<Double> l = Variable.Factory.INSTANCE.create("l2");
        Variable<Double> r = Variable.Factory.INSTANCE.create("r2");
        var chain = IncreasingConstraint.of(List.of(l, r));
        var result = chain.propagate(Map.of(l, IntervalDomain.of(2.0, 3.0), r, IntervalDomain.of(0.0, 10.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(r)).getMin()).isEqualTo(2.0);
        assertThat(((IntervalDomain) result.get(r)).getMax()).isEqualTo(10.0);
        assertThat(result.containsKey(l)).isFalse();
    }

    @Test
    void propagate_nonNumericComparableChain_stillPropagates() {
        // String chain: propagation is Comparable-based, not Number-restricted, so this narrows
        // just like a numeric chain would — "m" is deleted from v1 since it exceeds v2/v3's max.
        Variable<String> s1 = Variable.Factory.INSTANCE.create("s1");
        Variable<String> s2 = Variable.Factory.INSTANCE.create("s2");
        Variable<String> s3 = Variable.Factory.INSTANCE.create("s3");
        var chain = IncreasingConstraint.of(List.of(s1, s2, s3));
        Domain<String> d1 = DomainObjectSet.<String>builder().value("a").value("b").value("m").build();
        Domain<String> d2 = DomainObjectSet.<String>builder().value("c").value("d").build();
        Domain<String> d3 = DomainObjectSet.<String>builder().value("e").value("z").build();
        var result = chain.propagate(Map.of(s1, d1, s2, d2, s3, d3)).orElseThrow();
        assertThat(((DomainObjectSet<String>) result.get(s1)).values()).containsExactlyInAnyOrder("a", "b");
        assertThat(result.containsKey(s2)).isFalse();
        assertThat(result.containsKey(s3)).isFalse();
    }

    @Test
    void explainInfeasible_citesNonAdjacentSingletonPair() {
        // v1={10} (singleton), v2/v3 wide open, v4={3} (singleton): the running floor (10, from
        // v1) exceeds the running ceiling (3, from v4) by the time both reach position 0 — the
        // violation is attributed to v1 and v4 directly, skipping v2/v3 entirely.
        var result = constraint.explainInfeasible(Map.of(
                v1, new IntRangeDomain(Set.of(10)), v2, IntRangeDomain.of(0, 20),
                v3, IntRangeDomain.of(0, 20), v4, new IntRangeDomain(Set.of(3))));
        assertThat(result).isEqualTo(Optional.of(GroundNogoodConstraint.of(Map.of(v1, 10, v4, 3))));
    }

    @Test
    void explainInfeasible_notBothSingleton_returnsEmpty() {
        // Same shape as above but v4 is a genuine open range: neither variable-value pair is
        // individually sufficient, so no sound explanation is produced (caller falls back further).
        var result = constraint.explainInfeasible(Map.of(
                v1, new IntRangeDomain(Set.of(10)), v2, IntRangeDomain.of(0, 20),
                v3, IntRangeDomain.of(0, 20), v4, IntRangeDomain.of(0, 3)));
        assertThat(result).isEmpty();
    }

    @Test
    void explainInfeasible_feasibleChain_returnsEmpty() {
        var result = constraint.explainInfeasible(Map.of(
                v1, IntRangeDomain.of(0, 3), v2, IntRangeDomain.of(3, 6),
                v3, IntRangeDomain.of(6, 9), v4, IntRangeDomain.of(9, 12)));
        assertThat(result).isEmpty();
    }
}
