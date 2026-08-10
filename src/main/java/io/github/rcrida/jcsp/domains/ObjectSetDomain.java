package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Represents a set-based implementation of the {@link DiscreteSetDomain} interface.
 */
public record ObjectSetDomain<T>(@NonNull Set<T> values) implements DiscreteSetDomain<T> {

    @Override
    public boolean equals(Object o) { return DiscreteSetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return DiscreteSetDomain.domainHashCode(this); }

    @Override
    public String toString() { return DiscreteSetDomain.domainToString(this); }
}
