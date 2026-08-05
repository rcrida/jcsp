package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a set-based implementation of the {@link SetDomain} interface.
 */
public record ObjectSetDomain<T>(@NonNull Set<T> values) implements SetDomain<T> {

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    @Override
    public String toString() {
        return values.stream().map(Object::toString).collect(Collectors.joining(", ", "{", "}"));
    }
}
