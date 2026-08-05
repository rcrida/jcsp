package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The numeric analogue of {@link ObjectEmptyDomain}: the empty {@link NumericDiscreteDomain},
 * produced by {@link NumericSetDomain.NumericSetDomainBuilder#build} whenever narrowing empties a
 * {@link NumericSetDomain} completely. {@link #getMin()}/{@link #getMax()} throw, matching {@link
 * NumericSetDomain}'s own pre-existing behavior for an empty domain (its {@code
 * values.stream().min/max(...).orElseThrow()}) -- callers must not ask an empty domain for
 * bounds, the same contract as before this class existed, just enforced with an explicit message
 * instead of falling out of {@link Stream#min}/{@link Stream#max}'s own exception.
 * <p>
 * One shared instance for all {@code N} via {@link #instance()}, mirroring {@code
 * java.util.Collections#emptyList()} -- there is exactly one possible empty domain regardless of
 * {@code N}, so the underlying instance is reused rather than allocated per call, with the
 * unchecked cast that requires contained entirely inside {@link #instance()} instead of repeated
 * at every call site. A plain final class rather than a record, unlike every other domain type:
 * see {@link ObjectEmptyDomain}'s Javadoc for why a record couldn't enforce this.
 */
public final class NumericEmptyDomain<N extends Number> implements NumericDiscreteDomain<N> {
    private static final NumericEmptyDomain<?> INSTANCE = new NumericEmptyDomain<>();

    private NumericEmptyDomain() {}

    @SuppressWarnings("unchecked")
    public static <N extends Number> NumericEmptyDomain<N> instance() {
        return (NumericEmptyDomain<N>) INSTANCE;
    }

    @Override
    public N getMin() {
        throw new NoSuchElementException("empty domain has no minimum");
    }

    @Override
    public N getMax() {
        throw new NoSuchElementException("empty domain has no maximum");
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
    public Stream<N> stream() {
        return Stream.empty();
    }

    @Override
    public List<N> toList() {
        return List.of();
    }

    @Override
    public Optional<N> singleValue() {
        return Optional.empty();
    }

    @Override
    public Builder<N> toBuilder() {
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
