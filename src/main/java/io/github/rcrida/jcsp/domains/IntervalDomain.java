package io.github.rcrida.jcsp.domains;

import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A continuous domain of {@code double} values in the inclusive range {@code [min, max]}.
 * <p>
 * Unlike {@link DomainObjectSet}-based domains, values are not enumerated: {@link #stream()}
 * and {@link #toBuilder()} are unsupported. Narrowing is performed via {@link #withBounds}.
 * Suitable for use with {@link io.github.rcrida.jcsp.constraints.nary.SumConstraint} and
 * {@link io.github.rcrida.jcsp.constraints.nary.LinearConstraint} bounds propagation only.
 */
@EqualsAndHashCode
public final class IntervalDomain implements BoundedDomain<Double> {
    private final double min;
    private final double max;

    private IntervalDomain(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public static IntervalDomain of(double min, double max) {
        assert min <= max : String.format("min (%s) must be less than or equal to max (%s)", min, max);
        return new IntervalDomain(min, max);
    }

    @Override
    public Double getMin() {
        return min;
    }

    @Override
    public Double getMax() {
        return max;
    }

    @Override
    public Domain<Double> withBounds(@NonNull Double newMin, @NonNull Double newMax) {
        return new IntervalDomain(Math.max(min, newMin), Math.min(max, newMax));
    }

    @Override
    public boolean contains(@Nullable Object value) {
        return value instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max;
    }

    @Override
    public boolean isEmpty() {
        return min > max;
    }

    @Override
    public int size() {
        return isSingleton() ? 1 : Integer.MAX_VALUE;
    }

    @Override
    public boolean isSingleton() {
        return min == max;
    }

    @Override
    public Optional<Double> singleValue() {
        return isSingleton() ? Optional.of(min) : Optional.empty();
    }

    @Override
    public String toString() {
        return "[" + min + ", " + max + "]";
    }
}
