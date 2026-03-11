package org.jcsp.domains;

import java.util.HashSet;
import java.util.Set;

public class IntRangeDomain extends DomainObjectSet {
    public IntRangeDomain(int minInclusive, int maxInclusive) {
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
