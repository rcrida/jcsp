package org.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Represents a domain that restricts valid values to a predefined set of elements belonging
 * to a specified enumeration. This class is immutable and uses an {@link EnumSet} to store
 * the allowed values, ensuring efficient storage and lookup operations.
 *
 * <p>Instances of this class verify whether a given value is allowed by checking its
 * membership in the set of allowed enumeration constants. The domain only permits values
 * that are explicitly specified in the provided EnumSet.
 *
 * <p>This implementation adheres to the {@link Domain} interface contract.
 *
 * @param values a set of allowed enumeration constants represented as an {@link EnumSet<?>}.
 *               The set defines the boundaries of valid values for this domain.
 */
public record EnumDomain(@NonNull EnumSet<?> values) implements Domain {
    public static <E extends Enum<E>> EnumDomain allOf(@NonNull Class<E> elementType) {
        return new EnumDomain(EnumSet.allOf(elementType));
    }

    @Override
    public boolean contains(@Nullable Object value) {
        return values.contains(value);
    }
}
