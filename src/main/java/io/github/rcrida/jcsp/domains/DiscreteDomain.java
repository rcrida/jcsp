package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link Domain} whose values are enumerable. Extends the base {@link Domain} contract with
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

    /**
     * Builds a discrete domain from explicit values, collapsing to an {@link ObjectSingletonDomain}
     * for exactly one value or an {@link ObjectSetDomain} otherwise -- see {@link
     * ObjectSetDomain.ObjectSetDomainBuilder#build}. The numeric analogue is {@link
     * NumericDiscreteDomain#of}.
     */
    @SafeVarargs
    static <T> DiscreteDomain<T> of(T... values) {
        return ObjectSetDomain.<T>builder().values(List.of(values)).build();
    }

    interface Builder<T> {
        Builder<T> delete(@NonNull Object value);
        DiscreteDomain<T> build();
    }
}
