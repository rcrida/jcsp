package io.github.rcrida.jcsp.variables;

import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Represents a variable in a constraint satisfaction problem.
 */
public interface Variable {
    String getName();

    @Value
    class Impl implements Variable {
        @NonNull String name;

        @Override
        public String toString() {
            return name;
        }
    }

    interface Factory {
        Factory INSTANCE = new Factory() {};

        default Variable create(String name) {
            return new Impl(name);
        }
    }
}
