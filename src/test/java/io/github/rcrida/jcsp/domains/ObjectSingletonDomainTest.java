package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSingletonDomainTest {

    @Test
    void toList_returnsSingletonList() {
        var domain = new ObjectSingletonDomain("x");
        assertThat(domain.toList()).containsExactly("x");
    }

    @Test
    void stream_returnsSingletonStream() {
        var domain = new ObjectSingletonDomain("x");
        assertThat(domain.stream()).containsExactly("x");
    }

    @Test
    void singleValue_returnsTheValue() {
        assertThat(new ObjectSingletonDomain("x").singleValue()).contains("x");
    }

    @Test
    void contains_value() {
        assertThat(new ObjectSingletonDomain(42).contains(42)).isTrue();
    }

    @Test
    void doesNotContain_otherValue() {
        assertThat(new ObjectSingletonDomain(42).contains(99)).isFalse();
    }

    @Test
    void isSingleton() {
        assertThat(new ObjectSingletonDomain("x").isSingleton()).isTrue();
    }

    @Test
    void testToString() {
        assertThat(new ObjectSingletonDomain("x").toString()).isEqualTo("{x}");
    }

    @Test
    void equals_sameValue() {
        assertThat(new ObjectSingletonDomain("x").equals(new ObjectSingletonDomain("x"))).isTrue();
    }

    @Test
    void equals_sameInstance() {
        var domain = new ObjectSingletonDomain("x");
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentValue() {
        assertThat(new ObjectSingletonDomain("x").equals(new ObjectSingletonDomain("y"))).isFalse();
    }

    @Test
    void equals_differentType() {
        assertThat(new ObjectSingletonDomain("x").equals("x")).isFalse();
    }

    @Test
    void hashCode_consistent() {
        assertThat(new ObjectSingletonDomain("x").hashCode()).isEqualTo(new ObjectSingletonDomain("x").hashCode());
    }

    @Test
    void isEmpty_alwaysFalse() {
        assertThat(new ObjectSingletonDomain("x").isEmpty()).isFalse();
    }

    @Test
    void size_alwaysOne() {
        assertThat(new ObjectSingletonDomain("x").size()).isEqualTo(1);
    }

    // ── Cross-type equality symmetry with SetDomain (ObjectSingletonDomain is the one DiscreteDomain
    // implementor that isn't a SetDomain, so both directions must agree) ────

    @Test
    void equals_singletonSetDomain_bothDirections() {
        ObjectSingletonDomain singleton = new ObjectSingletonDomain("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("x"));

        assertThat(singleton.equals(set)).isTrue();
        assertThat(set.equals(singleton)).isTrue();
    }

    @Test
    void equals_differentValueSetDomain_bothDirectionsFalse() {
        ObjectSingletonDomain singleton = new ObjectSingletonDomain("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void equals_multiValueSetDomain_bothDirectionsFalse() {
        ObjectSingletonDomain singleton = new ObjectSingletonDomain("x");
        ObjectSetDomain<String> set = new ObjectSetDomain<>(Set.of("x", "y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void hashCode_matchesSingletonSetDomain() {
        assertThat(new ObjectSingletonDomain("x").hashCode())
                .isEqualTo(new ObjectSetDomain<>(Set.of("x")).hashCode());
    }

    // ── toBuilder() (reuses SetDomain.DefaultBuilder for correct delete-to-empty semantics) ──

    @Test
    void toBuilder_deletingTheValue_buildsEmptyDomain() {
        DiscreteDomain<Object> result = new ObjectSingletonDomain("x").toBuilder().delete("x").build();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void toBuilder_deletingADifferentValue_leavesItUnchanged() {
        DiscreteDomain<Object> result = new ObjectSingletonDomain("x").toBuilder().delete("y").build();
        assertThat(result.singleValue()).contains("x");
    }

    // ── SetDomain.DefaultBuilder.build() returns ObjectSingletonDomain once narrowed to one value ──
    // IntRangeDomain (unlike ObjectSetDomain/NumericSetDomain, which have their own Lombok
    // @Builder(toBuilder = true)) has no toBuilder()/build() of its own, so narrowing it via
    // toBuilder() genuinely exercises SetDomain.DefaultBuilder.

    @Test
    void defaultBuilder_narrowedToOneValue_buildsObjectSingletonDomain() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 2).toBuilder().delete(2).build();

        assertThat(narrowed).isInstanceOf(ObjectSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains(1);
    }

    @Test
    void defaultBuilder_narrowedToTwoValues_staysObjectSetDomain() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 3).toBuilder().delete(3).build();

        assertThat(narrowed).isInstanceOf(ObjectSetDomain.class);
    }

    // ── ObjectSetDomain's own builder override (Lombok's @Builder(toBuilder = true) shadows
    // SetDomain's default, so it needs its own matching build() override) ──

    @Test
    void domainObjectSetBuilder_narrowedToOneValue_buildsObjectSingletonDomain() {
        DiscreteDomain<String> narrowed = new ObjectSetDomain<>(Set.of("x", "y")).toBuilder().delete("y").build();

        assertThat(narrowed).isInstanceOf(ObjectSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains("x");
    }

    @Test
    void domainObjectSetBuilder_narrowedToTwoValues_staysObjectSetDomain() {
        DiscreteDomain<String> narrowed =
                new ObjectSetDomain<>(Set.of("x", "y", "z")).toBuilder().delete("z").build();

        assertThat(narrowed).isInstanceOf(ObjectSetDomain.class);
        assertThat(narrowed.toList()).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void objectSetDomainBuilder_neverAddedAnyValue_buildsObjectEmptyDomain() {
        // Lombok's @Singular field starts null (not an empty collection) until first added --
        // build() must tolerate a builder nothing was ever added to. A distinct code path from
        // DiscreteDomain.of()'s empty case, which always routes through values(List.of()) and so
        // never leaves the field null, even though both now produce the same ObjectEmptyDomain.
        DiscreteDomain<Object> built = ObjectSetDomain.builder().build();

        assertThat(built).isInstanceOf(ObjectEmptyDomain.class);
        assertThat(built.isEmpty()).isTrue();
    }
}
