package io.github.rcrida.jcsp.domains;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a domain of integers defined by an inclusive range.
 */
public record IntRangeDomain(Set<Integer> values) implements SetDomain<Integer> {
    public IntRangeDomain {
        values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public static IntRangeDomain of(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        var range = new LinkedHashSet<Integer>(maxInclusive - minInclusive + 1);
        for (int i = minInclusive; i <= maxInclusive; i++) range.add(i);
        return new IntRangeDomain(range);
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }
}
