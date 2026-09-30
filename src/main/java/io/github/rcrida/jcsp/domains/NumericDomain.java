package io.github.rcrida.jcsp.domains;

/**
 * A {@link Domain} of {@link Number} values that exposes its bounds and can be narrowed to a
 * sub-range, regardless of whether the domain is enumerable ({@link IntRangeDomain}) or continuous
 * ({@link BoundedDomain}) — the shared contract {@link io.github.rcrida.jcsp.constraints.NumericBounds}
 * dispatches through instead of separate handling for each domain kind.
 * <p>
 * {@link BoundedDomain} narrows the return type of {@link #withBounds} to {@code BoundedDomain<T>}
 * and implements it directly — no delegating override needed, since {@code double} is already what
 * a {@link BoundedDomain} like {@link IntervalDomain} works in internally (its {@code double min,
 * double max} fields), and every real caller of bounds-narrowing code only ever had a {@code
 * double} in hand anyway (see {@link BoundedDomain#withBounds}'s own Javadoc). The default {@link
 * #withBounds} here instead assumes {@code this} is also a {@link DiscreteDomain} (true for every
 * non-{@link BoundedDomain} implementor) and filters its values through {@link
 * NumericDiscreteDomain}'s builder, which already collapses to a {@link NumericSingletonDomain} when
 * exactly one value survives the filter (see {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder#build})
 * — the numeric analogue of {@link DiscreteDomain.DiscreteDomainBuilder}'s own fallback to {@link
 * ObjectSetDomain} when the caller's specific concrete type isn't known. That builder's {@code
 * build()} is declared to return {@link NumericDiscreteDomain} (a subtype of this interface), so no
 * cast is needed here despite {@code this} method's own return type being the broader {@link
 * NumericDomain} -- kept broad so {@link BoundedDomain} can still narrow its own override to {@code
 * BoundedDomain<T>}, which isn't enumerable and so can't implement {@link NumericDiscreteDomain}.
 */
public interface NumericDomain<N extends Number> extends Domain<N> {
    N getMin();

    N getMax();

    /**
     * Returns this domain narrowed to its intersection with {@code [newMin, newMax]}, or {@code
     * this} when the requested range already contains every value — the case a propagator reaching
     * its fixpoint hits over and over. Filtering would compute that intersection implicitly, since
     * every value present is by definition within the domain's current bounds, but comparing the
     * two bounds first is {@code O(1)} where filtering is {@code O(size)}, and it lets {@link
     * io.github.rcrida.jcsp.constraints.NumericBounds#narrow} recognise the no-op by reference
     * rather than by an equally sized {@code equals} against a domain it just rebuilt.
     * <p>
     * Returning {@code this} also keeps the domain's own type, where filtering hands back whatever
     * {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder} collapses to: an {@link
     * IntRangeDomain} that survives a covering narrowing intact stays an {@link IntRangeDomain}
     * rather than degrading to a {@link NumericSetDomain}.
     */
    @SuppressWarnings("unchecked")
    default NumericDomain<N> withBounds(double newMin, double newMax) {
        DiscreteDomain<N> discrete = (DiscreteDomain<N>) this;
        if (!discrete.isEmpty() && getMin().doubleValue() >= newMin && getMax().doubleValue() <= newMax) {
            return this;
        }
        var builder = new NumericDiscreteDomain.NumericDiscreteDomainBuilder<N>(discrete.size());
        for (N value : discrete.asCollection()) {
            if (value.doubleValue() >= newMin && value.doubleValue() <= newMax) {
                builder.value(value);
            }
        }
        return builder.build();
    }
}
