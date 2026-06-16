package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DomainTest {

    /** Minimal DiscreteDomain implementation exercising the default toList(), isSingleton(), singleValue() methods. */
    @SuppressWarnings("rawtypes")
    static final DiscreteDomain DOMAIN = new DiscreteDomain() {
        @Override public boolean contains(Object value) { return List.of(1, 2, 3).contains(value); }
        @Override public boolean isEmpty() { return false; }
        @Override public int size() { return 3; }
        @Override public Stream<?> stream() { return Stream.of(1, 2, 3); }
        @Override public Builder toBuilder() { throw new UnsupportedOperationException(); }
    };

    @SuppressWarnings("rawtypes")
    static final DiscreteDomain SINGLETON_DOMAIN = new DiscreteDomain() {
        @Override public boolean contains(Object value) { return List.of(1).contains(value); }
        @Override public boolean isEmpty() { return false; }
        @Override public int size() { return 1; }
        @Override public Stream<?> stream() { return Stream.of(1); }
        @Override public Builder toBuilder() { throw new UnsupportedOperationException(); }
    };

    @Test
    void defaultToListDelegatesToStream() {
        assertThat(DOMAIN.toList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void isSingleton_falseForMultiValueDomain() {
        assertThat(DOMAIN.isSingleton()).isFalse();
        assertThat(DOMAIN.singleValue()).isEmpty();
    }

    @Test
    void isSingleton_trueForSingleValueDomain() {
        assertThat(SINGLETON_DOMAIN.isSingleton()).isTrue();
        assertThat(SINGLETON_DOMAIN.singleValue()).contains(1);
    }
}
