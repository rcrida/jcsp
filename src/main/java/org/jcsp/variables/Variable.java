package org.jcsp.variables;

import org.jcsp.domains.Domain;

public interface Variable {
    String getName();
    Domain getDomain();

    default boolean isAllowedValue(Object value) {
        final var domain = getDomain();
        return domain.contains(value);
    }

    interface Factory {
        default Variable create(String name, Domain domain) {
            return new Variable() {

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Domain getDomain() {
                    return domain;
                }
            };
        }
    }
}
