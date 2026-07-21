package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

/**
 * A {@link Domain} whose values form a contiguous numeric range, supporting bounds narrowing
 * without enumerating individual values. Used by continuous domains such as {@link IntervalDomain}.
 */
public interface BoundedDomain<T extends Number & Comparable<T>> extends Domain<T> {
    T getMin();

    T getMax();

    /**
     * Returns this domain narrowed to the intersection of its current bounds and
     * {@code [newMin, newMax]}. The result may be empty if the bounds do not overlap.
     * <p>
     * Returns {@code BoundedDomain<T>} rather than the weaker {@code Domain<T>}, so a caller that
     * narrows more than once within a single pass (e.g. narrow, then inspect the result via {@link
     * #getMin()}/{@link #getMax()} to decide on a further narrowing) never needs an intermediate
     * cast back to this interface.
     */
    BoundedDomain<T> withBounds(@NonNull T newMin, @NonNull T newMax);
}
