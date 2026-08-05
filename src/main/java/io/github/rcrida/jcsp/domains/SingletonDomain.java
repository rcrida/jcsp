package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A domain holding exactly one value, used during search when a variable has been assigned to
 * limit the remaining search space, and by {@link SetDomain.DefaultBuilder#build} (or, for {@link
 * DomainObjectSet}, its own builder override of the same optimization) whenever ordinary
 * propagation narrows a {@link SetDomain}-backed domain down to one remaining value -- most
 * instances in a real solve come from the latter, not an explicit search decision. The one
 * exception is {@link NumericDiscreteDomain}: its builder deliberately keeps Lombok's generated
 * {@code build()} rather than collapsing to a {@link SingletonDomain}, because {@link
 * NumericDomain#withBounds}'s default method (the main producer of narrowed {@link
 * NumericDiscreteDomain}s) is typed to return {@link NumericDomain}, which {@link SingletonDomain}
 * doesn't implement. Implements
 * {@link DiscreteDomain} directly rather than {@link SetDomain}: {@link #contains}/{@link
 * #isEmpty}/{@link #size}/{@link #stream}/{@link #singleValue} all work straight against {@link
 * #value}, without materialising a throwaway {@code Set.of(value)} the way {@link SetDomain}'s
 * default implementations would need to -- worthwhile because this is one of the hottest paths in
 * the whole solver.
 * <p>
 * {@link #equals}/{@link #hashCode} still compare equal to any {@link DiscreteDomain} (not just
 * another {@link SingletonDomain}) holding the same single value -- e.g. a singleton {@link
 * IntRangeDomain} and a {@link SingletonDomain} holding the same value are equal in both directions
 * -- via {@link SetDomain#domainEquals}, which checks {@code instanceof DiscreteDomain} rather than
 * {@code instanceof SetDomain} specifically so this class (the one {@link DiscreteDomain}
 * implementor that isn't a {@link SetDomain}) doesn't break that symmetry.
 */
public record SingletonDomain(@NonNull Object value) implements DiscreteDomain<Object> {
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
    public Stream<Object> stream() {
        return Stream.of(value);
    }

    @Override
    public List<Object> toList() {
        return List.of(value);
    }

    @Override
    public Optional<Object> singleValue() {
        return Optional.of(value);
    }

    @Override
    public Builder<Object> toBuilder() {
        return new SetDomain.DefaultBuilder<>(Set.of(value));
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
}
