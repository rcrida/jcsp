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
 * non-{@code BoundedDomain} implementor) and filters its values, building the result as a {@link
 * NumericDiscreteDomain} — the numeric analogue of {@link SetDomain.DefaultBuilder}'s own fallback
 * to {@link DomainObjectSet} when the caller's specific concrete type isn't known.
 */
public interface NumericDomain<N extends Number> extends Domain<N> {
    N getMin();

    N getMax();

    /**
     * Returns this domain narrowed to its intersection with {@code [newMin, newMax]}. Filtering
     * against the requested range already computes that intersection implicitly — every value
     * present is by definition within the domain's current bounds — so, unlike {@link
     * BoundedDomain#withBounds}, no separate current-bounds lookup is needed here.
     */
    @SuppressWarnings("unchecked")
    default NumericDomain<N> withBounds(double newMin, double newMax) {
        var builder = NumericDiscreteDomain.<N>builder();
        for (N value : ((DiscreteDomain<N>) this).toList()) {
            if (value.doubleValue() >= newMin && value.doubleValue() <= newMax) {
                builder.value(value);
            }
        }
        return builder.build();
    }
}
