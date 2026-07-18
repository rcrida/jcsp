package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared bounds-consistency computation for {@link IncreasingConstraint}/{@link DecreasingConstraint},
 * generic over any {@code T extends Comparable<T>} — matching those constraints' own type bound
 * rather than narrowing to {@link Number} the way {@code BinaryComparatorConstraint} does. A
 * non-decreasing chain {@code v[0] <= v[1] <= ... <= v[n-1]} is fully bounds-consistent by
 * computing, independently, a running maximum of minimums left-to-right (transitivity means each
 * {@code v[i].min} must be at least every earlier variable's min) and a running minimum of maximums
 * right-to-left (the dual, for maxes) — one pass each direction, rather than iterating the pairwise
 * decomposition to a fixpoint. {@link DecreasingConstraint} reduces to the same computation over its
 * reversed variable list, since {@code v[0] >= v[1] >= ... >= v[n-1]} holds iff the reverse is
 * non-decreasing.
 * <p>
 * Works uniformly over {@link BoundedDomain} (via {@code getMin}/{@code getMax}/{@code withBounds},
 * all already {@code T}-typed — no {@code double} conversion needed) and {@link DiscreteDomain}
 * (via natural ordering and value deletion), so an ordering over non-numeric {@code Comparable}
 * types (e.g. {@link String} or enum variables) gets real propagation too, not just numeric chains.
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

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T min(Domain<T> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return (T) bounded.getMin();
        return ((DiscreteDomain<T>) domain).stream().min(Comparator.<T>naturalOrder()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T max(Domain<T> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return (T) bounded.getMax();
        return ((DiscreteDomain<T>) domain).stream().max(Comparator.<T>naturalOrder()).orElseThrow();
    }

    /**
     * Narrows {@code domain} to {@code [newMin, newMax]}.
     *
     * @return {@link Optional#empty()} if the domain is unchanged, otherwise the narrowed
     *         domain (which may itself be {@link Domain#isEmpty() empty}, signalling infeasibility)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T extends Comparable<T>> Optional<Domain<T>> narrow(Domain<T> domain, T newMin, T newMax) {
        if (domain instanceof BoundedDomain<?> bounded) {
            T curMin = (T) bounded.getMin();
            T curMax = (T) bounded.getMax();
            T lo = curMin.compareTo(newMin) >= 0 ? curMin : newMin;
            T hi = curMax.compareTo(newMax) <= 0 ? curMax : newMax;
            if (lo.equals(curMin) && hi.equals(curMax)) return Optional.empty();
            BoundedDomain raw = bounded;
            // Raw-type erasure of withBounds(T, T) is withBounds(Number, Number) — BoundedDomain's
            // own bound is <T extends Number & Comparable<T>> — but this method's T is only known
            // as Comparable<T> (matching Increasing/DecreasingConstraint's own bound), so lo/hi need
            // an explicit cast to Number: legal since Comparable is a non-final interface that
            // Number subtypes (Integer, Double, ...) do implement, and every BoundedDomain's actual
            // values are one of those subtypes at runtime.
            return Optional.of((Domain<T>) raw.withBounds((Number) lo, (Number) hi));
        }

        DiscreteDomain<T> discrete = (DiscreteDomain<T>) domain;
        DiscreteDomain.Builder<T> builder = null;
        for (T val : discrete.toList()) {
            if (val.compareTo(newMin) < 0 || val.compareTo(newMax) > 0) {
                if (builder == null) builder = discrete.toBuilder();
                builder.delete(val);
            }
        }
        return builder == null ? Optional.empty() : Optional.of(builder.build());
    }

    /** Bounds consistency for a non-decreasing chain over {@code orderedVariables}, in order. */
    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> ChainBounds<T> nonDecreasingBounds(
            List<Variable<T>> orderedVariables, Map<Variable<?>, Domain<?>> domains) {
        int n = orderedVariables.size();
        List<T> mins = new ArrayList<>(n);
        List<T> maxs = new ArrayList<>(n);
        for (Variable<T> v : orderedVariables) {
            Domain<T> d = (Domain<T>) domains.get(v);
            mins.add(min(d));
            maxs.add(max(d));
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
