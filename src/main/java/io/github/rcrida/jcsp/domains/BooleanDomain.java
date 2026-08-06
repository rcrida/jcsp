package io.github.rcrida.jcsp.domains;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a domain containing the two boolean values {@code true} and {@code false}.
 */
public record BooleanDomain() implements SetDomain<Boolean> {
    public static final BooleanDomain INSTANCE = new BooleanDomain();

    private static final Set<Boolean> BOOLEAN_VALUES = Set.of(true, false);

    /** The narrowed domain containing only {@code true} -- an {@link ObjectSingletonDomain}, not a {@link BooleanDomain}. */
    public static final DiscreteDomain<Boolean> TRUE_ONLY = INSTANCE.toBuilder().delete(false).build();
    /** The narrowed domain containing only {@code false} -- an {@link ObjectSingletonDomain}, not a {@link BooleanDomain}. */
    public static final DiscreteDomain<Boolean> FALSE_ONLY = INSTANCE.toBuilder().delete(true).build();

    @Override
    public Set<Boolean> values() {
        return BOOLEAN_VALUES;
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    @Override
    public String toString() {
        return "{true, false}";
    }
}
