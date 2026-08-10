package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NumericSingletonDomainTest {

    @Test
    void getMinAndMax_returnTheValue() {
        var domain = new NumericSingletonDomain<>(5);
        assertThat(domain.getMin()).isEqualTo(5);
        assertThat(domain.getMax()).isEqualTo(5);
    }

    @Test
    void contains_value() {
        assertThat(new NumericSingletonDomain<>(5).contains(5)).isTrue();
    }

    @Test
    void doesNotContain_otherValue() {
        assertThat(new NumericSingletonDomain<>(5).contains(6)).isFalse();
    }

    @Test
    void isEmpty_alwaysFalse() {
        assertThat(new NumericSingletonDomain<>(5).isEmpty()).isFalse();
    }

    @Test
    void size_alwaysOne() {
        assertThat(new NumericSingletonDomain<>(5).size()).isEqualTo(1);
    }

    @Test
    void isSingleton() {
        assertThat(new NumericSingletonDomain<>(5).isSingleton()).isTrue();
    }

    @Test
    void stream_returnsSingletonStream() {
        assertThat(new NumericSingletonDomain<>(5).stream()).containsExactly(5);
    }

    @Test
    void toList_returnsSingletonList() {
        assertThat(new NumericSingletonDomain<>(5).toList()).containsExactly(5);
    }

    @Test
    void singleValue_returnsTheValue() {
        assertThat(new NumericSingletonDomain<>(5).singleValue()).contains(5);
    }

    @Test
    void toBuilder_deletingTheValue_buildsEmptyDomain() {
        DiscreteDomain<Integer> result = new NumericSingletonDomain<>(5).toBuilder().delete(5).build();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void toBuilder_deletingADifferentValue_leavesItUnchanged() {
        DiscreteDomain<Integer> result = new NumericSingletonDomain<>(5).toBuilder().delete(6).build();
        assertThat(result.singleValue()).contains(5);
    }

    @Test
    void equals_sameValue() {
        assertThat(new NumericSingletonDomain<>(5)).isEqualTo(new NumericSingletonDomain<>(5));
    }

    @Test
    void equals_sameInstance() {
        var domain = new NumericSingletonDomain<>(5);
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentValue() {
        assertThat(new NumericSingletonDomain<>(5)).isNotEqualTo(new NumericSingletonDomain<>(6));
    }

    @Test
    void equals_differentType() {
        assertThat(new NumericSingletonDomain<>(5).equals("5")).isFalse();
    }

    @Test
    void hashCode_consistent() {
        assertThat(new NumericSingletonDomain<>(5).hashCode())
                .isEqualTo(new NumericSingletonDomain<>(5).hashCode());
    }

    @Test
    void testToString() {
        assertThat(new NumericSingletonDomain<>(5).toString()).isEqualTo("{5}");
    }

    // ── Cross-type equality symmetry with ObjectSingletonDomain and DiscreteSetDomain -- NumericSingletonDomain
    // is a DiscreteDomain like both, so all three must agree on equal single-valued domains. ──

    @Test
    void equals_singletonDomain_bothDirections() {
        NumericSingletonDomain<Integer> numeric = new NumericSingletonDomain<>(5);
        ObjectSingletonDomain<Integer> plain = new ObjectSingletonDomain<>(5);

        assertThat(numeric.equals(plain)).isTrue();
        assertThat(plain.equals(numeric)).isTrue();
    }

    @Test
    void equals_intRangeDomain_bothDirections() {
        NumericSingletonDomain<Integer> numeric = new NumericSingletonDomain<>(5);
        IntRangeDomain range = IntRangeDomain.of(5, 5);

        assertThat(numeric.equals(range)).isTrue();
        assertThat(range.equals(numeric)).isTrue();
    }

    @Test
    void equals_domainObjectSet_bothDirections() {
        NumericSingletonDomain<Integer> numeric = new NumericSingletonDomain<>(5);
        ObjectSetDomain<Integer> set = new ObjectSetDomain<>(Set.of(5));

        assertThat(numeric.equals(set)).isTrue();
        assertThat(set.equals(numeric)).isTrue();
    }

    @Test
    void equals_numericSetDomain_bothDirections() {
        NumericSingletonDomain<Integer> numeric = new NumericSingletonDomain<>(5);
        NumericSetDomain<Integer> set = new NumericSetDomain<>(Set.of(5));

        assertThat(numeric.equals(set)).isTrue();
        assertThat(set.equals(numeric)).isTrue();
    }

    @Test
    void hashCode_matchesObjectSingletonDomain() {
        assertThat(new NumericSingletonDomain<>(5).hashCode())
                .isEqualTo(new ObjectSingletonDomain<>(5).hashCode());
    }
}
