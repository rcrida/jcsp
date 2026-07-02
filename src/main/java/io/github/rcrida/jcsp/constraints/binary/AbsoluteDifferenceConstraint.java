package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A binary constraint enforcing {@code |left - right| op bound} over numeric variables.
 * <p>
 * For {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
 * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) variables, propagation applies
 * interval-arithmetic bounds narrowing:
 * <ul>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}: clips both domains symmetrically —
 *       {@code x ∈ [y.min − d, y.max + d]} and {@code y ∈ [x.min − d, x.max + d]}.</li>
 *   <li>{@link Operator#EQ}: same narrowing plus infeasibility detection when the
 *       maximum achievable distance falls below {@code d}.</li>
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: infeasibility detection only —
 *       returns empty when no (x, y) pair can achieve the required distance.</li>
 *   <li>{@link Operator#NEQ}: skipped (delegated to AC3 for discrete domains).</li>
 * </ul>
 * Mixed bounded/discrete pairs are supported via {@link NumericBounds}; only
 * {@link BoundedDomain} sides are narrowed.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AbsoluteDifferenceConstraint<N extends Number> extends BinaryConstraint<N, N> implements Propagatable {
    @NonNull Number bound;
    @NonNull Operator operator;

    public static <N extends Number> AbsoluteDifferenceConstraint<N> of(
            @NonNull Variable<N> left, @NonNull Variable<N> right,
            @NonNull Operator operator, @NonNull N bound) {
        return AbsoluteDifferenceConstraint.<N>builder()
                .left(left).right(right).operator(operator).bound(bound).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull N leftValue, @NonNull N rightValue) {
        double diff = Math.abs(leftValue.doubleValue() - rightValue.doubleValue());
        return operator.compare(diff, bound.doubleValue());
    }

    @Override
    public String getRelation() {
        return String.format("|%s - %s| %s %s", getLeft(), getRight(), operator.symbol, bound);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (operator == Operator.NEQ) return Optional.of(Map.of());
        Domain<?> lDomain = domains.get(getLeft());
        Domain<?> rDomain = domains.get(getRight());
        boolean lBounded = lDomain instanceof BoundedDomain<?>;
        boolean rBounded = rDomain instanceof BoundedDomain<?>;
        if (!lBounded && !rBounded) return Optional.of(Map.of());

        double d = bound.doubleValue();
        double lMin = NumericBounds.min((Domain<N>) lDomain);
        double lMax = NumericBounds.max((Domain<N>) lDomain);
        double rMin = NumericBounds.min((Domain<N>) rDomain);
        double rMax = NumericBounds.max((Domain<N>) rDomain);

        if (operator == Operator.GEQ || operator == Operator.GT) {
            double maxDist = Math.max(lMax - rMin, rMax - lMin);
            // GT requires strictly greater: equal max-distance is infeasible for GT but not GEQ
            if (operator == Operator.GT ? maxDist <= d : maxDist < d) return Optional.empty();
            return Optional.of(Map.of());
        }

        // LEQ, LT, EQ: |x−y| ≤ d ⟺ y−d ≤ x ≤ y+d (and symmetric for y)
        double newLMin = Math.max(lMin, rMin - d);
        double newLMax = Math.min(lMax, rMax + d);
        double newRMin = Math.max(rMin, lMin - d);
        double newRMax = Math.min(rMax, lMax + d);

        if (newLMin > newLMax) return Optional.empty();

        if (operator == Operator.EQ) {
            double maxDist = Math.max(lMax - rMin, rMax - lMin);
            if (maxDist < d) return Optional.empty();
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (lBounded && (newLMin != lMin || newLMax != lMax)) {
            BoundedDomain raw = (BoundedDomain) lDomain;
            updated.put(getLeft(), raw.withBounds(newLMin, newLMax));
        }
        if (rBounded && (newRMin != rMin || newRMax != rMax)) {
            BoundedDomain raw = (BoundedDomain) rDomain;
            updated.put(getRight(), raw.withBounds(newRMin, newRMax));
        }
        return Optional.of(updated);
    }

    /**
     * When bounds narrowing empties the feasible range, attributes the conflict to whichever
     * side already holds a singleton domain — the other side is omitted since no single value
     * can be blamed for it. Empty when neither side is singleton. Mirrors
     * {@link BinaryComparatorConstraint#explainInfeasible}; see its javadoc for the narrow
     * scope of this benefit (mixed discrete/bounded pairs during search).
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(domains.get(getLeft()), getLeft(), reason);
        Propagatable.addIfSingleton(domains.get(getRight()), getRight(), reason);
        return reason;
    }
}
