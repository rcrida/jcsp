package org.jcsp.variables;

import lombok.Value;
import org.jcsp.domains.Domain;
import org.jspecify.annotations.NonNull;

/**
 * Represents a variable in a constraint satisfaction problem. Each variable
 * has a name and a domain, which defines the set of allowed values it can take.
 */
public interface Variable {
    String getName();
    Domain getDomain();

    default boolean isAllowedValue(Object value) {
        final var domain = getDomain();
        return domain.contains(value);
    }

    @Value
    class Impl implements Variable {
        @NonNull String name;
        @NonNull Domain domain;

        @Override
        public String toString() {
            return name;
        }
    }

    interface Factory {
        default Variable create(String name, Domain domain) {
            return new Impl(name, domain);
        }
    }
}
