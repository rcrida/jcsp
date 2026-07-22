package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A "set interval" domain: the set of every possible {@code Set<E>} value {@code S} such that
 * {@code lowerBound ⊆ S ⊆ upperBound} and {@code minCardinality <= |S| <= maxCardinality}. Values
 * are not enumerated — narrowing proceeds via {@link #withLowerBound}, {@link #withUpperBound},
 * and {@link #withCardinality} instead, the set-CP analogue of how {@link IntervalDomain} narrows
 * without enumerating every {@code double} in its range.
 * <p>
 * Bounds are always stored sorted by {@link #comparator}, via a {@link TreeSet} — every
 * construction path (including {@link #withLowerBound}/{@link #withUpperBound}/{@link
 * #withCardinality}, not just the two {@code of} factory groups below) re-sorts, so a caller that
 * needs deterministic enumeration (e.g. a branching search picking "the next candidate element")
 * never has to re-derive an ordering itself, no matter how many narrowing steps a domain has
 * already been through. {@code comparator} is required, not optional, precisely so this holds
 * unconditionally: the two {@code of} factory groups are the only two ways to obtain one (either
 * {@link Comparator#naturalOrder()} for {@code E extends Comparable<E>}, or an explicit {@code
 * Comparator<E>} for any other {@code E}), so there's never a domain state without one to fall
 * back on.
 */
public record SetIntervalDomain<E>(Set<E> lowerBound, Set<E> upperBound, int minCardinality, int maxCardinality,
                                    Comparator<E> comparator)
        implements SetBoundedDomain<E> {

    /**
     * Beyond wrapping the bounds defensively (sorted via {@link #comparator}), this also applies a
     * domain-intrinsic tightening that holds regardless of which constraint (if any) is doing the
     * narrowing: once {@code |lowerBound| == maxCardinality}, no further element can ever be added
     * without exceeding the cardinality cap, so {@code upperBound} narrows to its intersection with
     * {@code lowerBound}; symmetrically, once {@code |upperBound| == minCardinality}, no candidate
     * can be dropped without falling short of it, so {@code lowerBound} widens to its union with
     * {@code upperBound}. Both the trigger conditions <em>and</em> the new values are computed from
     * the bounds exactly as passed into this constructor call (never against each other's result,
     * and never against a caller's pre-narrowing state from further up the call stack) —
     * deliberately intersection/union, not a blind overwrite of one bound with the other: for an
     * already-valid pair ({@code lowerBound ⊆ upperBound}) the two coincide, but overwriting
     * instead of intersecting/unioning would silently discard a genuinely narrower value a caller
     * passed in — e.g. {@code withUpperBound(Set.of())} called on a domain already at {@code
     * |lowerBound|==maxCardinality} would, under a blind overwrite, "helpfully" restore {@code
     * upperBound} back to {@code lowerBound} instead of leaving it empty, masking exactly the
     * infeasibility {@link #isEmpty()}'s containment check exists to catch (found via {@code
     * DisjointConstraint} propagating a real exclusion into this exact state). This runs on every
     * construction path, including {@link #of}, since an un-tightened-but-equivalent domain is
     * never wrong, just less informative than it could immediately be — e.g. it's what lets a set
     * variable resolve to a singleton through propagation alone, without needing a dedicated
     * branching search stage.
     */
    public SetIntervalDomain {
        lowerBound = sorted(lowerBound, comparator);
        upperBound = sorted(upperBound, comparator);

        Set<E> tightenedUpper = upperBound;
        if (lowerBound.size() == maxCardinality) {
            Set<E> intersected = new HashSet<>(upperBound);
            intersected.retainAll(lowerBound);
            tightenedUpper = sorted(intersected, comparator);
        }
        Set<E> tightenedLower = lowerBound;
        if (upperBound.size() == minCardinality) {
            Set<E> unioned = new HashSet<>(lowerBound);
            unioned.addAll(upperBound);
            tightenedLower = sorted(unioned, comparator);
        }
        upperBound = tightenedUpper;
        lowerBound = tightenedLower;
    }

    private static <E> Set<E> sorted(Set<E> elements, Comparator<E> comparator) {
        var tree = new TreeSet<>(comparator);
        tree.addAll(elements);
        return Collections.unmodifiableSet(tree);
    }

    /**
     * Constructs and validates an initial domain, ordered by {@link Comparator#naturalOrder()}.
     * Unlike {@link #withLowerBound}/{@link #withUpperBound}/{@link #withCardinality} (used during
     * propagation, where narrowing to an empty domain is an expected, silently-representable
     * outcome), this is the user-facing construction path, so an already-infeasible domain is a
     * programmer error caught eagerly — the same relationship {@link IntervalDomain#of} has with
     * {@link IntervalDomain#withBounds}.
     */
    public static <E extends Comparable<E>> SetIntervalDomain<E> of(@NonNull Set<E> lowerBound, @NonNull Set<E> upperBound,
                                                                      int minCardinality, int maxCardinality) {
        return of(lowerBound, upperBound, minCardinality, maxCardinality, Comparator.naturalOrder());
    }

    /** {@code lowerBound = ∅}, {@code upperBound = universe}, cardinality unrestricted within {@code [0, |universe|]}. */
    public static <E extends Comparable<E>> SetIntervalDomain<E> of(@NonNull Set<E> universe) {
        return of(Set.of(), universe, 0, universe.size());
    }

    /** As {@link #of(Set, Set, int, int)}, ordered by the given {@code comparator} instead of requiring {@code E extends Comparable<E>}. */
    public static <E> SetIntervalDomain<E> of(@NonNull Set<E> lowerBound, @NonNull Set<E> upperBound,
                                               int minCardinality, int maxCardinality, @NonNull Comparator<E> comparator) {
        assert upperBound.containsAll(lowerBound) :
                String.format("lowerBound %s must be a subset of upperBound %s", lowerBound, upperBound);
        assert minCardinality >= 0 : String.format("minCardinality (%d) must not be negative", minCardinality);
        assert minCardinality <= maxCardinality :
                String.format("minCardinality (%d) must be less than or equal to maxCardinality (%d)", minCardinality, maxCardinality);
        assert lowerBound.size() <= maxCardinality :
                String.format("lowerBound size (%d) must not exceed maxCardinality (%d)", lowerBound.size(), maxCardinality);
        assert upperBound.size() >= minCardinality :
                String.format("upperBound size (%d) must be at least minCardinality (%d)", upperBound.size(), minCardinality);
        return new SetIntervalDomain<>(lowerBound, upperBound, minCardinality, maxCardinality, comparator);
    }

    /** As {@link #of(Set)}, ordered by the given {@code comparator} instead of requiring {@code E extends Comparable<E>}. */
    public static <E> SetIntervalDomain<E> of(@NonNull Set<E> universe, @NonNull Comparator<E> comparator) {
        return of(Set.of(), universe, 0, universe.size(), comparator);
    }

    @Override
    public Set<E> getLowerBound() { return lowerBound; }

    @Override
    public Set<E> getUpperBound() { return upperBound; }

    @Override
    public int getMinCardinality() { return minCardinality; }

    @Override
    public int getMaxCardinality() { return maxCardinality; }

    @Override
    public Comparator<E> getComparator() { return comparator; }

    @Override
    public SetIntervalDomain<E> withLowerBound(@NonNull Set<E> forcedIn) {
        var newLower = new HashSet<>(lowerBound);
        newLower.addAll(forcedIn);
        return new SetIntervalDomain<>(newLower, upperBound, minCardinality, maxCardinality, comparator);
    }

    @Override
    public SetIntervalDomain<E> withUpperBound(@NonNull Set<E> restrictedTo) {
        var newUpper = new HashSet<>(upperBound);
        newUpper.retainAll(restrictedTo);
        return new SetIntervalDomain<>(lowerBound, newUpper, minCardinality, maxCardinality, comparator);
    }

    @Override
    public SetIntervalDomain<E> withCardinality(int newMin, int newMax) {
        return new SetIntervalDomain<>(lowerBound, upperBound, Math.max(minCardinality, newMin), Math.min(maxCardinality, newMax), comparator);
    }

    @Override
    public boolean contains(@Nullable Object value) {
        if (!(value instanceof Set<?> s)) return false;
        return s.size() >= minCardinality && s.size() <= maxCardinality
                && lowerBound.stream().allMatch(s::contains)
                && s.stream().allMatch(upperBound::contains);
    }

    /**
     * Empty (infeasible) when the lower bound escapes the upper bound, the cardinality range is
     * contradictory, or the bounds and cardinality range are jointly unsatisfiable ({@code
     * lowerBound} alone already exceeds {@code maxCardinality}, or even taking every candidate in
     * {@code upperBound} can't reach {@code minCardinality}).
     */
    @Override
    public boolean isEmpty() {
        return !upperBound.containsAll(lowerBound)
                || minCardinality > maxCardinality
                || lowerBound.size() > maxCardinality
                || upperBound.size() < minCardinality;
    }

    @Override
    public int size() {
        return isSingleton() ? 1 : Integer.MAX_VALUE;
    }

    /** {@code lowerBound.equals(upperBound)}, guarded by {@link #isEmpty()} since an infeasible domain is never a singleton. */
    @Override
    public boolean isSingleton() {
        return !isEmpty() && lowerBound.equals(upperBound);
    }

    @Override
    public Optional<Set<E>> singleValue() {
        return isSingleton() ? Optional.of(lowerBound) : Optional.empty();
    }

    /**
     * Ignores {@link #comparator}: two domains with the same bounds and cardinality range are the
     * same domain regardless of which ordering (or which of two logically-equivalent {@code
     * Comparator} instances, e.g. two separate {@code naturalOrder()} calls) produced their
     * internal iteration order. {@code lowerBound}/{@code upperBound} equality is already
     * order-independent ({@link Set#equals} never considers iteration order), so this override
     * exists solely to keep {@code comparator} out of the comparison and hash entirely.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetIntervalDomain<?> other)) return false;
        return minCardinality == other.minCardinality && maxCardinality == other.maxCardinality
                && lowerBound.equals(other.lowerBound) && upperBound.equals(other.upperBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound, minCardinality, maxCardinality);
    }

    @Override
    public String toString() {
        return "[" + lowerBound + " subsetOf S subsetOf " + upperBound + ", |S| in [" + minCardinality + ", " + maxCardinality + "]]";
    }
}
