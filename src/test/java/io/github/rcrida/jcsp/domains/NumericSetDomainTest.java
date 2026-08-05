package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void testToString() {
        assertThat(NumericDiscreteDomain.of(1, 2, 3).toString()).isEqualTo("{1, 2, 3}");
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
    void toBuilder_deletedDownToZeroValues_buildsNumericEmptyDomain() {
        DiscreteDomain<Integer> narrowed = new NumericSetDomain<>(Set.of(1)).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericEmptyDomain.class);
        assertThat(narrowed.isEmpty()).isTrue();
    }

    @Test
    void builder_neverAddedAnyValue_buildsNumericEmptyDomain() {
        DiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().build();

        assertThat(built).isInstanceOf(NumericEmptyDomain.class);
        assertThat(built.isEmpty()).isTrue();
    }

    @Test
    void builder_value_addsOneAtATime() {
        NumericDiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().value(1).value(2).build();

        assertThat(built).isInstanceOf(NumericSetDomain.class);
        assertThat(built.toList()).containsExactly(1, 2);
    }

    @Test
    void builder_values_addsACollection() {
        NumericDiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().values(List.of(1, 2)).build();

        assertThat(built.toList()).containsExactly(1, 2);
    }
}
