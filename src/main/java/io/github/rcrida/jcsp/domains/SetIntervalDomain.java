package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
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
 * already been through. {@link #comparator} is required, not optional, precisely so this holds
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
     * without exceeding the cardinality cap, so {@link #upperBound} narrows to its intersection with
     * {@link #lowerBound}; symmetrically, once {@code |upperBound| == minCardinality}, no candidate
     * can be dropped without falling short of it, so {@link #lowerBound} widens to its union with
     * {@link #upperBound}. Both the trigger conditions <em>and</em> the new values are computed from
     * the bounds exactly as passed into this constructor call (never against each other's result,
     * and never against a caller's pre-narrowing state from further up the call stack) —
     * deliberately intersection/union, not a blind overwrite of one bound with the other: for an
     * already-valid pair ({@code lowerBound ⊆ upperBound}) the two coincide, but overwriting
     * instead of intersecting/unioning would silently discard a genuinely narrower value a caller
     * passed in — e.g. {@code withUpperBound(Set.of())} called on a domain already at {@code
     * |lowerBound|==maxCardinality} would, under a blind overwrite, "helpfully" restore {@code
     * upperBound} back to {@link #lowerBound} instead of leaving it empty, masking exactly the
     * infeasibility {@link #isEmpty()}'s containment check exists to catch (found via {@link
     * io.github.rcrida.jcsp.constraints.binary.DisjointConstraint} propagating a real exclusion into this exact state). This runs on every
     * construction path, including {@link #of}, since an un-tightened-but-equivalent domain is
     * never wrong, just less informative than it could immediately be — e.g. it's what lets a set
     * variable resolve to a singleton through propagation alone, without needing a dedicated
     * branching search stage.
     * <p>
     * The trigger checks and intersection/union above run against the raw, as-passed-in bounds
     * <em>before</em> either is sorted, so each bound is only ever sorted once — whichever value
     * (raw or tightened) turns out to be the final one — rather than sorting the raw bound first
     * and then sorting the tightened replacement again on top when tightening fires. {@link Set#size}
     * and set intersection/union are unaffected by a {@link Set}'s internal ordering, so computing
     * them from the raw bounds gives exactly the same result either way.
     */
    public SetIntervalDomain {
        Set<E> tightenedUpper = upperBound;
        if (lowerBound.size() == maxCardinality) {
            tightenedUpper = new HashSet<>(upperBound);
            tightenedUpper.retainAll(lowerBound);
        }
        Set<E> tightenedLower = lowerBound;
        if (upperBound.size() == minCardinality) {
            tightenedLower = new HashSet<>(lowerBound);
            tightenedLower.addAll(upperBound);
        }
        lowerBound = sorted(tightenedLower, comparator);
        upperBound = sorted(tightenedUpper, comparator);
    }

    /**
     * Skips the re-sort when {@code elements} is already a {@link TrustedSortedSet} ordered by an
     * equal {@link #comparator} — the common case for whichever bound a narrowing call <em>didn't</em>
     * touch (e.g. {@link #withLowerBound} passes {@link #upperBound} straight through unchanged).
     * Deliberately checks for {@link TrustedSortedSet} specifically, not the generic {@link
     * SortedSet} interface: a caller-supplied {@link Set} that merely happens to already be sorted
     * (e.g. an external raw {@link TreeSet}) would pass a generic {@code instanceof SortedSet}
     * check too, but returning it by reference — instead of the defensive copy every other path
     * here takes — would alias that caller's own, still-mutable object into this otherwise-immutable
     * domain; a later external mutation would then silently corrupt it. {@link TrustedSortedSet}'s
     * constructor is private to this class, so no such alias can ever occur: an instance can only
     * exist if this method itself already produced it — see that class's own Javadoc for the
     * incident this reasoning comes from (and for why {@link TrustedSortedSet} deliberately does
     * <em>not</em> implement {@link SortedSet}, a second, independent incident from the same
     * optimization attempt).
     */
    private static <E> Set<E> sorted(Set<E> elements, Comparator<E> comparator) {
        if (elements instanceof TrustedSortedSet<E> trusted && Objects.equals(trusted.sortedBy, comparator)) {
            return trusted;
        }
        var tree = new TreeSet<>(comparator);
        tree.addAll(elements);
        return new TrustedSortedSet<>(tree, comparator);
    }

    /**
     * Marks a {@link Set} as already produced by {@link #sorted}, so a later {@link #sorted} call
     * can safely return it by reference instead of re-sorting. Only {@link #sorted} can ever
     * construct one (private constructor, private class), so {@code instanceof TrustedSortedSet}
     * is a sound way to distinguish "genuinely our own prior output" from an external caller's own
     * {@link Set} that merely happens to already be sorted the same way — the distinction an
     * earlier, reverted version of this optimization got wrong: it checked the generic {@link
     * SortedSet} interface instead, which let an externally-supplied, still-mutable {@link TreeSet}
     * get aliased into this otherwise-immutable domain instead of defensively copied, so a later
     * external mutation silently corrupted it (caught by {@code mvn clean verify} via a "passes in
     * isolation, fails as part of the full test class" signature — the tell for shared mutable
     * state).
     * <p>
     * Deliberately implements {@link Set} only, <em>not</em> {@link SortedSet}, even though it's
     * backed by a real, sorted {@link TreeSet} internally: AssertJ's {@code isEqualTo} switches to
     * an order-sensitive, element-by-element comparison the moment the actual value implements
     * {@link SortedSet} (rather than the order-independent {@link Set#equals} used for a plain
     * {@link Set}), and {@code Set.of(...)}'s own iteration order is randomised per JVM launch — so
     * an early version of this class that did implement {@link SortedSet} passed or failed
     * genuinely at random depending on whether that random order happened to match ascending order
     * that particular run (reproduced directly against AssertJ, no Maven/JaCoCo involved, ruling
     * those out first). Iteration order is still deterministically sorted (via the internal
     * {@link TreeSet}), preserving this class's own determinism guarantee for callers like {@link
     * io.github.rcrida.jcsp.solver.SetBranchingSolver} — only the {@link SortedSet} <em>interface</em>
     * (and the special handling library code gives it) is withheld.
     */
    private static final class TrustedSortedSet<E> extends AbstractSet<E> {
        private final Set<E> delegate;
        private final Comparator<E> sortedBy;

        private TrustedSortedSet(TreeSet<E> backing, Comparator<E> sortedBy) {
            this.delegate = Collections.unmodifiableSet(backing);
            this.sortedBy = sortedBy;
        }

        @Override public int size() { return delegate.size(); }
        @Override public boolean contains(Object o) { return delegate.contains(o); }
        @Override public Iterator<E> iterator() { return delegate.iterator(); }
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

    /** As {@link #of(Set, Set, int, int)}, ordered by the given {@link #comparator} instead of requiring {@code E extends Comparable<E>}. */
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

    /** As {@link #of(Set)}, ordered by the given {@link #comparator} instead of requiring {@code E extends Comparable<E>}. */
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

    /** No-op check first: a repeat call with an already-subsumed {@code forcedIn} (common once a propagator's forced element has already narrowed in) skips the copy and the constructor's resort entirely. */
    @Override
    public SetIntervalDomain<E> withLowerBound(@NonNull Set<E> forcedIn) {
        if (lowerBound.containsAll(forcedIn)) return this;
        var newLower = new HashSet<>(lowerBound);
        newLower.addAll(forcedIn);
        return new SetIntervalDomain<>(newLower, upperBound, minCardinality, maxCardinality, comparator);
    }

    /** No-op check first, mirroring {@link #withLowerBound}. */
    @Override
    public SetIntervalDomain<E> withUpperBound(@NonNull Set<E> restrictedTo) {
        if (restrictedTo.containsAll(upperBound)) return this;
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
        if (s.size() < minCardinality || s.size() > maxCardinality) return false;
        for (E e : lowerBound) {
            if (!s.contains(e)) return false;
        }
        for (Object e : s) {
            if (!upperBound.contains(e)) return false;
        }
        return true;
    }

    /**
     * Empty (infeasible) when the lower bound escapes the upper bound, the cardinality range is
     * contradictory, or the bounds and cardinality range are jointly unsatisfiable ({@code
     * lowerBound} alone already exceeds {@link #maxCardinality}, or even taking every candidate in
     * {@link #upperBound} can't reach {@link #minCardinality}).
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
     * same domain regardless of which ordering (or which of two logically-equivalent {@link
     * Comparator} instances, e.g. two separate {@link Comparator#naturalOrder} calls) produced their
     * internal iteration order. {@link #lowerBound}/{@link #upperBound} equality is already
     * order-independent ({@link Set#equals} never considers iteration order), so this override
     * exists solely to keep {@link #comparator} out of the comparison and hash entirely.
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
        // use .. to denote countable range
        return "[" + lowerBound + " subsetOf S subsetOf " + upperBound + ", |S| in [" + minCardinality + ".." + maxCardinality + "]]";
    }
}
