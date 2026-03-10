package org.jcsp.domains;

import org.jspecify.annotations.Nullable;

public record IntRangeDomain(int minInclusive, int maxInclusive) implements Domain {
    public IntRangeDomain {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
    }

    @Override
    public boolean contains(@Nullable Object value) {
        return value instanceof Integer i && i >= minInclusive && i <= maxInclusive;
    }
}
