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
        assertThat(((NumericSetDomain<Integer>) narrowed).values()).containsExactlyInAnyOrder(3, 4, 5);
    }

    @Test
    void withBounds_numericSetDomain_narrowedToOneValue_returnsNumericSingletonDomain() {
        NumericDomain<Integer> narrowed = NumericDiscreteDomain.of(1, 2, 5).withBounds(4, 10);

        assertThat(narrowed).isInstanceOf(NumericSingletonDomain.class);
        assertThat(narrowed.getMin()).isEqualTo(5);
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
        IntRangeDomain domain = IntRangeDomain.of(1, 5);

        assertThat(domain.withBounds(0, 10)).isSameAs(domain);
    }

    @Test
    void withBounds_emptyDomain_staysEmpty() {
        // An empty domain has no bounds to compare against, so it can't take the fast path above.
        NumericDomain<Integer> narrowed = NumericEmptyDomain.<Integer>instance().withBounds(0, 10);

        assertThat(narrowed.isEmpty()).isTrue();
    }
}
