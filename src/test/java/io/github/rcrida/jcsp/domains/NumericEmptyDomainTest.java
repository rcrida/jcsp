package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumericEmptyDomainTest {

    @Test
    void instance_isEmpty() {
        NumericEmptyDomain<Integer> domain = NumericEmptyDomain.instance();
        assertThat(domain.isEmpty()).isTrue();
    }

    @Test
    void size_alwaysZero() {
        assertThat(NumericEmptyDomain.<Integer>instance().size()).isEqualTo(0);
    }

    @Test
    void contains_alwaysFalse() {
        assertThat(NumericEmptyDomain.<Integer>instance().contains(5)).isFalse();
    }

    @Test
    void stream_isEmpty() {
        assertThat(NumericEmptyDomain.<Integer>instance().stream()).isEmpty();
    }

    @Test
    void toList_isEmpty() {
        assertThat(NumericEmptyDomain.<Integer>instance().toList()).isEmpty();
    }

    @Test
    void singleValue_isEmpty() {
        assertThat(NumericEmptyDomain.<Integer>instance().singleValue()).isEmpty();
    }

    @Test
    void getMin_throws() {
        assertThatThrownBy(() -> NumericEmptyDomain.<Integer>instance().getMin())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMax_throws() {
        assertThatThrownBy(() -> NumericEmptyDomain.<Integer>instance().getMax())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void toBuilder_buildsBackToEmpty() {
        DiscreteDomain<Integer> rebuilt = NumericEmptyDomain.<Integer>instance().toBuilder().build();
        assertThat(rebuilt).isInstanceOf(ObjectEmptyDomain.class);
    }

    @Test
    void equals_sameInstance() {
        NumericEmptyDomain<Integer> domain = NumericEmptyDomain.instance();
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentType() {
        assertThat(NumericEmptyDomain.<Integer>instance().equals("x")).isFalse();
    }

    @Test
    void equals_nonEmptyDomain_false() {
        assertThat(NumericEmptyDomain.<Integer>instance().equals(new NumericSingletonDomain<>(5))).isFalse();
    }

    // ── Cross-type equality symmetry with every other DiscreteDomain kind that can be empty ──

    @Test
    void equals_numericSetDomain_bothDirections() {
        NumericEmptyDomain<Integer> empty = NumericEmptyDomain.instance();
        NumericSetDomain<Integer> set = new NumericSetDomain<>(Set.of());

        assertThat(empty.equals(set)).isTrue();
        assertThat(set.equals(empty)).isTrue();
    }

    @Test
    void equals_objectEmptyDomain_bothDirections() {
        NumericEmptyDomain<Integer> numericEmpty = NumericEmptyDomain.instance();
        ObjectEmptyDomain<Object> objectEmpty = ObjectEmptyDomain.instance();

        assertThat(numericEmpty.equals(objectEmpty)).isTrue();
        assertThat(objectEmpty.equals(numericEmpty)).isTrue();
    }

    @Test
    void hashCode_matchesEmptySet() {
        assertThat(NumericEmptyDomain.<Integer>instance().hashCode()).isEqualTo(Set.of().hashCode());
    }

    @Test
    void testToString() {
        assertThat(NumericEmptyDomain.<Integer>instance().toString()).isEqualTo("{}");
    }
}
