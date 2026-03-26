package org.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;

public class AssignedDomain extends DomainObjectSet {
    public AssignedDomain(@NonNull Object value) {
        super(Set.of(value));
    }
}
