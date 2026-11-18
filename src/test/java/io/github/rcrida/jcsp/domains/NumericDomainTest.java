package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link NumericDomain}'s default {@code withBounds} method directly -- both real
 * implementors that rely on it ({@link IntRangeDomain}, {@link NumericSetDomain}; {@link
 * IntervalDomain}/{@link BoundedDomain} override it themselves and are covered by their own tests).
 */
class NumericDomainTest {

    @Test
    void withBounds_intRangeDomain_narrowedToOneValue_returnsNumericSingletonDomain() {
        NumericDomain<Integer> narrowed = IntRangeDomain.of(1, 5).withBounds(5, 10);

        assertThat(narrowed).isInstanceOf(NumericSingletonDomain.class);
        assertThat(narrowed.getMin()).isEqualTo(5);
        assertThat(narrowed.getMax()).isEqualTo(5);
    }

    @Test
    void withBounds_intRangeDomain_narrowedToMultipleValues_returnsNumericSetDomain() {
        NumericDomain<Integer> narrowed = IntRangeDomain.of(1, 5).withBounds(3, 10);

        assertThat(narrowed).isInstanceOf(NumericSetDomain.class);
        assertThat(((NumericSetDomain<Integer>) narrowed).values()).containsExactly(3, 4, 5);
    }

    @Test
    void withBounds_numericSetDomain_narrowedToOneValue_returnsNumericSingletonDomain() {
        NumericDomain<Integer> narrowed = NumericDiscreteDomain.of(1, 2, 5).withBounds(4, 10);

        assertThat(narrowed).isInstanceOf(NumericSingletonDomain.class);
        assertThat(narrowed.getMin()).isEqualTo(5);
    }

    @Test
    void withBounds_setDomainOverAPlainSet_filtersIt() {
        NumericDomain<Integer> domain = new NumericSetDomain<>(new java.util.LinkedHashSet<>(java.util.List.of(1, 2, 5)));

        assertThat(domain.withBounds(2, 5)).isEqualTo(NumericDiscreteDomain.of(2, 5));
    }

    @Test
    void withBounds_numericSetDomain_narrowedToMultipleValues_returnsNumericSetDomain() {
        NumericDomain<Integer> narrowed = NumericDiscreteDomain.of(1, 2, 5).withBounds(0, 10);

        assertThat(narrowed).isInstanceOf(NumericSetDomain.class);
    }

    @Test
    void withBounds_narrowedToNoValues_returnsNumericEmptyDomain() {
        NumericDomain<Integer> narrowed = IntRangeDomain.of(1, 5).withBounds(100, 200);

        assertThat(narrowed).isInstanceOf(NumericEmptyDomain.class);
        assertThat(narrowed.isEmpty()).isTrue();
    }

    @Test
    void withBounds_rangeAlreadyContainsEveryValue_returnsTheSameInstance() {
        // NumericBounds#narrow detects a no-op narrowing from this identity -- through
        // DiscreteSetDomain.domainEquals' own self == o fast path -- rather than by rebuilding the
        // domain and comparing value sets, and the domain keeps its own type rather than being
        // collapsed into whatever NumericDiscreteDomainBuilder would have produced.
        NumericSetDomain<Integer> domain = IntRangeDomain.of(1, 5);

        assertThat(domain.withBounds(0, 10)).isSameAs(domain);
    }

    @Test
    void withBounds_ascendingDomain_isSlicedFromBothEnds() {
        NumericSetDomain<Integer> domain = (NumericSetDomain<Integer>) NumericDiscreteDomain.of(1, 3, 5, 7, 9, 11);

        NumericDomain<Integer> narrowed = domain.withBounds(2, 10);

        assertThat(narrowed).isInstanceOf(NumericSetDomain.class).isEqualTo(NumericDiscreteDomain.of(3, 5, 7, 9));
        assertThat(narrowed.getMin()).isEqualTo(3);
        assertThat(narrowed.getMax()).isEqualTo(9);
        OrderedValueSet<?> values = (OrderedValueSet<?>) ((NumericSetDomain<Integer>) narrowed).values();
        assertThat(values.isAscending()).isTrue();
        assertThat(values.isRange()).isFalse();
        assertThat(domain.withBounds(4, 6)).isInstanceOf(NumericSingletonDomain.class).isEqualTo(NumericDiscreteDomain.of(5));
        assertThat(domain.withBounds(4.2, 4.8)).isInstanceOf(NumericEmptyDomain.class);
    }

    @Test
    void withBounds_unorderedDomain_filtersKeepingItsOrderAndBounds() {
        NumericSetDomain<Integer> domain = (NumericSetDomain<Integer>) NumericDiscreteDomain.of(9, 1, 7, 3, 5);

        NumericDomain<Integer> narrowed = domain.withBounds(2, 8);

        assertThat(((NumericSetDomain<Integer>) narrowed).toList()).containsExactly(7, 3, 5);
        assertThat(narrowed.getMin()).isEqualTo(3);
        assertThat(narrowed.getMax()).isEqualTo(7);
        assertThat(((NumericSetDomain<Integer>) narrowed).contains(9)).isFalse();
        assertThat(domain.withBounds(6, 8)).isInstanceOf(NumericSingletonDomain.class).isEqualTo(NumericDiscreteDomain.of(7));
        assertThat(domain.withBounds(5.5, 6.5)).isInstanceOf(NumericEmptyDomain.class);
    }

    @Test
    void withBounds_descendingDomain_isFilteredNotSliced() {
        NumericDomain<Integer> narrowed = NumericDiscreteDomain.of(9, 7, 5, 3, 1).withBounds(2, 8);

        assertThat(((NumericSetDomain<Integer>) narrowed).toList()).containsExactly(7, 5, 3);
        assertThat(narrowed.getMin()).isEqualTo(3);
        assertThat(narrowed.getMax()).isEqualTo(7);
    }

    @Test
    void withBounds_emptyDomain_staysEmpty() {
        // An empty domain has no bounds to compare against, so it can't take the fast path above.
        NumericDomain<Integer> narrowed = NumericEmptyDomain.<Integer>instance().withBounds(0, 10);

        assertThat(narrowed.isEmpty()).isTrue();
    }
}
