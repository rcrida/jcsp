package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntRangeDomainTest {
    @Test
    void constructInvalid() {
        assertThatThrownBy(() -> IntRangeDomain.of(10, 5))
            .isInstanceOf(AssertionError.class)
            .hasMessage("minInclusive (10) must be less than or equal to maxInclusive (5)");
    }

    @Test
    void contains() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5)).isTrue();
        assertThat(domain.contains(10)).isTrue();
        assertThat(domain.contains(4)).isFalse();
        assertThat(domain.contains(11)).isFalse();
    }

    @Test
    void containsNull() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(null)).isFalse();
    }

    @Test
    void containsInvalid() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5.5)).isFalse();
        assertThat(domain.contains("5")).isFalse();
    }

    @Test
    void stream() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.stream()).containsOnly(5, 6, 7, 8, 9, 10);
    }

    @Test
    void getMinAndMax_of() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.getMin()).isEqualTo(5);
        assertThat(domain.getMax()).isEqualTo(10);
    }

    @Test
    void testToString() {
        assertThat(IntRangeDomain.of(5, 10).toString()).isEqualTo("[5..10]");
    }

    /**
     * A one-value range and a {@link NumericSingletonDomain} denote the same domain, so narrowing
     * one into the other must not read as a change. {@link NumericDomain#withBounds} used to be
     * what exercised this -- it rebuilt even a no-op narrowing, collapsing the range to a
     * singleton -- and now short-circuits instead, so the property needs asserting directly.
     */
    @Test
    void equals_singletonRange_matchesNumericSingletonDomain_bothDirections() {
        IntRangeDomain range = IntRangeDomain.of(5, 5);
        NumericSingletonDomain<Integer> singleton = new NumericSingletonDomain<>(5);

        assertThat(range.equals(singleton)).isTrue();
        assertThat(singleton.equals(range)).isTrue();
    }

    @Test
    void constructor_mismatchedMin_throwsAssertionError() {
        // of() can never produce this (it always passes its own minInclusive/maxInclusive
        // straight through), but the canonical constructor is necessarily public -- a record's
        // canonical constructor can't be more restrictive than the record itself -- so the
        // min/max-matches-values invariant is enforced by assertion instead of access control.
        assertThatThrownBy(() -> new IntRangeDomain(Set.of(1, 2, 3), 100, 200))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_mismatchedMax_throwsAssertionError() {
        // Distinct from the mismatched-min case above: min==1 matches, so the short-circuiting
        // && only reaches (and fails) the max comparison here, not the min one.
        assertThatThrownBy(() -> new IntRangeDomain(Set.of(1, 2, 3), 1, 200))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_emptyValues_skipsMinMaxCheck() {
        // Collections.min/max would throw NoSuchElementException on an empty collection; the
        // assert's isEmpty() short-circuit avoids ever evaluating them in that case.
        var domain = new IntRangeDomain(Set.of(), 0, 0);
        assertThat(domain.isEmpty()).isTrue();
    }

    // ── Narrowing stays numeric ───────────────────────────────────────────────
    // Inheriting DiscreteSetDomain's generic builder demoted a narrowed range to a non-numeric
    // ObjectSetDomain, so every later `instanceof NumericDomain` test on it silently failed. AC3
    // narrows by deleting one value at a time, so this happened to essentially every integer
    // variable the moment arc consistency touched it.

    @Test
    void toBuilder_narrowedToSeveralValues_staysNumeric() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 3).toBuilder().delete(3).build();

        assertThat(narrowed).isInstanceOf(NumericDomain.class);
        assertThat(narrowed).isInstanceOf(NumericSetDomain.class);
        assertThat(((NumericDomain<Integer>) narrowed).getMax()).isEqualTo(2);
    }

    @Test
    void toBuilder_narrowedToOneValue_staysNumeric() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 2).toBuilder().delete(2).build();

        assertThat(narrowed).isInstanceOf(NumericDomain.class);
        assertThat(narrowed.singleValue()).contains(1);
    }

    @Test
    void toBuilder_narrowedToNothing_staysNumeric() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 1).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericDomain.class);
        assertThat(narrowed.isEmpty()).isTrue();
    }

    // ---- a range narrowed by its bounds shares its parent's array ----------------------------------

    @Test
    void withBounds_narrowedRange_isASliceOverTheSameValues() {
        IntRangeDomain wide = IntRangeDomain.of(-10_000, 10_000);
        NumericDomain<Integer> narrowed = wide.withBounds(-5, 5);

        // A NumericSetDomain, as every other narrowing produces, whose values are a range slice.
        assertThat(narrowed).isInstanceOf(NumericSetDomain.class).isEqualTo(IntRangeDomain.of(-5, 5));
        assertThat(((OrderedValueSet<?>) ((NumericSetDomain<Integer>) narrowed).values()).isRange()).isTrue();
        assertThat(narrowed.getMin()).isEqualTo(-5);
        assertThat(narrowed.getMax()).isEqualTo(5);
        assertThat(wide.withBounds(-2.5, 2.5)).isEqualTo(IntRangeDomain.of(-2, 2));
        assertThat(wide.withBounds(Double.NEGATIVE_INFINITY, -9_999)).isEqualTo(IntRangeDomain.of(-10_000, -9_999));
        assertThat(wide.withBounds(3, 3)).isEqualTo(NumericDiscreteDomain.of(3)).isInstanceOf(NumericSingletonDomain.class);
        assertThat(wide.withBounds(20_000, 30_000).isEmpty()).isTrue();
        assertThat(wide.withBounds(0.2, 0.8).isEmpty()).isTrue();
        assertThat(wide.withBounds(-20_000, 20_000)).isSameAs(wide);
    }

    @Test
    void toString_aNarrowedRangePrintsAsARange() {
        NumericDomain<Integer> slice = IntRangeDomain.of(0, 99).withBounds(3, 6);

        assertThat(slice).hasToString("[3..6]");
        assertThat(((DiscreteDomain<Integer>) slice).toBuilder().delete(4).build()).hasToString("{3, 5, 6}");
        assertThat(OrderedValueSet.range(-2, 2)).hasToString("[-2..2]");
        assertThat(OrderedValueSet.handingOver(new java.util.LinkedHashSet<>(java.util.List.of(5, 1)))).hasToString("[5, 1]");
    }

    @Test
    void withBounds_onASlice_slicesAgain() {
        NumericDomain<Integer> slice = IntRangeDomain.of(0, 99).withBounds(10, 89);
        NumericDomain<Integer> narrower = slice.withBounds(20, 29);

        assertThat(narrower).isEqualTo(IntRangeDomain.of(20, 29));
        assertThat(((OrderedValueSet<?>) ((NumericSetDomain<Integer>) narrower).values()).isRange()).isTrue();
    }

    @Test
    void withBounds_rangeBuiltFromAnArbitrarySet_filtersAsAnyDomainDoes() {
        IntRangeDomain fromSet = new IntRangeDomain(Set.of(3, 1, 2), 1, 3);

        assertThat(fromSet.withBounds(2, 3)).isEqualTo(NumericDiscreteDomain.of(2, 3));
    }

    @Test
    void constructor_keepsARangeWithTheseBoundsAsItIs() {
        OrderedValueSet<Integer> slice = OrderedValueSet.range(0, 9).slice(2, 5);

        assertThat(new IntRangeDomain(slice, 2, 5).values()).isSameAs(slice);
        assertThat(new IntRangeDomain(OrderedValueSet.handingOver(new java.util.LinkedHashSet<>(Set.of(1, 2))), 1, 2))
                .isEqualTo(IntRangeDomain.of(1, 2));
    }

    @Test
    void constructor_rangeWithOtherBounds_isCheckedAsAnyOtherSet() {
        assertThatThrownBy(() -> new IntRangeDomain(OrderedValueSet.range(1, 3), 0, 3)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> new IntRangeDomain(OrderedValueSet.range(1, 3), 1, 4)).isInstanceOf(AssertionError.class);
    }

    @Test
    void toBuilder_onASlice_narrowsOnlyTheSlicesValues() {
        // A slice shares a wider array; the builder must walk only the slice's own part of it.
        DiscreteDomain<Integer> slice = (DiscreteDomain<Integer>) IntRangeDomain.of(0, 9).withBounds(3, 6);

        assertThat(slice.toBuilder().delete(4).build().toList()).containsExactly(3, 5, 6);
        assertThat(slice.toBuilder().delete(9).build()).isEqualTo(slice);
    }

    @Test
    void equalsAGaplessSetDomain() {
        assertThat(IntRangeDomain.of(1, 3).withBounds(1, 2)).isEqualTo(NumericDiscreteDomain.of(2, 1));
        assertThat(NumericDiscreteDomain.of(2, 1)).isEqualTo(IntRangeDomain.of(1, 3).withBounds(1, 2));
        assertThat(IntRangeDomain.of(1, 3).withBounds(1, 2).hashCode()).isEqualTo(NumericDiscreteDomain.of(2, 1).hashCode());
    }
}
