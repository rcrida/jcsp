package io.github.rcrida.jcsp.solver;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyListTest {

    @Test
    void stream_returnsElements() {
        var lazyList = new LazyList<>(Stream.of(1, 2, 3));
        assertThat(lazyList.stream().toList()).containsExactly(1, 2, 3);
    }

    @Test
    void stream_lazyCachesOnDemand() {
        var callCount = new AtomicInteger();
        var lazyList = new LazyList<>(Stream.of(1, 2, 3).peek(v -> callCount.incrementAndGet()));

        assertThat(lazyList.stream().findFirst()).contains(1);
        assertThat(callCount.get()).isEqualTo(1);

        assertThat(lazyList.stream().toList()).containsExactly(1, 2, 3);
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void stream_replaysFromCache() {
        var callCount = new AtomicInteger();
        var lazyList = new LazyList<>(Stream.of(1, 2, 3).peek(v -> callCount.incrementAndGet()));

        assertThat(lazyList.stream().toList()).containsExactly(1, 2, 3);
        assertThat(callCount.get()).isEqualTo(3);

        assertThat(lazyList.stream().toList()).containsExactly(1, 2, 3);
        assertThat(callCount.get()).isEqualTo(3);
    }
}
