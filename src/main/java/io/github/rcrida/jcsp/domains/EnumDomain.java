package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.EnumSet;

/**
 * Represents a domain that restricts valid values to a predefined set of elements belonging
 * to a specified enumeration. This class is immutable and uses an {@link EnumSet} to store
 * the allowed values, ensuring efficient storage and lookup operations.
 */
public class EnumDomain extends DomainObjectSet {
    public static <E extends Enum<E>> EnumDomain allOf(@NonNull Class<E> elementType) {
        return new EnumDomain(EnumSet.allOf(elementType));
    }

    public EnumDomain(@NonNull EnumSet<?> values) {
        super(values);
    }
}
