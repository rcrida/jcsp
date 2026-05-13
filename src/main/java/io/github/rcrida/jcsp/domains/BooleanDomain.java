package io.github.rcrida.jcsp.domains;

import java.util.Set;

/**
 * Represents a domain containing the two boolean values {@code true} and {@code false}.
 */
public class BooleanDomain extends DomainObjectSet {
    public static final BooleanDomain INSTANCE = new BooleanDomain();

    private BooleanDomain() {
        super(Set.of(true, false));
    }
}
