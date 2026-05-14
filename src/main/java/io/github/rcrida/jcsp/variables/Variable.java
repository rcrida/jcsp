package io.github.rcrida.jcsp.variables;

import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Represents a variable in a constraint satisfaction problem.
 * The type parameter {@code T} is a phantom type that carries the value type at compile time,
 * enabling type-safe domain and constraint registration at the builder level.
 */
public interface Variable<T> {
    String getName();

    @Value
    class Impl<T> implements Variable<T> {
        @NonNull String name;

        @Override
        public String toString() {
            return name;
        }
    }

    interface Factory {
        Factory INSTANCE = new Factory() {};

        default <T> Variable<T> create(String name) {
            return new Impl<>(name);
        }
    }
}
