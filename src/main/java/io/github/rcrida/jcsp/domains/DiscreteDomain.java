package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link Domain} whose values are enumerable. Extends the base {@code Domain} contract with
 * {@link #stream()}, {@link #toList()}, and {@link #toBuilder()} — methods that
 * require individual values to be addressable. All concrete domain implementations except
 * {@link IntervalDomain} implement this interface.
 */
public interface DiscreteDomain<T> extends Domain<T> {
    Stream<T> stream();
    default List<T> toList() { return stream().toList(); }
    Builder<T> toBuilder();

    @Override
    default Optional<T> singleValue() { return isSingleton() ? stream().findFirst() : Optional.empty(); }

    interface Builder<T> {
        Builder<T> delete(@NonNull Object value);
        DiscreteDomain<T> build();
    }
}
