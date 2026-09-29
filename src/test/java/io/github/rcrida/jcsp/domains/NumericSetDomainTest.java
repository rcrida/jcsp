package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ── Cached bounds (see this domain's own Javadoc: null bounds are reachable only through the
    // canonical constructor, since the builder collapses an empty value set to NumericEmptyDomain) ──

    @Test
    void getMinAndGetMax_unorderedValues_returnTheExtremes() {
        NumericDiscreteDomain<Integer> domain = NumericDiscreteDomain.of(3, 1, 2);

        assertThat(domain.getMin()).isEqualTo(1);
        assertThat(domain.getMax()).isEqualTo(3);
    }

    @Test
    void getMin_emptyValues_throws() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.<Integer>of()).getMin())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMax_emptyValues_throws() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.<Integer>of()).getMax())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void constructor_mismatchedMin_throwsAssertionError() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.of(1, 2, 3), 100, 3))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_mismatchedMax_throwsAssertionError() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.of(1, 2, 3), 1, 200))
                .isInstanceOf(AssertionError.class);
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
