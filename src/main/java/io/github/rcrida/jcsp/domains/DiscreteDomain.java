package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * DiscreteDomainBuilder#build}. The numeric analogue is {@link NumericDiscreteDomain#of}.
     */
    @SafeVarargs
    static <T> DiscreteDomain<T> of(T... values) {
        return DiscreteDomain.<T>builder().values(List.of(values)).build();
    }

    /**
     * A fresh, empty builder — the entry point for building a domain from scratch value by value,
     * as opposed to {@link #toBuilder()}, which narrows an already-built domain.
     */
    static <T> DiscreteDomainBuilder<T> builder() {
        return new DiscreteDomainBuilder<>(Set.of());
    }

    interface Builder<T> {
        Builder<T> delete(@NonNull Object value);
        DiscreteDomain<T> build();
    }

    /**
     * The single hand-written builder shared by every non-numeric discrete domain — {@link
     * ObjectSetDomain}, {@link ObjectSingletonDomain}, and {@link ObjectEmptyDomain} all route
     * their {@link #toBuilder()} through this class, so the empty/singleton/many collapsing in
     * {@link #build} lives in exactly one place. Replaces two previously-separate builders (a
     * Lombok-generated one on {@link ObjectSetDomain}, and a hand-written {@code
     * DiscreteSetDomain.DefaultBuilder}) that used to duplicate this same collapsing logic and repeatedly
     * fought Lombok's builder-class-naming/return-type conventions.
     */
    final class DiscreteDomainBuilder<T> implements Builder<T> {
        private final Set<T> mutableValues = new LinkedHashSet<>();

        DiscreteDomainBuilder(Set<T> initial) {
            mutableValues.addAll(initial);
        }

        public DiscreteDomainBuilder<T> value(T value) {
            mutableValues.add(value);
            return this;
        }

        public DiscreteDomainBuilder<T> values(Collection<? extends T> values) {
            mutableValues.addAll(values);
            return this;
        }

        @Override
        public DiscreteDomainBuilder<T> delete(@NonNull Object value) {
            mutableValues.remove(value);
            return this;
        }

        @Override
        public DiscreteDomain<T> build() {
            if (mutableValues.isEmpty()) {
                return ObjectEmptyDomain.instance();
            }
            if (mutableValues.size() == 1) {
                return new ObjectSingletonDomain<>(mutableValues.iterator().next());
            }
            return new ObjectSetDomain<>(Collections.unmodifiableSet(new LinkedHashSet<>(mutableValues)));
        }
    }
}
