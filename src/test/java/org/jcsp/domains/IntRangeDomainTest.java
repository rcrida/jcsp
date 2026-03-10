package org.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntRangeDomainTest {
    @Test
    void constructInvalid() {
        assertThatThrownBy(() -> new IntRangeDomain(10, 5))
            .isInstanceOf(AssertionError.class)
            .hasMessage("minInclusive (10) must be less than or equal to maxInclusive (5)");
    }

    @Test
    void contains() {
        IntRangeDomain domain = new IntRangeDomain(5, 10);
        assertThat(domain.contains(5)).isTrue();
        assertThat(domain.contains(10)).isTrue();
        assertThat(domain.contains(4)).isFalse();
        assertThat(domain.contains(11)).isFalse();
    }

    @Test
    void containsNull() {
        IntRangeDomain domain = new IntRangeDomain(5, 10);
        assertThat(domain.contains(null)).isFalse();
    }

    @Test
    void containsInvalid() {
        IntRangeDomain domain = new IntRangeDomain(5, 10);
        assertThat(domain.contains(5.5)).isFalse();
        assertThat(domain.contains("5")).isFalse();
    }
}
