package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.ComparableBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.NumericDomain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
}
