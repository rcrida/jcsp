package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSetDomainTest {

    @Test
    void testToString() {
        assertThat(DiscreteDomain.of("a", "b", "c").toString()).isEqualTo("{a, b, c}");
    }
}
