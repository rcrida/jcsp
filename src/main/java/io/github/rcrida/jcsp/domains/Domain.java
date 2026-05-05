package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Represents a collection of elements that defines the domain of values that a variable can take.
 */
public interface Domain {
    boolean contains(@Nullable Object value);
    boolean isEmpty();
    int size();
    Stream<?> stream();
    Builder toBuilder();

    interface Builder {
        Builder delete(@NonNull Object value);
        Domain build();
    }
}
