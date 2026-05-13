package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BooleanDomainTest {

    @Test
    void containsTrue() {
        assertThat(BooleanDomain.INSTANCE.contains(true)).isTrue();
    }

    @Test
    void containsFalse() {
        assertThat(BooleanDomain.INSTANCE.contains(false)).isTrue();
    }

    @Test
    void doesNotContainNull() {
        assertThat(BooleanDomain.INSTANCE.contains(null)).isFalse();
    }

    @Test
    void doesNotContainNonBoolean() {
        assertThat(BooleanDomain.INSTANCE.contains(1)).isFalse();
        assertThat(BooleanDomain.INSTANCE.contains("true")).isFalse();
    }

    @Test
    void size() {
        assertThat(BooleanDomain.INSTANCE.size()).isEqualTo(2);
    }

    @Test
    void isNotEmpty() {
        assertThat(BooleanDomain.INSTANCE.isEmpty()).isFalse();
    }
}
