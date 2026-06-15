package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
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

    /** @return true if this domain contains exactly one value */
    default boolean isSingleton() {
        return size() == 1;
    }

    /** @return the single value in this domain, or empty if it is not a singleton */
    default Optional<T> singleValue() {
        return isSingleton() ? stream().findFirst() : Optional.empty();
    }

    interface Builder<T> {
        Builder<T> delete(@NonNull Object value);
        Domain<T> build();
    }
}
