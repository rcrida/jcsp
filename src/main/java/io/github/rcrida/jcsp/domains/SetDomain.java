package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A {@link DiscreteDomain} backed by a {@link Set}. Provides default implementations of all
 * {@link DiscreteDomain} methods in terms of {@link #values()}, so implementors only need to
 * supply that single method.
 */
public interface SetDomain<T> extends DiscreteDomain<T> {

    Set<T> values();

    @Override
    default boolean contains(@Nullable Object value) {
        return value != null && values().contains(value);
    }

    @Override
    default boolean isEmpty() {
        return values().isEmpty();
    }

    @Override
    default int size() {
        return values().size();
    }

    @Override
    default Stream<T> stream() {
        return values().stream();
    }

    @Override
    default List<T> toList() {
        return List.copyOf(values());
    }

    @Override
    default Builder<T> toBuilder() {
        return new DefaultBuilder<>(values());
    }

    /**
     * Overrides {@link DiscreteDomain}'s {@code stream().findFirst()} default: {@link #values()}
     * gives direct access to the one element a singleton domain holds, so this is a plain iterator
     * grab rather than building a whole Stream pipeline -- found via JFR profiling to be, across its
     * many call sites (any code checking whether a variable is already decided), one of the largest
     * single allocation sources in a search-heavy set-CP solve.
     */
    @Override
    default Optional<T> singleValue() {
        return isSingleton() ? Optional.of(values().iterator().next()) : Optional.empty();
    }

    static boolean domainEquals(SetDomain<?> self, Object o) {
        if (self == o) return true;
        if (!(o instanceof SetDomain<?> other)) return false;
        return self.values().equals(other.values());
    }

    static int domainHashCode(SetDomain<?> self) {
        return self.values().hashCode();
    }

    class DefaultBuilder<T> implements DiscreteDomain.Builder<T> {
        private final Set<T> mutableValues;

        DefaultBuilder(Set<T> initial) {
            this.mutableValues = new HashSet<>(initial);
        }

        @Override
        public Builder<T> delete(@NonNull Object value) {
            mutableValues.remove(value);
            return this;
        }

        @Override
        public DiscreteDomain<T> build() {
            return DomainObjectSet.<T>builder().values(mutableValues).build();
        }
    }
}
