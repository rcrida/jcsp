package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.NumericDomain;

import java.util.Comparator;
import java.util.Optional;

/**
 * Shared bounds extraction and narrowing for any {@code T extends Comparable<T>} — the
 * non-numeric-capable sibling of {@link NumericBounds}, which restricts to {@link Number}. Works
 * uniformly over {@link NumericDomain} ({@code getMin}/{@code getMax} are {@code T}-typed there;
 * {@code withBounds} itself takes {@code double} — see {@link #narrow}) and any other {@link
 * DiscreteDomain} (via natural ordering and value deletion), so a bound like {@link String} or an
 * enum gets real propagation too, not just numeric domains. Extracted from {@link
 * io.github.rcrida.jcsp.constraints.nary.OrderingPropagation}, which was the original
 * Comparable-generic bounds computation in this codebase; that class now delegates here instead of
 * keeping its own copy.
 */
public final class ComparableBounds {
    private ComparableBounds() {}

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> T min(Domain<T> domain) {
        if (domain instanceof NumericDomain<?> numeric) return (T) numeric.getMin();
        return ((DiscreteDomain<T>) domain).stream().min(Comparator.<T>naturalOrder()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> T max(Domain<T> domain) {
        if (domain instanceof NumericDomain<?> numeric) return (T) numeric.getMax();
        return ((DiscreteDomain<T>) domain).stream().max(Comparator.<T>naturalOrder()).orElseThrow();
    }

    /**
     * Narrows {@code domain} to {@code [newMin, newMax]}.
     *
     * @return {@link Optional#empty()} if the domain is unchanged, otherwise the narrowed
     *         domain (which may itself be {@link Domain#isEmpty() empty}, signalling infeasibility)
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> Optional<Domain<T>> narrow(Domain<T> domain, T newMin, T newMax) {
        if (domain instanceof NumericDomain<?> numeric) {
            T curMin = (T) numeric.getMin();
            T curMax = (T) numeric.getMax();
            T lo = curMin.compareTo(newMin) >= 0 ? curMin : newMin;
            T hi = curMax.compareTo(newMax) <= 0 ? curMax : newMax;
            if (lo.equals(curMin) && hi.equals(curMax)) return Optional.empty();
            // T here is only known as Comparable<T>, but withBounds takes double -- widening via
            // Number is legal since Comparable is a non-final interface that Number subtypes
            // (Integer, Double, ...) do implement, and every NumericDomain's actual values are one
            // of those subtypes at runtime.
            double newMinD = ((Number) lo).doubleValue();
            double newMaxD = ((Number) hi).doubleValue();
            return Optional.of((Domain<T>) numeric.withBounds(newMinD, newMaxD));
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
}
