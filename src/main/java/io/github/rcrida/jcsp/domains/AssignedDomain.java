package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Represents a domain assigned to a single value, used during search when a variable has been
 * assigned to limit the remaining search space.
 */
public record AssignedDomain(@NonNull Object value) implements SetDomain<Object> {
    @Override
    public Set<Object> values() {
        return Set.of(value);
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }
}
