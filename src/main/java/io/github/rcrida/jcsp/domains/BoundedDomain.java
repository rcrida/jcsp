package io.github.rcrida.jcsp.domains;

/**
 * A {@link Domain} whose values form a contiguous numeric range, supporting bounds narrowing
 * without enumerating individual values. Used by continuous domains such as {@link IntervalDomain}.
 */
public interface BoundedDomain<T extends Number> extends NumericDomain<T> {
    /**
     * Returns this domain narrowed to the intersection of its current bounds and
     * {@code [newMin, newMax]}. The result may be empty if the bounds do not overlap.
     * <p>
     * Takes {@code double} rather than a {@code T}-typed pair: every real caller of bounds-
     * narrowing code (propagators working generically over an erased {@code T}) only ever had a
     * plain {@code double} in hand — a freshly computed bound, or a {@code T} value already widened
     * via {@code Number#doubleValue()} — and previously had to reach for a raw {@link BoundedDomain}
     * reference just to pass it through the old {@code T}-typed signature, autoboxing to {@code
     * Double} in the process. Taking {@code double} natively removes that raw-type cast (and the
     * boxing) from every one of those call sites, and gives this method the exact signature {@link
     * NumericDomain}'s own shared contract already needs, so no separate delegating override is
     * required here any more — {@code T}'s bound already implies {@code double} is a lossless
     * enough working representation for every real implementation ({@link IntervalDomain} itself
     * stores {@code double} fields directly).
     * <p>
     * Returns {@code BoundedDomain<T>} rather than the weaker {@code Domain<T>}, so a caller that
     * narrows more than once within a single pass (e.g. narrow, then inspect the result via {@link
     * #getMin()}/{@link #getMax()} to decide on a further narrowing) never needs an intermediate
     * cast back to this interface.
     */
    @Override
    BoundedDomain<T> withBounds(double newMin, double newMax);
}
