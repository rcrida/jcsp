package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SingletonDomainTest {

    @Test
    void toList_returnsSingletonList() {
        var domain = new SingletonDomain("x");
        assertThat(domain.toList()).containsExactly("x");
    }

    @Test
    void stream_returnsSingletonStream() {
        var domain = new SingletonDomain("x");
        assertThat(domain.stream()).containsExactly("x");
    }

    @Test
    void singleValue_returnsTheValue() {
        assertThat(new SingletonDomain("x").singleValue()).contains("x");
    }

    @Test
    void contains_value() {
        assertThat(new SingletonDomain(42).contains(42)).isTrue();
    }

    @Test
    void doesNotContain_otherValue() {
        assertThat(new SingletonDomain(42).contains(99)).isFalse();
    }

    @Test
    void isSingleton() {
        assertThat(new SingletonDomain("x").isSingleton()).isTrue();
    }

    @Test
    void equals_sameValue() {
        assertThat(new SingletonDomain("x").equals(new SingletonDomain("x"))).isTrue();
    }

    @Test
    void equals_sameInstance() {
        var domain = new SingletonDomain("x");
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentValue() {
        assertThat(new SingletonDomain("x").equals(new SingletonDomain("y"))).isFalse();
    }

    @Test
    void equals_differentType() {
        assertThat(new SingletonDomain("x").equals("x")).isFalse();
    }

    @Test
    void hashCode_consistent() {
        assertThat(new SingletonDomain("x").hashCode()).isEqualTo(new SingletonDomain("x").hashCode());
    }

    @Test
    void isEmpty_alwaysFalse() {
        assertThat(new SingletonDomain("x").isEmpty()).isFalse();
    }

    @Test
    void size_alwaysOne() {
        assertThat(new SingletonDomain("x").size()).isEqualTo(1);
    }

    // ── Cross-type equality symmetry with SetDomain (SingletonDomain is the one DiscreteDomain
    // implementor that isn't a SetDomain, so both directions must agree) ────

    @Test
    void equals_singletonSetDomain_bothDirections() {
        SingletonDomain singleton = new SingletonDomain("x");
        DomainObjectSet<String> set = new DomainObjectSet<>(Set.of("x"));

        assertThat(singleton.equals(set)).isTrue();
        assertThat(set.equals(singleton)).isTrue();
    }

    @Test
    void equals_differentValueSetDomain_bothDirectionsFalse() {
        SingletonDomain singleton = new SingletonDomain("x");
        DomainObjectSet<String> set = new DomainObjectSet<>(Set.of("y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void equals_multiValueSetDomain_bothDirectionsFalse() {
        SingletonDomain singleton = new SingletonDomain("x");
        DomainObjectSet<String> set = new DomainObjectSet<>(Set.of("x", "y"));

        assertThat(singleton.equals(set)).isFalse();
        assertThat(set.equals(singleton)).isFalse();
    }

    @Test
    void hashCode_matchesSingletonSetDomain() {
        assertThat(new SingletonDomain("x").hashCode())
                .isEqualTo(new DomainObjectSet<>(Set.of("x")).hashCode());
    }

    // ── toBuilder() (reuses SetDomain.DefaultBuilder for correct delete-to-empty semantics) ──

    @Test
    void toBuilder_deletingTheValue_buildsEmptyDomain() {
        DiscreteDomain<Object> result = new SingletonDomain("x").toBuilder().delete("x").build();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void toBuilder_deletingADifferentValue_leavesItUnchanged() {
        DiscreteDomain<Object> result = new SingletonDomain("x").toBuilder().delete("y").build();
        assertThat(result.singleValue()).contains("x");
    }

    // ── SetDomain.DefaultBuilder.build() returns SingletonDomain once narrowed to one value ──
    // IntRangeDomain (unlike DomainObjectSet/NumericSetDomain, which have their own Lombok
    // @Builder(toBuilder = true)) has no toBuilder()/build() of its own, so narrowing it via
    // toBuilder() genuinely exercises SetDomain.DefaultBuilder.

    @Test
    void defaultBuilder_narrowedToOneValue_buildsSingletonDomain() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 2).toBuilder().delete(2).build();

        assertThat(narrowed).isInstanceOf(SingletonDomain.class);
        assertThat(narrowed.singleValue()).contains(1);
    }

    @Test
    void defaultBuilder_narrowedToTwoValues_staysDomainObjectSet() {
        DiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 3).toBuilder().delete(3).build();

        assertThat(narrowed).isInstanceOf(DomainObjectSet.class);
    }

    // ── DomainObjectSet's own builder override (Lombok's @Builder(toBuilder = true) shadows
    // SetDomain's default, so it needs its own matching build() override) ──

    @Test
    void domainObjectSetBuilder_narrowedToOneValue_buildsSingletonDomain() {
        DiscreteDomain<String> narrowed = new DomainObjectSet<>(Set.of("x", "y")).toBuilder().delete("y").build();

        assertThat(narrowed).isInstanceOf(SingletonDomain.class);
        assertThat(narrowed.singleValue()).contains("x");
    }

    @Test
    void domainObjectSetBuilder_narrowedToTwoValues_staysDomainObjectSet() {
        DiscreteDomain<String> narrowed =
                new DomainObjectSet<>(Set.of("x", "y", "z")).toBuilder().delete("z").build();

        assertThat(narrowed).isInstanceOf(DomainObjectSet.class);
        assertThat(narrowed.toList()).containsExactlyInAnyOrder("x", "y");
    }
}
