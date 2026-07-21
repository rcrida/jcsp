package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * A {@link Domain} of {@link Set} values, characterised by a lower bound (elements known to be in
 * every possible value) and an upper bound (elements that are candidates for some possible value)
 * plus a cardinality range, rather than an enumeration of every candidate subset — the set-CP
 * analogue of {@link BoundedDomain}'s numeric interval, sometimes called a "set interval" in the
 * constraint-programming literature: the set of every {@code Set<E>} value {@code S} such that
 * {@code getLowerBound() ⊆ S ⊆ getUpperBound()} and {@code getMinCardinality() <= |S| <=
 * getMaxCardinality()}. Used by {@link SetIntervalDomain}, its sole implementation, the same
 * relationship {@link BoundedDomain} has with {@link IntervalDomain}.
 * <p>
 * Cardinality is tracked as an explicit range rather than derived from {@code |getUpperBound()| -
 * |getLowerBound()|}: the two are independent. A domain can have five undetermined elements
 * ({@code |upperBound \ lowerBound| = 5}) while cardinality is pinned to exactly 2 — narrowing
 * cardinality alone (via {@link #withCardinality}) doesn't touch the bounds, and narrowing a bound
 * doesn't touch cardinality.
 * <p>
 * Deliberately <em>not</em> a subtype of {@link BoundedDomain}: every existing {@code
 * BoundedDomain} consumer (e.g. {@code NumericBounds}, {@code BisectionConditioningSolver}, {@code
 * ConstraintSatisfactionProblem}'s {@code CONTINUOUS_COMPATIBLE_CONSTRAINTS} whitelist check) does
 * an {@code instanceof BoundedDomain} check and then immediately treats the result as {@code
 * Number} — interval-arithmetic narrowing, midpoint bisection, {@code doubleValue()} bounds
 * extraction. Unifying the two interfaces would sweep this domain kind into all of that
 * numeric-specific handling incorrectly (a {@code Set} is not a {@code Number}), and every one of
 * those call sites would need an extra type check to exclude it again. The two only share a
 * superficial shape (two bound accessors plus narrowing operations); the actual narrowing math
 * differs completely — lattice union/intersection under subset ordering here, versus interval
 * intersection under a total numeric order for {@link BoundedDomain}.
 * <p>
 * The narrowing methods below return {@code SetBoundedDomain<E>} rather than the weaker {@code
 * Domain<Set<E>>}, matching {@link BoundedDomain#withBounds}'s own return type ({@code
 * BoundedDomain<T>}, not {@code Domain<T>}) for the same reason: a caller that narrows more than
 * once within a single {@code propagate()} pass (e.g. force an element into the lower bound, then
 * check whether {@code |lowerBound| == getMaxCardinality()}, and if so trim the upper bound to
 * match) never needs an intermediate cast back to this interface. That chaining is set
 * propagation's main source of strength, so the return type matters more here than it might first
 * appear — but the principle is identical to {@code BoundedDomain}'s, not a divergence from it.
 */
public interface SetBoundedDomain<E> extends Domain<Set<E>> {
    Set<E> getLowerBound();

    Set<E> getUpperBound();

    int getMinCardinality();

    int getMaxCardinality();

    /**
     * Returns this domain with {@code forcedIn} unioned into the lower bound — elements now known
     * to belong to every possible value. May produce an empty domain (e.g. when {@code forcedIn}
     * contains an element outside the upper bound, or pushes the lower bound's size past the
     * maximum cardinality); callers check {@link #isEmpty()} rather than this method throwing.
     */
    SetBoundedDomain<E> withLowerBound(@NonNull Set<E> forcedIn);

    /**
     * Returns this domain with the upper bound intersected with {@code restrictedTo} — elements no
     * longer possible are removed. May produce an empty domain (e.g. when the lower bound is no
     * longer a subset of the narrowed upper bound); callers check {@link #isEmpty()} rather than
     * this method throwing.
     */
    SetBoundedDomain<E> withUpperBound(@NonNull Set<E> restrictedTo);

    /**
     * Returns this domain with its cardinality range narrowed to the intersection of its current
     * range and {@code [newMin, newMax]}. May produce an empty domain; callers check {@link
     * #isEmpty()} rather than this method throwing.
     */
    SetBoundedDomain<E> withCardinality(int newMin, int newMax);
}
