package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.ComparableBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.NumericDomain;
import io.github.rcrida.jcsp.variables.Variable;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared bounds-consistency computation for {@link IncreasingConstraint}/{@link DecreasingConstraint},
 * generic over any {@code T extends Comparable<T>} — matching those constraints' own type bound
 * rather than narrowing to {@link Number} the way {@link io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint} does. A
 * non-decreasing chain {@code v[0] <= v[1] <= ... <= v[n-1]} is fully bounds-consistent by
 * computing, independently, a running maximum of minimums left-to-right (transitivity means each
 * {@code v[i].min} must be at least every earlier variable's min) and a running minimum of maximums
 * right-to-left (the dual, for maxes) — one pass each direction, rather than iterating the pairwise
 * decomposition to a fixpoint. {@link DecreasingConstraint} reduces to the same computation over its
 * reversed variable list, since {@code v[0] >= v[1] >= ... >= v[n-1]} holds iff the reverse is
 * non-decreasing.
 * <p>
 * Per-position min/max/narrow are delegated to {@link ComparableBounds} (works uniformly over
 * {@link NumericDomain}, both {@link BoundedDomain} and discrete numeric domains like {@link
 * io.github.rcrida.jcsp.domains.IntRangeDomain}, and any other {@link DiscreteDomain} via natural
 * ordering and value deletion), so an ordering over non-numeric {@link Comparable} types (e.g.
 * {@link String} or enum variables) gets real propagation too, not just numeric chains. This
 * class's own contribution is purely the two-pass running floor/ceiling computation over a chain.
 */
final class OrderingPropagation {
    private OrderingPropagation() {}

    /**
     * @param newMins    each position's tightened lower bound
     * @param newMaxs    each position's tightened upper bound
     * @param minSource  for each position, the index (at or before it) whose min set the current
     *                   running floor — when {@code newMins[i] > newMaxs[i]}, the variable at this
     *                   index together with the one at {@code maxSource[i]} is the (possibly
     *                   non-adjacent) pair the violation traces back to
     * @param maxSource  the dual, for the running ceiling
     */
    record ChainBounds<T extends Comparable<T>>(List<T> newMins, List<T> newMaxs, int[] minSource, int[] maxSource) {}

    /** Bounds consistency for a non-decreasing chain over {@code orderedVariables}, in order. */
    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> ChainBounds<T> nonDecreasingBounds(
            List<Variable<T>> orderedVariables, Map<Variable<?>, Domain<?>> domains) {
        int n = orderedVariables.size();
        List<T> mins = new ArrayList<>(n);
        List<T> maxs = new ArrayList<>(n);
        for (Variable<T> v : orderedVariables) {
            Domain<T> d = (Domain<T>) domains.get(v);
            mins.add(ComparableBounds.min(d));
            maxs.add(ComparableBounds.max(d));
        }

        List<T> newMins = new ArrayList<>(n);
        int[] minSource = new int[n];
        T floor = null;
        int floorIdx = -1;
        for (int i = 0; i < n; i++) {
            if (floor == null || mins.get(i).compareTo(floor) >= 0) {
                floor = mins.get(i);
                floorIdx = i;
            }
            newMins.add(floor);
            minSource[i] = floorIdx;
        }

        List<T> newMaxs = new ArrayList<>(Collections.nCopies(n, (T) null));
        int[] maxSource = new int[n];
        T ceiling = null;
        int ceilIdx = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (ceiling == null || maxs.get(i).compareTo(ceiling) <= 0) {
                ceiling = maxs.get(i);
                ceilIdx = i;
            }
            newMaxs.set(i, ceiling);
            maxSource[i] = ceilIdx;
        }

        return new ChainBounds<>(newMins, newMaxs, minSource, maxSource);
    }

    /** Each position's tightened bounds for a strictly increasing chain. */
    record StrictChainBounds<T extends Comparable<T>>(List<T> newMins, List<T> newMaxs) {}

    /**
     * Bounds consistency for a strictly increasing chain over {@code orderedVariables}: each
     * position's lower bound is the smallest value of its domain above the previous position's,
     * and each upper bound the largest value below the next position's. Empty when some position
     * has no such value.
     */
    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> Optional<StrictChainBounds<T>> strictlyIncreasingBounds(
            List<Variable<T>> orderedVariables, Map<Variable<?>, Domain<?>> domains) {
        int n = orderedVariables.size();
        List<T> newMins = new ArrayList<>(n);
        T floor = null;
        for (Variable<T> v : orderedVariables) {
            Domain<T> d = (Domain<T>) domains.get(v);
            floor = floor == null ? ComparableBounds.min(d) : above(d, floor);
            if (floor == null) return Optional.empty();
            newMins.add(floor);
        }
        // The floors just found are a strictly increasing chain within the domains, so every
        // position has a value below the next position's ceiling: this pass cannot fail.
        List<T> newMaxs = new ArrayList<>(Collections.nCopies(n, (T) null));
        T ceiling = null;
        for (int i = n - 1; i >= 0; i--) {
            Domain<T> d = (Domain<T>) domains.get(orderedVariables.get(i));
            ceiling = ceiling == null ? ComparableBounds.max(d) : below(d, ceiling);
            newMaxs.set(i, ceiling);
        }
        return Optional.of(new StrictChainBounds<>(newMins, newMaxs));
    }

    /** The smallest value of {@code d} strictly above {@code x}, or null if none. */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> @Nullable T above(Domain<T> d, T x) {
        T min = ComparableBounds.min(d);
        if (min.compareTo(x) > 0) return min;
        if (d instanceof BoundedDomain<?> bounded) {
            double next = Math.nextUp(((Number) x).doubleValue());
            return next <= bounded.getMax().doubleValue() ? (T) Double.valueOf(next) : null;
        }
        return ((DiscreteDomain<T>) d).stream().filter(v -> v.compareTo(x) > 0).min(Comparator.naturalOrder()).orElse(null);
    }

    /** The largest value of {@code d} strictly below {@code x}, given that {@code d} has one. */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T below(Domain<T> d, T x) {
        T max = ComparableBounds.max(d);
        if (max.compareTo(x) < 0) return max;
        if (d instanceof BoundedDomain<?>) return (T) Double.valueOf(Math.nextDown(((Number) x).doubleValue()));
        return ((DiscreteDomain<T>) d).stream().filter(v -> v.compareTo(x) < 0).max(Comparator.naturalOrder()).orElseThrow();
    }
}
