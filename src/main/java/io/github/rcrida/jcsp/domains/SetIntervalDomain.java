package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A "set interval" domain: the set of every possible {@code Set<E>} value {@code S} such that
 * {@code lowerBound ⊆ S ⊆ upperBound} and {@code minCardinality <= |S| <= maxCardinality}. Values
 * are not enumerated — narrowing proceeds via {@link #withLowerBound}, {@link #withUpperBound},
 * and {@link #withCardinality} instead, the set-CP analogue of how {@link IntervalDomain} narrows
 * without enumerating every {@code double} in its range.
 * <p>
 * Bounds are wrapped via {@link Set#copyOf}, not {@code LinkedHashSet}: unlike {@link
 * IntRangeDomain}/{@link EnumDomain}, which build their own canonical ascending order internally
 * before construction, this record takes whatever {@code Set<E>} a caller passes in directly —
 * wrapping that in a {@code LinkedHashSet} would only freeze in whatever order the caller's set
 * already had (e.g. {@code Set.of(...)}'s randomized per-JVM-run order), not derive a
 * deterministic one, so it isn't worth the extra structure. {@code E} is unbounded (no {@code
 * Comparable} guarantee), so there's no canonical order to derive here anyway; any future consumer
 * that needs deterministic enumeration (e.g. a branching search picking "the next candidate
 * element") sorts explicitly at its own call site instead, the same way {@link #formatSet} already
 * does for display.
 */
public record SetIntervalDomain<E>(Set<E> lowerBound, Set<E> upperBound, int minCardinality, int maxCardinality)
        implements SetBoundedDomain<E> {

    /**
     * Beyond wrapping the bounds defensively, this also applies a domain-intrinsic tightening
     * that holds regardless of which constraint (if any) is doing the narrowing: once {@code
     * |lowerBound| == maxCardinality}, no further element can ever be added without exceeding the
     * cardinality cap, so {@code upperBound} narrows to its intersection with {@code lowerBound};
     * symmetrically, once {@code |upperBound| == minCardinality}, no candidate can be dropped
     * without falling short of it, so {@code lowerBound} widens to its union with {@code
     * upperBound}. Both the trigger conditions <em>and</em> the new values are computed from the
     * bounds exactly as passed into this constructor call (never against each other's result, and
     * never against a caller's pre-narrowing state from further up the call stack) — deliberately
     * intersection/union, not a blind overwrite of one bound with the other: for an already-valid
     * pair ({@code lowerBound ⊆ upperBound}) the two coincide, but overwriting instead of
     * intersecting/unioning would silently discard a genuinely narrower value a caller passed in —
     * e.g. {@code withUpperBound(Set.of())} called on a domain already at {@code
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
        lowerBound = Set.copyOf(lowerBound);
        upperBound = Set.copyOf(upperBound);

        Set<E> tightenedUpper = upperBound;
        if (lowerBound.size() == maxCardinality) {
            Set<E> intersected = new HashSet<>(upperBound);
            intersected.retainAll(lowerBound);
            tightenedUpper = Set.copyOf(intersected);
        }
        Set<E> tightenedLower = lowerBound;
        if (upperBound.size() == minCardinality) {
            Set<E> unioned = new HashSet<>(lowerBound);
            unioned.addAll(upperBound);
            tightenedLower = Set.copyOf(unioned);
        }
        upperBound = tightenedUpper;
        lowerBound = tightenedLower;
    }

    /**
     * Constructs and validates an initial domain. Unlike {@link #withLowerBound}/{@link
     * #withUpperBound}/{@link #withCardinality} (used during propagation, where narrowing to an
     * empty domain is an expected, silently-representable outcome), this is the user-facing
     * construction path, so an already-infeasible domain is a programmer error caught eagerly —
     * the same relationship {@link IntervalDomain#of} has with {@link IntervalDomain#withBounds}.
     */
    public static <E> SetIntervalDomain<E> of(@NonNull Set<E> lowerBound, @NonNull Set<E> upperBound,
                                               int minCardinality, int maxCardinality) {
        assert upperBound.containsAll(lowerBound) :
                String.format("lowerBound %s must be a subset of upperBound %s", lowerBound, upperBound);
        assert minCardinality >= 0 : String.format("minCardinality (%d) must not be negative", minCardinality);
        assert minCardinality <= maxCardinality :
                String.format("minCardinality (%d) must be less than or equal to maxCardinality (%d)", minCardinality, maxCardinality);
        assert lowerBound.size() <= maxCardinality :
                String.format("lowerBound size (%d) must not exceed maxCardinality (%d)", lowerBound.size(), maxCardinality);
        assert upperBound.size() >= minCardinality :
                String.format("upperBound size (%d) must be at least minCardinality (%d)", upperBound.size(), minCardinality);
        return new SetIntervalDomain<>(lowerBound, upperBound, minCardinality, maxCardinality);
    }

    /** {@code lowerBound = ∅}, {@code upperBound = universe}, cardinality unrestricted within {@code [0, |universe|]}. */
    public static <E> SetIntervalDomain<E> of(@NonNull Set<E> universe) {
        return of(Set.of(), universe, 0, universe.size());
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
    public SetIntervalDomain<E> withLowerBound(@NonNull Set<E> forcedIn) {
        var newLower = new HashSet<>(lowerBound);
        newLower.addAll(forcedIn);
        return new SetIntervalDomain<>(newLower, upperBound, minCardinality, maxCardinality);
    }

    @Override
    public SetIntervalDomain<E> withUpperBound(@NonNull Set<E> restrictedTo) {
        var newUpper = new HashSet<>(upperBound);
        newUpper.retainAll(restrictedTo);
        return new SetIntervalDomain<>(lowerBound, newUpper, minCardinality, maxCardinality);
    }

    @Override
    public SetIntervalDomain<E> withCardinality(int newMin, int newMax) {
        return new SetIntervalDomain<>(lowerBound, upperBound, Math.max(minCardinality, newMin), Math.min(maxCardinality, newMax));
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

    @Override
    public String toString() {
        return "[" + formatSet(lowerBound) + " subsetOf S subsetOf " + formatSet(upperBound) + ", |S| in [" + minCardinality + ", " + maxCardinality + "]]";
    }

    /**
     * Renders {@code set} in a deterministic order for display. {@code E} is unbounded (no {@code
     * Comparable} guarantee), and the {@code Set} a caller constructs this domain from may itself
     * have a randomized iteration order (e.g. {@code Set.of(...)}) — sorting by each element's own
     * {@code toString()} keeps this method's output a pure function of the domain's content rather
     * than of incidental construction history, which matters for reproducible debug logging and
     * deterministic test assertions alike.
     */
    private static <E> String formatSet(Set<E> set) {
        return set.stream().map(String::valueOf).sorted().toList().toString();
    }
}
