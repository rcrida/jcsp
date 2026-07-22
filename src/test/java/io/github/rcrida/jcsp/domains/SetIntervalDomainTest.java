package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SetIntervalDomainTest {

    @Test
    void of_lowerBoundNotSubsetOfUpperBound_throwsAssertionError() {
        assertThatThrownBy(() -> SetIntervalDomain.of(Set.of(1, 2), Set.of(1), 0, 2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must be a subset of");
    }

    @Test
    void of_negativeMinCardinality_throwsAssertionError() {
        assertThatThrownBy(() -> SetIntervalDomain.of(Set.of(), Set.of(1, 2), -1, 2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void of_minCardinalityExceedsMaxCardinality_throwsAssertionError() {
        assertThatThrownBy(() -> SetIntervalDomain.of(Set.of(), Set.of(1, 2), 2, 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must be less than or equal to");
    }

    @Test
    void of_lowerBoundExceedsMaxCardinality_throwsAssertionError() {
        assertThatThrownBy(() -> SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2, 3), 0, 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not exceed maxCardinality");
    }

    @Test
    void of_upperBoundBelowMinCardinality_throwsAssertionError() {
        assertThatThrownBy(() -> SetIntervalDomain.of(Set.of(), Set.of(1, 2), 3, 3))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must be at least minCardinality");
    }

    @Test
    void of_universeOnly_defaultsToEmptyLowerBoundFullUpperBoundAndFullCardinalityRange() {
        var d = SetIntervalDomain.of(Set.of(1, 2, 3));
        assertThat(d.getLowerBound()).isEmpty();
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1, 2, 3));
        assertThat(d.getMinCardinality()).isZero();
        assertThat(d.getMaxCardinality()).isEqualTo(3);
    }

    @Test
    void gettersReturnConstructedBounds() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        assertThat(d.getLowerBound()).isEqualTo(Set.of(1));
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1, 2, 3));
        assertThat(d.getMinCardinality()).isEqualTo(1);
        assertThat(d.getMaxCardinality()).isEqualTo(2);
    }

    @Test
    void withLowerBound_unionsForcedInElements() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3).withLowerBound(Set.of(2));
        assertThat(d.getLowerBound()).isEqualTo(Set.of(1, 2));
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1, 2, 3));
    }

    @Test
    void withLowerBound_reachingMaxCardinality_forcesUpperBoundDown() {
        // Forcing lowerBound to size 1 while maxCardinality is 1 means no further element can
        // ever be added, so the domain-intrinsic tightening should collapse upperBound to match.
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 1).withLowerBound(Set.of(1));
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1));
    }

    @Test
    void withUpperBound_reachingMinCardinality_forcesLowerBoundUp() {
        // Narrowing upperBound to size 2 while minCardinality is 2 means no candidate can ever be
        // dropped, so the domain-intrinsic tightening should expand lowerBound to match.
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 2, 3).withUpperBound(Set.of(1, 2));
        assertThat(d.getLowerBound()).isEqualTo(Set.of(1, 2));
    }

    @Test
    void withLowerBound_elementOutsideUpperBound_producesEmptyDomain() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 2).withLowerBound(Set.of(3));
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void withUpperBound_intersectsCandidates() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3).withUpperBound(Set.of(1, 2));
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1, 2));
        assertThat(d.getLowerBound()).isEqualTo(Set.of(1));
    }

    @Test
    void withUpperBound_excludesLowerBoundElement_producesEmptyDomain() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3).withUpperBound(Set.of(2, 3));
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void withCardinality_narrowsToIntersection() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3).withCardinality(1, 2);
        assertThat(d.getMinCardinality()).isEqualTo(1);
        assertThat(d.getMaxCardinality()).isEqualTo(2);
    }

    @Test
    void withCardinality_widerRangeDoesNotExpandDomain() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 2).withCardinality(0, 3);
        assertThat(d.getMinCardinality()).isEqualTo(1);
        assertThat(d.getMaxCardinality()).isEqualTo(2);
    }

    @Test
    void contains_nonSet_returnsFalse() {
        var d = SetIntervalDomain.of(Set.of(1, 2, 3));
        assertThat(d.contains("not a set")).isFalse();
    }

    @Test
    void contains_cardinalityOutOfRange_returnsFalse() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 2);
        assertThat(d.contains(Set.of())).isFalse();
        assertThat(d.contains(Set.of(1, 2, 3))).isFalse();
    }

    @Test
    void contains_missingLowerBoundElement_returnsFalse() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        assertThat(d.contains(Set.of(2))).isFalse();
    }

    @Test
    void contains_elementOutsideUpperBound_returnsFalse() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 3);
        assertThat(d.contains(Set.of(1, 3))).isFalse();
    }

    @Test
    void contains_validValue_returnsTrue() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        assertThat(d.contains(Set.of(1, 2))).isTrue();
    }

    @Test
    void isEmpty_falseForValidDomain() {
        assertThat(SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_trueWhenLowerBoundEscapesUpperBound() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 2).withLowerBound(Set.of(3));
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_trueWhenCardinalityRangeContradictory() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3).withCardinality(2, 3).withCardinality(0, 1);
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_trueWhenLowerBoundExceedsMaxCardinality() {
        var d = SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2, 3), 0, 3).withCardinality(0, 1);
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_trueWhenUpperBoundBelowMinCardinality() {
        // withCardinality only narrows via intersection, so minCardinality=2 is fixed from
        // construction; shrinking the upper bound below it in isolation (lowerBound stays a
        // subset, cardinality range stays internally consistent) isolates this exact branch.
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 2, 3).withUpperBound(Set.of(1));
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void size_singletonReturnsOne() {
        var d = SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 0, 2);
        assertThat(d.size()).isEqualTo(1);
    }

    @Test
    void size_nonSingletonReturnsMaxValue() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        assertThat(d.size()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void isSingleton_trueWhenLowerBoundEqualsUpperBound() {
        assertThat(SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 0, 2).isSingleton()).isTrue();
    }

    @Test
    void isSingleton_falseWhenBoundsDiffer() {
        assertThat(SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2).isSingleton()).isFalse();
    }

    @Test
    void isSingleton_falseWhenEmptyDespiteEqualBounds() {
        var d = SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 0, 2).withCardinality(5, 5);
        assertThat(d.isSingleton()).isFalse();
    }

    @Test
    void singleValue_presentOnlyForSingleton() {
        assertThat(SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 0, 2).singleValue()).contains(Set.of(1, 2));
        assertThat(SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2).singleValue()).isEmpty();
    }

    @Test
    void testToString() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        assertThat(d.toString()).isEqualTo("[[1] subsetOf S subsetOf [1, 2], |S| in [0, 2]]");
    }

    @Test
    void withUpperBound_narrowingBelowForcedLowerBound_correctlyReportsEmpty_notSilentlyRestored() {
        // Regression test: the domain-intrinsic tightening (upperBound narrows towards lowerBound
        // once |lowerBound|==maxCardinality) must use intersection, not a blind overwrite --
        // otherwise a caller narrowing upperBound below what lowerBound already requires would have
        // that narrower, correctly-infeasible value silently discarded and replaced back with
        // lowerBound, masking a genuine contradiction instead of reporting it. Found via
        // DisjointConstraint propagating a real exclusion into exactly this state (SetBranchingSolver
        // forcing an element into one side that a disjoint partner already requires).
        var base = SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1);
        var narrowed = base.withUpperBound(Set.of());
        assertThat(narrowed.isEmpty()).isTrue();
    }

    // --- comparator: construction ---

    @Test
    void of_universeWithComparator_defaultsToEmptyLowerBoundFullUpperBoundAndFullCardinalityRange() {
        var d = SetIntervalDomain.of(Set.of(1, 2, 3), Comparator.<Integer>naturalOrder());
        assertThat(d.getLowerBound()).isEmpty();
        assertThat(d.getUpperBound()).isEqualTo(Set.of(1, 2, 3));
        assertThat(d.getMinCardinality()).isZero();
        assertThat(d.getMaxCardinality()).isEqualTo(3);
    }

    @Test
    void getComparator_returnsSuppliedComparator() {
        Comparator<Integer> reverse = Comparator.reverseOrder();
        var d = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3, reverse);
        assertThat(d.getComparator()).isSameAs(reverse);
    }

    @Test
    void of_naturalOrderFactory_defaultsToNaturalOrderComparator() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        assertThat(d.getComparator()).isEqualTo(Comparator.<Integer>naturalOrder());
    }

    @Test
    void of_explicitComparator_ordersBoundsByIt() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(3, 1, 2), 0, 3, Comparator.<Integer>reverseOrder());
        assertThat(d.getUpperBound()).containsExactly(3, 2, 1);
    }

    @Test
    void withLowerBound_preservesComparatorAcrossNarrowing() {
        var d = SetIntervalDomain.of(Set.of(), Set.of(3, 1, 2), 0, 3, Comparator.<Integer>reverseOrder())
                .withLowerBound(Set.of(1));
        assertThat(d.getLowerBound()).containsExactly(1);
        assertThat(d.getUpperBound()).containsExactly(3, 2, 1);
        assertThat(d.getComparator()).isEqualTo(Comparator.<Integer>reverseOrder());
    }

    record Point(int x, int y) {}

    @Test
    void of_nonComparableTypeWithExplicitComparator_ordersBoundsByIt() {
        var p1 = new Point(1, 5);
        var p2 = new Point(2, 3);
        var d = SetIntervalDomain.of(Set.of(), Set.of(p2, p1), 0, 2, Comparator.comparingInt(Point::x));
        assertThat(d.getUpperBound()).containsExactly(p1, p2);
    }

    // --- equals / hashCode ---

    @Test
    void equals_sameReference_true() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        assertThat(d.equals(d)).isTrue();
    }

    @Test
    void equals_differentType_false() {
        var d = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        assertThat(d.equals("not a domain")).isFalse();
    }

    @Test
    void equals_sameContentDifferentComparatorInstance_trueWithMatchingHashCode() {
        var d1 = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        var d2 = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2, Comparator.<Integer>naturalOrder());
        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    }

    @Test
    void equals_differentContent_false() {
        var d1 = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 0, 2);
        var d2 = SetIntervalDomain.of(Set.of(2), Set.of(1, 2), 0, 2);
        assertThat(d1).isNotEqualTo(d2);
    }
}
