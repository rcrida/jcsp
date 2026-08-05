package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The numeric analogue of {@link ObjectSingletonDomain}: a domain holding exactly one {@link Number}
 * value, produced by {@link NumericDomain#withBounds}'s default method whenever bounds-narrowing a
 * {@link NumericDomain} (e.g. {@link IntRangeDomain}, {@link NumericSetDomain}) leaves exactly one
 * value -- the same allocation-avoidance {@link ObjectSingletonDomain} already gives non-numeric {@link
 * SetDomain}-backed domains, without materialising a throwaway {@code Set.of(value)} or {@link
 * NumericSetDomain} wrapper. Implements {@link NumericDiscreteDomain} (unlike {@link
 * ObjectSingletonDomain}, which implements neither {@link NumericDomain} nor {@link
 * NumericDiscreteDomain}) specifically so it satisfies {@link NumericDomain#withBounds}'s own
 * return-type contract -- see {@link ObjectSingletonDomain}'s Javadoc for why plain {@link
 * ObjectSingletonDomain} can't be used here instead.
 * <p>
 * {@link #equals}/{@link #hashCode} follow {@link ObjectSingletonDomain}'s same cross-type contract:
 * equal to any {@link DiscreteDomain} (numeric or not) holding the same single value.
 */
public record NumericSingletonDomain<N extends Number>(@NonNull N value) implements NumericDiscreteDomain<N> {
    @Override
    public N getMin() {
        return value;
    }

    @Override
    public N getMax() {
        return value;
    }

    @Override
    public boolean contains(@Nullable Object v) {
        return value.equals(v);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Stream<N> stream() {
        return Stream.of(value);
    }

    @Override
    public List<N> toList() {
        return List.of(value);
    }

    @Override
    public Optional<N> singleValue() {
        return Optional.of(value);
    }

    @Override
    public Builder<N> toBuilder() {
        return new DiscreteDomain.DiscreteDomainBuilder<>(Set.of(value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DiscreteDomain<?> other && other.size() == 1 && other.contains(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "{" + value + "}";
    }
}
