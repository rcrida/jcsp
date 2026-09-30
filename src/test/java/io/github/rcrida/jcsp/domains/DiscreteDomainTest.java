package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscreteDomainTest {

    @Test
    void of_oneValue_returnsObjectSingletonDomain() {
        DiscreteDomain<Object> domain = DiscreteDomain.of("x");

        assertThat(domain).isInstanceOf(ObjectSingletonDomain.class);
        assertThat(domain.singleValue()).contains("x");
    }

    @Test
    void of_multipleValues_returnsObjectSetDomain() {
        DiscreteDomain<Object> domain = DiscreteDomain.of("x", "y", "z");

        assertThat(domain).isInstanceOf(ObjectSetDomain.class);
        assertThat(domain.toList()).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    void of_noValues_returnsObjectEmptyDomain() {
        DiscreteDomain<Object> domain = DiscreteDomain.of();

        assertThat(domain).isInstanceOf(ObjectEmptyDomain.class);
        assertThat(domain.isEmpty()).isTrue();
    }

    @Test
    void build_thenMutated_throwsAssertionError() {
        DiscreteDomain.DiscreteDomainBuilder<Object> builder =
                DiscreteDomain.<Object>builder().value("x").value("y");
        builder.build();

        assertThatThrownBy(() -> builder.delete("x")).isInstanceOf(AssertionError.class);
    }
}
