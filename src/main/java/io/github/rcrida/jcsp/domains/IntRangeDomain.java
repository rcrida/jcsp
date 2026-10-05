package io.github.rcrida.jcsp.domains;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a domain of integers defined by an inclusive range. Every call site that used to
 * build one from an arbitrary {@code Set<Integer>} (frequently gapped, despite the class's name)
 * now uses {@link NumericSetDomain} instead, so in practice this is only ever constructed via
 * {@link #of} — a record's canonical constructor can't be declared more restrictive than the
 * record itself (it would need to be non-{@code public}, which isn't viable for a type this
 * library exposes across packages), so that's enforced by convention plus the assertion below
 * rather than access control.
 */
public record IntRangeDomain(Set<Integer> values, int min, int max)
        implements DiscreteSetDomain<Integer>, NumericDiscreteDomain<Integer> {
    public IntRangeDomain {
        // The LinkedHashSet is the defensive copy a caller-supplied set needs; handing that copy
        // straight to OrderedValueSet, rather than wrapping it, is what makes traversal an array
        // walk -- and root domains are traversed on every arc revision for the whole solve.
        values = OrderedValueSet.handingOver(new LinkedHashSet<>(values));
        assert values.isEmpty() || (min == Collections.min(values) && max == Collections.max(values))
                : String.format("min (%d) and max (%d) must match the actual bounds of values %s", min, max, values);
    }

    public static IntRangeDomain of(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        var range = new LinkedHashSet<Integer>(maxInclusive - minInclusive + 1);
        for (int i = minInclusive; i <= maxInclusive; i++) range.add(i);
        return new IntRangeDomain(range, minInclusive, maxInclusive);
    }

    /**
     * The numeric builder, not the {@link DiscreteSetDomain} default, for exactly the reason {@link
     * NumericSetDomain#toBuilder} gives: deleting values one at a time (as {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3} does on every arc revision) must leave behind a
     * domain that is still a {@link NumericDomain}.
     * <p>
     * Inheriting the generic builder demoted a narrowed range to an {@link ObjectSetDomain}, which
     * is not numeric, so every later {@code instanceof NumericDomain} test on it silently failed:
     * {@link io.github.rcrida.jcsp.constraints.NumericBounds}' {@code min}/{@code max} fell back to
     * streaming the whole domain instead of reading a cached bound, {@code narrow} fell back to
     * per-value deletion instead of {@link NumericDomain#withBounds}, and a propagator gated on
     * numericity stopped acting at all. Measured on {@code Taillard-js-015-15-0}, whose objective
     * variable reached search as a non-numeric {@code ObjectSetDomain}.
     */
    @Override
    public NumericDiscreteDomain.NumericDiscreteDomainBuilder<Integer> toBuilder() {
        return new NumericDiscreteDomain.NumericDiscreteDomainBuilder<>(values);
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
    public boolean equals(Object o) { return DiscreteSetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return DiscreteSetDomain.domainHashCode(this); }

    @Override
    public String toString() {
        // use .. to denote countable range
        return "[" + min + ".." + max + "]";
    }
}
