package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AssignedDomainTest {

    @Test
    void values_returnsSingletonSet() {
        var domain = new AssignedDomain("x");
        assertThat(domain.values()).containsExactly("x");
    }

    @Test
    void contains_value() {
        assertThat(new AssignedDomain(42).contains(42)).isTrue();
    }

    @Test
    void doesNotContain_otherValue() {
        assertThat(new AssignedDomain(42).contains(99)).isFalse();
    }

    @Test
    void isSingleton() {
        assertThat(new AssignedDomain("x").isSingleton()).isTrue();
    }

    @Test
    void equals_sameValue() {
        assertThat(new AssignedDomain("x").equals(new AssignedDomain("x"))).isTrue();
    }

    @Test
    void equals_sameInstance() {
        var domain = new AssignedDomain("x");
        assertThat(domain.equals(domain)).isTrue();
    }

    @Test
    void equals_differentValue() {
        assertThat(new AssignedDomain("x").equals(new AssignedDomain("y"))).isFalse();
    }

    @Test
    void equals_differentType() {
        assertThat(new AssignedDomain("x").equals("x")).isFalse();
    }

    @Test
    void hashCode_consistent() {
        assertThat(new AssignedDomain("x").hashCode()).isEqualTo(new AssignedDomain("x").hashCode());
    }
}
