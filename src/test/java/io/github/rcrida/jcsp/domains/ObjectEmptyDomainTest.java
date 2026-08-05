package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectEmptyDomainTest {

    @Test
    void instance_isEmpty() {
        ObjectEmptyDomain<Object> domain = ObjectEmptyDomain.instance();
        assertThat(domain.isEmpty()).isTrue();
    }

    @Test
    void size_alwaysZero() {
        assertThat(ObjectEmptyDomain.instance().size()).isEqualTo(0);
    }

    @Test
    void contains_alwaysFalse() {
        assertThat(ObjectEmptyDomain.instance().contains("x")).isFalse();
    }

    @Test
    void stream_isEmpty() {
        assertThat(ObjectEmptyDomain.instance().stream()).isEmpty();
    }

    @Test
    void toList_isEmpty() {
        assertThat(ObjectEmptyDomain.instance().toList()).isEmpty();
    }

    @Test
    void singleValue_isEmpty() {
        assertThat(ObjectEmptyDomain.instance().singleValue()).isEmpty();
    }

    @Test
    void toBuilder_buildsBackToEmpty() {
        DiscreteDomain<Object> rebuilt = ObjectEmptyDomain.instance().toBuilder().build();
        assertThat(rebuilt).isInstanceOf(ObjectEmptyDomain.class);
    }

    @Test
    void equals_sameInstance() {
        ObjectEmptyDomain<Object> domain = ObjectEmptyDomain.instance();
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentType() {
        assertThat(ObjectEmptyDomain.instance().equals("x")).isFalse();
    }

    @Test
    void equals_nonEmptyDomain_false() {
        assertThat(ObjectEmptyDomain.instance().equals(new ObjectSingletonDomain("x"))).isFalse();
    }

    // ── Cross-type equality symmetry with every other DiscreteDomain kind that can be empty ──

    @Test
    void equals_objectSetDomain_bothDirections() {
        ObjectEmptyDomain<Object> empty = ObjectEmptyDomain.instance();
        ObjectSetDomain<Object> set = new ObjectSetDomain<>(Set.of());

        assertThat(empty.equals(set)).isTrue();
        assertThat(set.equals(empty)).isTrue();
    }

    @Test
    void equals_numericEmptyDomain_bothDirections() {
        ObjectEmptyDomain<Object> objectEmpty = ObjectEmptyDomain.instance();
        NumericEmptyDomain<Integer> numericEmpty = NumericEmptyDomain.instance();

        assertThat(objectEmpty.equals(numericEmpty)).isTrue();
        assertThat(numericEmpty.equals(objectEmpty)).isTrue();
    }

    @Test
    void hashCode_matchesEmptySet() {
        assertThat(ObjectEmptyDomain.instance().hashCode()).isEqualTo(Set.of().hashCode());
    }

    @Test
    void hashCode_matchesObjectSetDomain() {
        assertThat(ObjectEmptyDomain.instance().hashCode())
                .isEqualTo(new ObjectSetDomain<>(Set.of()).hashCode());
    }

    @Test
    void testToString() {
        assertThat(ObjectEmptyDomain.instance().toString()).isEqualTo("{}");
    }
}
