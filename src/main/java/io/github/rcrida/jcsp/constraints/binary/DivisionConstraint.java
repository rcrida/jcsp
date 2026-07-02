package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A binary constraint enforcing {@code dividend / divisor op bound} over numeric variables.
 * <p>
 * Uses real-valued (double) division. For partial assignments the constraint is optimistically
 * satisfied — only evaluated once both variables are assigned.
 * <p>
 * Propagation applies interval-arithmetic bounds narrowing for EQ, LEQ, and GEQ operators,
 * but only when both variable domains have strictly positive minimums. Domains that include
 * zero or negative values receive no narrowing (non-monotone division makes tight bounds
 * propagation unsound without case analysis).
 * <p>
 * For {@code x / y op k} with positive domains:
 * <ul>
 *   <li>LEQ pass (EQ/LEQ): clips {@code x.max} to {@code k * y.max} and raises {@code y.min}
 *       to {@code x.min / k}.</li>
 *   <li>GEQ pass (EQ/GEQ): raises {@code x.min} to {@code k * y.min} and clips {@code y.max}
 *       to {@code x.max / k}.</li>
 *   <li>Infeasible when: (LEQ) {@code x.min / y.max > k}; (GEQ) {@code x.max / y.min < k}.</li>
 * </ul>
 * Supports both {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
 * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) and enumerable numeric domains via
 * {@link NumericBounds}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DivisionConstraint<N extends Number> extends BinaryConstraint<N, N> implements Propagatable {

    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull private final N bound;
    @NonNull private final Operator operator;

    public static <N extends Number> DivisionConstraint<N> of(
            @NonNull Variable<N> dividend,
            @NonNull Variable<N> divisor,
            @NonNull Operator operator,
            @NonNull N bound) {
        return DivisionConstraint.<N>builder()
                .left(dividend).right(divisor).operator(operator).bound(bound).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull N dividendValue, @NonNull N divisorValue) {
        double ratio = dividendValue.doubleValue() / divisorValue.doubleValue();
        return operator.compare(ratio, bound.doubleValue());
    }

    @Override
    public String getRelation() {
        return getLeft() + " / " + getRight() + " " + operator.symbol + " " + bound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        Domain<N> xDom = (Domain<N>) domains.get(getLeft());   // dividend
        Domain<N> yDom = (Domain<N>) domains.get(getRight());  // divisor

        double xMin = NumericBounds.min(xDom), xMax = NumericBounds.max(xDom);
        double yMin = NumericBounds.min(yDom), yMax = NumericBounds.max(yDom);
        double k = bound.doubleValue();

        if (xMin <= 0 || yMin <= 0) return Optional.of(Map.of());

        // Early infeasibility: check whether bound is achievable at all
        if ((operator == Operator.EQ || operator == Operator.LEQ) && xMin / yMax > k) return Optional.empty();
        if ((operator == Operator.EQ || operator == Operator.GEQ) && xMax / yMin < k) return Optional.empty();

        // GEQ with k≤0 is trivially satisfied for strictly positive domains (x/y>0≥k); skip narrowing
        // to avoid sign-flip and division unsoundness. EQ/LEQ with k≤0 are already caught above.
        if (k <= 0) return Optional.of(Map.of());

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // LEQ pass: x/y <= k  →  x <= k*y (clip x.max)  and  y >= x/k (raise y.min)
        // Early check guarantees xMin ≤ k*yMax and newYMin ≤ yMax, so narrowed domains are never empty.
        if (operator == Operator.EQ || operator == Operator.LEQ) {
            double newXMax = k * yMax;
            if (newXMax < xMax) {
                xDom = NumericBounds.narrow(xDom, xMin, newXMax).orElseThrow();
                updated.put(getLeft(), xDom);
                xMax = NumericBounds.max(xDom);
            }
            double newYMin = xMin / k;
            if (newYMin > yMin) {
                yDom = NumericBounds.narrow(yDom, newYMin, yMax).orElseThrow();
                updated.put(getRight(), yDom);
                yMin = NumericBounds.min(yDom);
            }
        }

        // GEQ pass: x/y >= k  →  x >= k*y (raise x.min)  and  y <= x/k (clip y.max)
        // The LEQ pass may have clipped xMax below k*yMin, making the x-raise empty (infeasible).
        // If x-raise is non-empty then xMax ≥ k*yMin, so newYMax = xMax/k ≥ yMin and y-clip is never empty.
        if (operator == Operator.EQ || operator == Operator.GEQ) {
            double newXMin = k * yMin;
            if (newXMin > xMin) {
                xDom = NumericBounds.narrow(xDom, newXMin, xMax).orElseThrow();
                if (xDom.isEmpty()) return Optional.empty();
                updated.put(getLeft(), xDom);
            }
            double newYMax = xMax / k;
            if (newYMax < yMax) {
                yDom = NumericBounds.narrow(yDom, yMin, newYMax).orElseThrow();
                updated.put(getRight(), yDom);
            }
        }

        return Optional.of(updated);
    }

    /**
     * When bounds narrowing empties the feasible range, attributes the conflict to whichever
     * side already holds a singleton domain — the other side is omitted since no single value
     * can be blamed for it. Empty when neither side is singleton. Mirrors
     * {@link BinaryComparatorConstraint#explainInfeasible}; see its javadoc for the narrow
     * scope of this benefit (mixed discrete/bounded pairs during search). Applies uniformly to
     * all three infeasibility paths in {@link #propagate} (the two early ratio-bound checks and
     * the inner discrete-gap narrowing), since each depends only on the current dividend/divisor
     * bounds.
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(domains.get(getLeft()), getLeft(), reason);
        Propagatable.addIfSingleton(domains.get(getRight()), getRight(), reason);
        return reason;
    }
}
