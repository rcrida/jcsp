package io.github.rcrida.jcsp.domains;

import java.util.Set;

/**
 * Represents a domain containing the two boolean values {@code true} and {@code false}.
 */
public record BooleanDomain() implements SetDomain<Boolean> {
    public static final BooleanDomain INSTANCE = new BooleanDomain();

    private static final Set<Boolean> BOOLEAN_VALUES = Set.of(true, false);

    @Override
    public Set<Boolean> values() {
        return BOOLEAN_VALUES;
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }
}
