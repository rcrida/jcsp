package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A domain holding exactly one value, used during search when a variable has been assigned to
 * limit the remaining search space, and by {@link DiscreteDomain.DiscreteDomainBuilder#build}
 * whenever ordinary propagation narrows a {@link DiscreteSetDomain}-backed domain down to one remaining
 * value -- most instances in a real solve come from the latter, not an explicit search decision.
 * {@link NumericSetDomain} narrows to {@link NumericSingletonDomain} instead, via {@link
 * NumericDiscreteDomain.NumericDiscreteDomainBuilder} -- {@link ObjectSingletonDomain} doesn't
 * implement {@link NumericDomain}, which some of that builder's callers (e.g. {@link
 * NumericDomain#withBounds}'s default method) rely on.
 * Implements
 * {@link DiscreteDomain} directly rather than {@link DiscreteSetDomain}: {@link #contains}/{@link
 * #isEmpty}/{@link #size}/{@link #stream}/{@link #singleValue} all work straight against {@link
 * #value}, without materialising a throwaway {@code Set.of(value)} the way {@link DiscreteSetDomain}'s
 * default implementations would need to -- worthwhile because this is one of the hottest paths in
 * the whole solver. Genuinely generic in {@code T} (unlike {@link ObjectEmptyDomain}'s shared
 * single instance, which needs an internal unchecked cast precisely because there's no per-value
 * state to infer {@code T} from) -- a normal generic record works fine here since every instance
 * already holds its own distinctly-typed {@code value}, so callers like {@link
 * DiscreteDomain.DiscreteDomainBuilder#build} construct one directly with no cast needed.
 * <p>
 * {@link #equals}/{@link #hashCode} still compare equal to any {@link DiscreteDomain} (not just
 * another {@link ObjectSingletonDomain}) holding the same single value -- e.g. a singleton {@link
 * IntRangeDomain} and an {@link ObjectSingletonDomain} holding the same value are equal in both directions
 * -- via {@link DiscreteSetDomain#domainEquals}, which checks {@code instanceof DiscreteDomain} rather than
 * {@code instanceof DiscreteSetDomain} specifically so this class (one of the {@link DiscreteDomain}
 * implementors that isn't a {@link DiscreteSetDomain}) doesn't break that symmetry.
 */
public record ObjectSingletonDomain<T>(@NonNull T value) implements DiscreteDomain<T> {
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
    public Stream<T> stream() {
        return Stream.of(value);
    }

    @Override
    public List<T> toList() {
        return List.of(value);
    }

    @Override
    public Optional<T> singleValue() {
        return Optional.of(value);
    }

    @Override
    public Builder<T> toBuilder() {
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
