package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * Checked against any {@link DiscreteDomain}, not just another {@link SetDomain} -- {@link
     * ObjectSingletonDomain} implements {@link DiscreteDomain} directly (not this interface, to avoid
     * materialising a throwaway {@code Set} on every hot-path check) but must still compare equal to
     * a {@link SetDomain} holding the same single value, and vice versa; narrowing this check to
     * {@code SetDomain} would make that comparison asymmetric depending on which side calls {@code
     * equals}, violating {@link Object#equals}'s contract. The common case -- comparing two {@link
     * SetDomain}s -- stays on the direct {@link Set#equals} path; the {@code stream}/{@code allMatch}
     * fallback (needed since a non-{@link SetDomain} {@link DiscreteDomain} has no {@link #values()}
     * to compare against directly) is only reached for the one implementor that isn't a {@link
     * SetDomain}, {@link ObjectSingletonDomain}.
     */
    static boolean domainEquals(SetDomain<?> self, Object o) {
        if (self == o) return true;
        if (o instanceof SetDomain<?> setOther) {
            return self.values().equals(setOther.values());
        }
        return o instanceof DiscreteDomain<?> other
                && self.size() == other.size() && self.values().stream().allMatch(other::contains);
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

        /**
         * Returns an {@link ObjectSingletonDomain} when exactly one value remains, instead of always
         * building an {@link ObjectSetDomain} -- so any propagator narrowing a domain down to a
         * single value (not just search assigning one explicitly) gets the cheaper representation
         * for every subsequent read. {@link ObjectSingletonDomain#equals} treats the two as
         * interchangeable, so this is transparent to anything comparing domains.
         */
        @Override
        @SuppressWarnings("unchecked")
        public DiscreteDomain<T> build() {
            if (mutableValues.size() == 1) {
                return (DiscreteDomain<T>) new ObjectSingletonDomain(mutableValues.iterator().next());
            }
            return ObjectSetDomain.<T>builder().values(mutableValues).build();
        }
    }
}
