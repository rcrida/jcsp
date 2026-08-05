package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NumericSetDomainTest {

    @Test
    void of_oneValue_returnsNumericSingletonDomain() {
        NumericDomain<Integer> domain = NumericDiscreteDomain.of(5);

        assertThat(domain).isInstanceOf(NumericSingletonDomain.class);
        assertThat(domain.getMin()).isEqualTo(5);
        assertThat(domain.getMax()).isEqualTo(5);
    }

    @Test
    void of_multipleValues_returnsNumericSetDomain() {
        NumericDomain<Integer> domain = NumericDiscreteDomain.of(1, 2, 3);

        assertThat(domain).isInstanceOf(NumericSetDomain.class);
        assertThat(((NumericSetDomain<Integer>) domain).values()).containsExactlyInAnyOrder(1, 2, 3);
    }

    // ── Value-by-value deletion (the AC3 arc-revision path -- polymorphic toBuilder()/delete(),
    // distinct from the withBounds path covered by NumericDomainTest) ──

    @Test
    void toBuilder_deletedDownToOneValue_buildsNumericSingletonDomain() {
        DiscreteDomain<Integer> narrowed = new NumericSetDomain<>(Set.of(1, 2)).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains(2);
    }

    @Test
    void toBuilder_deletedDownToZeroValues_buildsEmptyNumericSetDomain() {
        DiscreteDomain<Integer> narrowed = new NumericSetDomain<>(Set.of(1)).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericSetDomain.class);
        assertThat(narrowed.isEmpty()).isTrue();
    }

    @Test
    void builder_neverAddedAnyValue_buildsEmptyNumericSetDomain() {
        // Lombok's @Singular field starts null (not an empty collection) until first added --
        // build() must tolerate a builder nothing was ever added to.
        DiscreteDomain<Integer> built = NumericSetDomain.<Integer>builder().build();

        assertThat(built).isInstanceOf(NumericSetDomain.class);
        assertThat(built.isEmpty()).isTrue();
    }
}
