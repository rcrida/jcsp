package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents a domain that restricts valid values to a predefined set of enum elements.
 */
public record EnumDomain<E extends Enum<E>>(Set<E> values) implements SetDomain<E> {
    public EnumDomain {
        values = Collections.unmodifiableSet(values);
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    public static <E extends Enum<E>> EnumDomain<E> allOf(@NonNull Class<E> elementType) {
        return new EnumDomain<>(EnumSet.allOf(elementType));
    }

    @SafeVarargs
    public static <E extends Enum<E>> EnumDomain<E> of(@NonNull E first, E... rest) {
        return new EnumDomain<>(EnumSet.of(first, rest));
    }
}
