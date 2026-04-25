package org.jcsp.domains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumDomainTest {
    enum Colour {
        RED, GREEN, BLUE
    }

    EnumDomain domain = EnumDomain.allOf(Colour.class);

    @ParameterizedTest
    @EnumSource(Colour.class)
    void contains(Colour colour) {
        assertThat(domain.contains(colour)).isTrue();
    }

    @Test
    void containsNull() {
        assertThat(domain.contains(null)).isFalse();
    }

    @Test
    void containsInvalid() {
        assertThat(domain.contains("not a valid colour")).isFalse();
    }

    @Test
    void stream() {
        assertThat((Stream<Colour>) domain.stream()).containsOnly(Colour.RED, Colour.GREEN, Colour.BLUE);
    }
}
