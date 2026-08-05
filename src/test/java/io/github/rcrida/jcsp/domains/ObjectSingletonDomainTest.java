package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSingletonDomainTest {

    @Test
    void toList_returnsSingletonList() {
        var domain = new ObjectSingletonDomain<>("x");
        assertThat(domain.toList()).containsExactly("x");
    }

    @Test
    void stream_returnsSingletonStream() {
        var domain = new ObjectSingletonDomain<>("x");
        assertThat(domain.stream()).containsExactly("x");
    }

    @Test
    void singleValue_returnsTheValue() {
        assertThat(new ObjectSingletonDomain<>("x").singleValue()).contains("x");
    }

    @Test
    void contains_value() {
        assertThat(new ObjectSingletonDomain<>(42).contains(42)).isTrue();
    }

    @Test
    void doesNotContain_otherValue() {
        assertThat(new ObjectSingletonDomain<>(42).contains(99)).isFalse();
    }

    @Test
    void isSingleton() {
        assertThat(new ObjectSingletonDomain<>("x").isSingleton()).isTrue();
    }

    @Test
    void testToString() {
        assertThat(new ObjectSingletonDomain<>("x").toString()).isEqualTo("{x}");
    }

    @Test
    void equals_sameValue() {
        assertThat(new ObjectSingletonDomain<>("x").equals(new ObjectSingletonDomain<>("x"))).isTrue();
    }

    @Test
    void equals_sameInstance() {
        var domain = new ObjectSingletonDomain<>("x");
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentValue() {
        assertThat(new ObjectSingletonDomain<>("x").equals(new ObjectSingletonDomain<>("y"))).isFalse();
    }

    @Test
    void equals_differentType() {
        assertThat(new ObjectSingletonDomain<>("x").equals("x")).isFalse();
    }

    @Test
    void hashCode_consistent() {
        assertThat(new ObjectSingletonDomain<>("x").hashCode()).isEqualTo(new ObjectSingletonDomain<>("x").hashCode());
    }

    @Test
    void isEmpty_alwaysFalse() {
        assertThat(new ObjectSingletonDomain<>("x").isEmpty()).isFalse();
    }

    @Test
    void size_alwaysOne() {
        assertThat(new ObjectSingletonDomain<>("x").size()).isEqualTo(1);
    }

    // ── Cross-type equality symmetry with SetDomain (ObjectSingletonDomain is the one DiscreteDomain
    // implementor that isn't a SetDomain, so both directions must agree) ────

    @Test
    void equals_singletonSetDomain_bothDirections() {
        ObjectSingletonDomain<String> singleton = new ObjectSingletonDomain<>("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("x"));

        assertThat(singleton.equals(set)).isTrue();
        assertThat(set.equals(singleton)).isTrue();
    }

    @Test
    void equals_differentValueSetDomain_bothDirectionsFalse() {
        ObjectSingletonDomain<String> singleton = new ObjectSingletonDomain<>("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void equals_multiValueSetDomain_bothDirectionsFalse() {
        ObjectSingletonDomain<String> singleton = new ObjectSingletonDomain<>("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("x", "y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void hashCode_matchesSingletonSetDomain() {
        assertThat(new ObjectSingletonDomain<>("x").hashCode())
                .isEqualTo(new ObjectSetDomain<>(Set.of("x")).hashCode());
    }

    // ── toBuilder() (reuses DiscreteDomain.DiscreteDomainBuilder for correct delete-to-empty
    // semantics) ──

    @Test
    void toBuilder_deletingTheValue_buildsEmptyDomain() {
        DiscreteDomain<String> result = new ObjectSingletonDomain<>("x").toBuilder().delete("x").build();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void toBuilder_deletingADifferentValue_leavesItUnchanged() {
        DiscreteDomain<String> result = new ObjectSingletonDomain<>("x").toBuilder().delete("y").build();
        assertThat(result.singleValue()).contains("x");
    }

    // ── DiscreteDomain.DiscreteDomainBuilder.build() collapses to the cheapest representation --
    // every SetDomain implementor (IntRangeDomain, ObjectSetDomain, ...) routes toBuilder() through
    // the same shared builder, so narrowing any of them exercises this identically. ──

    @Test
    void discreteDomainBuilder_narrowedToOneValue_buildsObjectSingletonDomain() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 2).toBuilder().delete(2).build();

        assertThat(narrowed).isInstanceOf(ObjectSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains(1);
    }

    @Test
    void discreteDomainBuilder_narrowedToTwoValues_staysObjectSetDomain() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 3).toBuilder().delete(3).build();

        assertThat(narrowed).isInstanceOf(ObjectSetDomain.class);
    }

    @Test
    void discreteDomainBuilder_fromExistingObjectSetDomain_narrowedToOneValue() {
        DiscreteDomain<String> narrowed = new ObjectSetDomain<>(Set.of("x", "y")).toBuilder().delete("y").build();

        assertThat(narrowed).isInstanceOf(ObjectSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains("x");
    }

    @Test
    void discreteDomainBuilder_neverAddedAnyValue_buildsObjectEmptyDomain() {
        DiscreteDomain<Object> built = DiscreteDomain.builder().build();

        assertThat(built).isInstanceOf(ObjectEmptyDomain.class);
        assertThat(built.isEmpty()).isTrue();
    }

    @Test
    void discreteDomainBuilder_value_addsOneAtATime() {
        DiscreteDomain<String> built = DiscreteDomain.<String>builder().value("x").value("y").build();

        assertThat(built).isInstanceOf(ObjectSetDomain.class);
        assertThat(built.toList()).containsExactly("x", "y");
    }

    @Test
    void discreteDomainBuilder_values_addsACollection() {
        DiscreteDomain<String> built = DiscreteDomain.<String>builder().values(List.of("x", "y")).build();

        assertThat(built.toList()).containsExactly("x", "y");
    }
}
