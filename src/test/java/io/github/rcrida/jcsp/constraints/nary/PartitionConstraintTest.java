package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.DisjointConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PartitionConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Set<Integer>> P1 = F.create("p1_partition");
    static final Variable<Set<Integer>> P2 = F.create("p2_partition");
    static final Variable<Set<Integer>> P3 = F.create("p3_partition");

    // --- construction ---

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2)))
                .isEqualTo(PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2)));
    }

    @Test
    void of_populatesVariablesFromParts() {
        var c = PartitionConstraint.of(Set.of(P1, P2, P3), Set.of(1, 2));
        assertThat(c.getVariables()).containsExactlyInAnyOrder(P1, P2, P3);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(P1, Set.of(1))))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBy_assignedPartsShareElement_violated() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(P1, Set.of(1), P2, Set.of(1))))).isFalse();
    }

    @Test
    void isSatisfiedBy_elementOutsideUniverse_violated() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(P1, Set.of(1), P2, Set.of(9))))).isFalse();
    }

    @Test
    void isSatisfiedBy_fullyAssignedWithCompleteCoverage_satisfied() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(P1, Set.of(1), P2, Set.of(2))))).isTrue();
    }

    @Test
    void isSatisfiedBy_fullyAssignedWithIncompleteCoverage_violated() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(P1, Set.of(1), P2, Set.of())))).isFalse();
    }

    // --- propagate: non-SetBoundedDomain sides ---

    @Test
    void propagate_nonSetBoundedDomainPart_noOp() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var p1Dom = DomainObjectSet.<Set<Integer>>builder().value(Set.of(1)).build();
        var p2Dom = SetIntervalDomain.of(Set.<Integer>of(), Set.of(2), 0, 1);
        var result = c.propagate(Map.of(P1, p1Dom, P2, p2Dom));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: infeasible ---

    @Test
    void propagate_twoDefinitePartsForceSameElement_infeasible() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2),
                P2, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_elementHasNoCandidatePart_infeasible() {
        // neither part even candidates element 2 -- coverage can never be satisfied.
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1), 0, 1),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1), 0, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagate: narrowing ---

    @Test
    void propagate_oneDefinitePart_excludesElementFromOtherParts() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 2), 0, 2));
        var result = c.propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(P2);
        assertThat(((SetIntervalDomain<Integer>) result.get(P2)).getUpperBound()).isEqualTo(Set.of(2));
    }

    @Test
    void propagate_exactlyOneUndeterminedCandidate_forcesElementIn() {
        // universe's 3rd element ("3") is candidated by both parts so it's never forced --
        // isolates the force-in behaviour to elements 1/2, each candidated by only one part.
        // upperBound genuinely has 2 candidates each (not just the eventually-forced element), so
        // SetIntervalDomain's own domain-intrinsic tightening doesn't pre-resolve this at
        // construction time the way {lower={}, upper={1}, card[1,1]} would.
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 3), 1, 1),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(2, 3), 1, 1));
        var result = c.propagate(domains).orElseThrow();
        assertThat(result).containsOnlyKeys(P1, P2);
        assertThat(((SetIntervalDomain<Integer>) result.get(P1)).getLowerBound()).isEqualTo(Set.of(1));
        assertThat(((SetIntervalDomain<Integer>) result.get(P1)).getUpperBound()).isEqualTo(Set.of(1));
        assertThat(((SetIntervalDomain<Integer>) result.get(P2)).getLowerBound()).isEqualTo(Set.of(2));
        assertThat(((SetIntervalDomain<Integer>) result.get(P2)).getUpperBound()).isEqualTo(Set.of(2));
    }

    @Test
    void propagate_twoOrMoreUndeterminedCandidates_noChange() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 2), 0, 2),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 2), 0, 2));
        var result = c.propagate(domains);
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_narrowingEmptiesAPart_infeasible() {
        // classify() itself finds no direct conflict (no element is double-forced, none has zero
        // candidates), but P1 requires exactly 2 elements while accumulated exclusion (1 and 2,
        // both forced elsewhere) plus forcing (3) leaves it only one candidate ({3}) -- a
        // narrowing-caused infeasibility distinct from anything classify() checks directly.
        var c = PartitionConstraint.of(Set.of(P1, P2, P3), Set.of(1, 2, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 2, 3), 2, 2),
                P2, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1),
                P3, SetIntervalDomain.of(Set.of(2), Set.of(2), 1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_twoDefiniteSingletonParts_returnsGroundNogood() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1),
                P2, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1));
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(GroundNogoodConstraint.class);
    }

    @Test
    void explainInfeasible_twoDefiniteNonSingletonParts_returnsBoundsNogoodScopedToJustThoseTwo() {
        var c = PartitionConstraint.of(Set.of(P1, P2, P3), Set.of(1, 2, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2),
                P2, SetIntervalDomain.of(Set.of(1), Set.of(1, 3), 1, 2),
                P3, SetIntervalDomain.of(Set.<Integer>of(), Set.of(2, 3), 0, 2));
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(SetBoundsNogoodConstraint.class);
        assertThat(reason.get().getVariables()).containsExactlyInAnyOrder(P1, P2);
    }

    @Test
    void explainInfeasible_coverageGap_returnsReasonOverEveryPart() {
        // Single-element universe so there's no ambiguity from Set's unspecified iteration order
        // about which element gets classified first.
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.<Integer>of(), Set.of(), 0, 0),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(), 0, 0));
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get().getVariables()).containsExactlyInAnyOrder(P1, P2);
    }

    @Test
    void explainInfeasible_noViolationPresent_returnsEmpty() {
        // Direct, white-box call: explainInfeasible is only ever invoked in practice right after
        // propagate() has reported this exact domain state infeasible, so its final "no violation
        // found" fallback is otherwise unreachable -- exercised here directly, matching the same
        // approach used for SetBoundsNogoodConstraint.pruneCardinality's analogous unreachable branch.
        // Element 1 has exactly one definite part (definite.isEmpty() false) and element 2 has
        // zero definite but one still-undetermined candidate (definite.isEmpty() true, some part
        // still open) -- neither element ever returns, so both are guaranteed to be evaluated
        // regardless of Set's iteration order, deterministically covering both outcomes.
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                P1, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1),
                P2, SetIntervalDomain.of(Set.<Integer>of(), Set.of(2), 0, 1));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- BinaryDecomposable ---

    @Test
    void getAsBinaryConstraints_returnsEveryPairwiseDisjoint() {
        // PartitionConstraint.of takes parts as a Set, so which side of each pair ends up "left"
        // vs "right" is unspecified -- compare unordered variable pairs, not exact left/right
        // orientation.
        var c = PartitionConstraint.of(Set.of(P1, P2, P3), Set.of(1, 2));
        var binary = c.getAsBinaryConstraints();
        assertThat(binary).hasSize(3);
        assertThat(binary).allMatch(bc -> bc instanceof DisjointConstraint);
        var actualPairs = binary.stream()
                .map(bc -> Set.of(((BinaryConstraint<?, ?>) bc).getLeft(), ((BinaryConstraint<?, ?>) bc).getRight()))
                .collect(Collectors.toSet());
        assertThat(actualPairs).containsExactlyInAnyOrder(Set.of(P1, P2), Set.of(P1, P3), Set.of(P2, P3));
    }

    @Test
    void isDecompositionComplete_isFalse() {
        var c = PartitionConstraint.of(Set.of(P1, P2), Set.of(1, 2));
        assertThat(c.isDecompositionComplete()).isFalse();
    }

    // --- misc ---

    @Test
    void testToString() {
        var c = PartitionConstraint.of(Set.of(P1), Set.of(1, 2));
        assertThat(c.toString()).isEqualTo("<(p1_partition), partition(parts=1, |universe|=2)>");
    }
}
