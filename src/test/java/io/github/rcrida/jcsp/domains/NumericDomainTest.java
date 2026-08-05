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
    void withBounds_noOpNarrowing_producesDomainEqualToInput() {
        // NumericBounds#narrow relies on this equality to detect a no-op narrowing and skip the
        // domain-map update entirely -- a singleton IntRangeDomain narrowed to bounds it already
        // satisfies must compare equal to a fresh NumericSingletonDomain holding the same value.
        IntRangeDomain domain = IntRangeDomain.of(5, 5);
        NumericDomain<Integer> narrowed = domain.withBounds(0, 10);

        assertThat(narrowed).isEqualTo(domain);
        assertThat(domain).isEqualTo(narrowed);
    }
}
