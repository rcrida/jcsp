package io.github.rcrida.jcsp.domains;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a domain of integers defined by an inclusive range. Every call site that used to
 * build one from an arbitrary {@code Set<Integer>} (frequently gapped, despite the class's name)
 * now uses {@link NumericDiscreteDomain} instead, so in practice this is only ever constructed via
 * {@link #of} — a record's canonical constructor can't be declared more restrictive than the
 * record itself (it would need to be non-{@code public}, which isn't viable for a type this
 * library exposes across packages), so that's enforced by convention plus the assertion below
 * rather than access control.
 */
public record IntRangeDomain(Set<Integer> values, int min, int max) implements SetDomain<Integer>, NumericDomain<Integer> {
    public IntRangeDomain {
        values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
        assert values.isEmpty() || (min == Collections.min(values) && max == Collections.max(values))
                : String.format("min (%d) and max (%d) must match the actual bounds of values %s", min, max, values);
    }

    public static IntRangeDomain of(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        var range = new LinkedHashSet<Integer>(maxInclusive - minInclusive + 1);
        for (int i = minInclusive; i <= maxInclusive; i++) range.add(i);
        return new IntRangeDomain(range, minInclusive, maxInclusive);
    }

    @Override
    public Integer getMin() {
        return min;
    }

    @Override
    public Integer getMax() {
        return max;
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    @Override
    public String toString() {
        return "IntRangeDomain[" + min + ".." + max + "]";
    }
}
