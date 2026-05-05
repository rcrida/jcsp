package org.jcsp.domains;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a domain of integers defined by an inclusive range. This class is a specialization
 * of {@link DomainObjectSet} that generates a set of integer values between the specified
 * minimum and maximum bounds.
 */
public class IntRangeDomain extends DomainObjectSet {
    public static IntRangeDomain of(int minInclusive, int maxInclusive) {
        return new IntRangeDomain(minInclusive, maxInclusive);
    }

    private IntRangeDomain(int minInclusive, int maxInclusive) {
        super(populateRange(minInclusive, maxInclusive));
    }

    private static Set<Integer> populateRange(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        final var range = new HashSet<Integer>();
        for (int i = minInclusive; i <= maxInclusive; i++) {
            range.add(i);
        }
        return range;
    }
}
