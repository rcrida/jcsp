package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Represents the set of values a variable may take. The base contract covers only operations that
 * are meaningful for both enumerable and non-enumerable (e.g. continuous interval) domains.
 *
 * <p>Enumerable domains implement {@link DiscreteDomain}, which adds {@code stream()},
 * {@code toList()}, and {@code toBuilder()}. Continuous domains implement {@link BoundedDomain},
 * which adds {@code getMin()}, {@code getMax()}, and {@code withBounds()}.
 */
public interface Domain<T> {
    boolean contains(@Nullable Object value);
    boolean isEmpty();
    int size();
    default boolean isSingleton() { return size() == 1; }
    Optional<T> singleValue();
}
