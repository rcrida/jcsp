package io.github.rcrida.jcsp.domains;

import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a set-based implementation of the {@link SetDomain} interface.
 */
@Builder(toBuilder = true)
public record DomainObjectSet<T>(@Singular Set<T> values) implements SetDomain<T> {

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    public static class DomainObjectSetBuilder<T> implements DiscreteDomain.Builder<T> {
        @Override
        public DiscreteDomain.Builder<T> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }
    }
}
