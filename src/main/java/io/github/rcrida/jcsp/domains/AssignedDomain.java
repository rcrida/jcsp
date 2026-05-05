package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Represents a domain that is assigned a single value, encapsulated as a set. This is used during
 * solution search when a variable has been assigned a value to indicate that it cannot take other
 * values, thus limiting the remaining search space.
 */
public class AssignedDomain extends DomainObjectSet {
    public AssignedDomain(@NonNull Object value) {
        super(Set.of(value));
    }
}
