package org.jcsp.domains;

import lombok.*;
import lombok.experimental.NonFinal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a set-based implementation of the {@link Domain} interface.
 */
@Value
@NonFinal
@Builder(toBuilder = true)
public class DomainObjectSet implements Domain {
    @Singular
    Set<?> values;

    @Override
    public boolean contains(@Nullable Object value) {
        return values.contains(value);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Stream<?> stream() {
        return values.stream();
    }

    @Override
    public String toString() {
        return "{" +
                values.stream().map(Object::toString).collect(Collectors.joining(", ")) +
                '}';
    }

    public static class DomainObjectSetBuilder implements Domain.Builder {
        @Override
        public Builder delete(@NonNull Object value) {
            this.values.remove(value);
            return null;
        }
    }
}
