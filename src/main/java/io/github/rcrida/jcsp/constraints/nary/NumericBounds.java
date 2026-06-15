package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;

import java.util.Optional;

/**
 * Shared bounds extraction and narrowing for {@code double}-based propagation in
 * {@link SumConstraint} and {@link LinearConstraint}. Handles both {@link BoundedDomain}
 * domains (e.g. {@link io.github.rcrida.jcsp.domains.IntervalDomain}), narrowed via
 * {@link BoundedDomain#withBounds}, and plain enumerable domains, narrowed by deleting
 * out-of-range values.
 */
final class NumericBounds {
    private NumericBounds() {}

    static <N extends Number> double min(Domain<N> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return bounded.getMin().doubleValue();
        return domain.stream().mapToDouble(Number::doubleValue).min().orElseThrow();
    }

    static <N extends Number> double max(Domain<N> domain) {
        if (domain instanceof BoundedDomain<?> bounded) return bounded.getMax().doubleValue();
        return domain.stream().mapToDouble(Number::doubleValue).max().orElseThrow();
    }

    /**
     * Narrows {@code domain} to {@code [newMin, newMax]}.
     *
     * @return {@link Optional#empty()} if the domain is unchanged, otherwise the narrowed
     *         domain (which may itself be {@link Domain#isEmpty() empty}, signalling infeasibility)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <N extends Number> Optional<Domain<N>> narrow(Domain<N> domain, double newMin, double newMax) {
        if (domain instanceof BoundedDomain<?> bounded) {
            double curMin = bounded.getMin().doubleValue();
            double curMax = bounded.getMax().doubleValue();
            double lo = Math.max(curMin, newMin);
            double hi = Math.min(curMax, newMax);
            if (lo == curMin && hi == curMax) return Optional.empty();
            BoundedDomain raw = bounded;
            return Optional.of((Domain<N>) raw.withBounds(lo, hi));
        }

        Domain.Builder<N> builder = null;
        for (N val : domain.toList()) {
            double v = val.doubleValue();
            if (v < newMin || v > newMax) {
                if (builder == null) builder = domain.toBuilder();
                builder.delete(val);
            }
        }
        return builder == null ? Optional.empty() : Optional.of(builder.build());
    }
}
