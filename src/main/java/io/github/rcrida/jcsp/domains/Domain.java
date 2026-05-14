package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Represents a collection of elements that defines the domain of values that a variable can take.
 */
public interface Domain<T> {
    boolean contains(@Nullable Object value);
    boolean isEmpty();
    int size();
    Stream<T> stream();
    default List<T> toList() {
        return stream().toList();
    }
    Builder<T> toBuilder();

    interface Builder<T> {
        Builder<T> delete(@NonNull Object value);
        Domain<T> build();
    }
}
