package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A continuous domain of {@code double} values in the inclusive range {@code [min, max]}.
 * <p>
 * Unlike {@link DomainObjectSet}-based domains, values are not enumerated: {@link DiscreteDomain#stream()}
 * and {@link DiscreteDomain#toBuilder()} are unsupported. Narrowing is performed via {@link #withBounds}.
 * <p>
 * Supported by the following constraint types via interval-arithmetic bounds propagation:
 * {@link io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint},
 * {@link io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint},
 * {@link io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint},
 * {@link io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint},
 * {@link io.github.rcrida.jcsp.constraints.binary.DivisionConstraint} (positive domains only),
 * {@link io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.LexConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.MaxConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.MinConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.ProductConstraint} (positive domains only),
 * {@link io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.DiffnConstraint} (origin variables),
 * {@link io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint} (both indirectly: no
 * bounds arithmetic of their own, but when their indicator is forced {@code true} they delegate
 * straight to a {@link io.github.rcrida.jcsp.consistency.Propagatable} body's own
 * {@code propagate}, so a body like {@link io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint}
 * still gets real interval narrowing through the reification/implication).
 * <p>
 * Also accepted, but without dedicated bounds propagation of their own — correctness for these
 * rests on the final {@code isSatisfiedBy} check once every {@code BoundedDomain} variable has
 * been resolved to a concrete point (see {@link io.github.rcrida.jcsp.solver.PropagationFixpointSolver}'s
 * midpoint snapping and {@link io.github.rcrida.jcsp.solver.BisectionConditioningSolver}'s bisection):
 * {@link io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint},
 * {@link io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.PredicateConstraint},
 * {@link io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint}.
 * <p>
 * Any other constraint type referencing an {@code IntervalDomain} variable is rejected at
 * build time with {@link IllegalArgumentException}.
 */
public record IntervalDomain(double min, double max) implements BoundedDomain<Double> {

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
    public IntervalDomain withBounds(@NonNull Double newMin, @NonNull Double newMax) {
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
