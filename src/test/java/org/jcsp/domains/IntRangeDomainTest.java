package org.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntRangeDomainTest {
    @Test
    void constructInvalid() {
        assertThatThrownBy(() -> IntRangeDomain.of(10, 5))
            .isInstanceOf(AssertionError.class)
            .hasMessage("minInclusive (10) must be less than or equal to maxInclusive (5)");
    }

    @Test
    void contains() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5)).isTrue();
        assertThat(domain.contains(10)).isTrue();
        assertThat(domain.contains(4)).isFalse();
        assertThat(domain.contains(11)).isFalse();
    }

    @Test
    void containsNull() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(null)).isFalse();
    }

    @Test
    void containsInvalid() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5.5)).isFalse();
        assertThat(domain.contains("5")).isFalse();
    }

    @Test
    void stream() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat((Stream<Integer>) domain.stream()).containsOnly(5, 6, 7, 8, 9, 10);
    }
}
