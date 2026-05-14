package io.github.rcrida.jcsp.domains;

import lombok.*;
import lombok.experimental.NonFinal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a set-based implementation of the {@link Domain} interface.
 */
@Value
@NonFinal
@Builder(toBuilder = true)
public class DomainObjectSet<T> implements Domain<T> {
    @Singular
    Set<T> values;

    @Override
    public boolean contains(@Nullable Object value) {
        return value != null && values.contains(value);
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
    public Stream<T> stream() {
        return values.stream();
    }

    @Override
    public List<T> toList() {
        return List.copyOf(values);
    }

    @Override
    public String toString() {
        return "{" +
                values.stream().map(Object::toString).collect(Collectors.joining(", ")) +
                '}';
    }

    public static class DomainObjectSetBuilder<T> implements Domain.Builder<T> {
        @Override
        public Domain.Builder<T> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }
    }
}
