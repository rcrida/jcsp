package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The empty analogue of {@link ObjectSingletonDomain}: the domain holding no values at all,
 * produced by {@link ObjectSetDomain.ObjectSetDomainBuilder#build} and {@link
 * SetDomain.DefaultBuilder#build} whenever narrowing empties a {@link SetDomain}-backed domain
 * completely. Unlike {@link ObjectSingletonDomain}, which needs one instance per value, there is
 * exactly one possible empty domain, so the single underlying instance is reused rather than
 * allocated per call -- purely for symmetry with {@link ObjectSingletonDomain}/{@link
 * NumericEmptyDomain}, not because empty-domain construction is a measured hot path (it isn't: an
 * empty domain is checked once with {@link #isEmpty()} and discarded immediately by every caller,
 * never read repeatedly the way a live domain is). Genuinely generic in {@code T}, unlike {@link
 * ObjectSingletonDomain} (which is fixed to {@code Object} since its single value's type can't be
 * recovered generically) -- there's no value here to lose the type of, so {@link #instance()} can
 * hand back a properly-typed {@code ObjectEmptyDomain<T>} with the unchecked cast contained
 * entirely inside that one method, the same shape as {@link NumericEmptyDomain#instance}.
 * <p>
 * A plain final class rather than a record, unlike every other domain type: a {@code public
 * record}'s canonical constructor can't be more restrictive than the record itself (JLS
 * 8.10.4.2), so a record couldn't actually enforce the one-instance property this class exists
 * for; a private constructor on a plain class can.
 */
public final class ObjectEmptyDomain<T> implements DiscreteDomain<T> {
    private static final ObjectEmptyDomain<?> INSTANCE = new ObjectEmptyDomain<>();

    private ObjectEmptyDomain() {}

    @SuppressWarnings("unchecked")
    public static <T> ObjectEmptyDomain<T> instance() {
        return (ObjectEmptyDomain<T>) INSTANCE;
    }

    @Override
    public boolean contains(@Nullable Object v) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Stream<T> stream() {
        return Stream.empty();
    }

    @Override
    public List<T> toList() {
        return List.of();
    }

    @Override
    public Optional<T> singleValue() {
        return Optional.empty();
    }

    @Override
    public Builder<T> toBuilder() {
        return new SetDomain.DefaultBuilder<>(Set.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DiscreteDomain<?> other && other.isEmpty();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "{}";
    }
}
