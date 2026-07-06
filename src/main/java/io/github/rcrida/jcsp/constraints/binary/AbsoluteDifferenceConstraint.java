package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
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
 * Propagation applies interval-arithmetic bounds narrowing via {@link NumericBounds}, which
 * narrows both {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
 * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) sides via {@code withBounds} and
 * discrete sides via value deletion — so a plain discrete/discrete pair gets real pruning
 * too, not only mixed discrete/bounded ones:
 * <ul>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}: clips both domains symmetrically —
 *       {@code x ∈ [y.min − d, y.max + d]} and {@code y ∈ [x.min − d, x.max + d]}.</li>
 *   <li>{@link Operator#EQ}: same narrowing plus infeasibility detection when the
 *       maximum achievable distance falls below {@code d}.</li>
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: infeasibility detection only —
 *       returns empty when no (x, y) pair can achieve the required distance; the feasible
 *       region excludes a middle band, so it can't be expressed as a simple bounds clip.</li>
 *   <li>{@link Operator#NEQ}: skipped (delegated to AC3).</li>
 * </ul>
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
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (operator == Operator.NEQ) return Optional.of(Map.of());
        Domain<N> lDomain = (Domain<N>) domains.get(getLeft());
        Domain<N> rDomain = (Domain<N>) domains.get(getRight());

        double d = bound.doubleValue();
        double lMin = NumericBounds.min(lDomain);
        double lMax = NumericBounds.max(lDomain);
        double rMin = NumericBounds.min(rDomain);
        double rMax = NumericBounds.max(rDomain);

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
        Optional<Domain<N>> prunedL = NumericBounds.narrow(lDomain, newLMin, newLMax);
        if (prunedL.isPresent()) {
            if (prunedL.get().isEmpty()) return Optional.empty();
            updated.put(getLeft(), prunedL.get());
        }
        Optional<Domain<N>> prunedR = NumericBounds.narrow(rDomain, newRMin, newRMax);
        if (prunedR.isPresent()) {
            if (prunedR.get().isEmpty()) return Optional.empty();
            updated.put(getRight(), prunedR.get());
        }
        return Optional.of(updated);
    }

    /**
     * When bounds narrowing empties the feasible range, attributes the conflict to whichever
     * side already holds a singleton domain — the other side is omitted since no single value
     * can be blamed for it. Empty when neither side is singleton. Structurally mirrors
     * {@link BinaryComparatorConstraint#explainInfeasible}, but unlike that constraint,
     * {@code propagate()} here narrows discrete/discrete pairs too (via
     * {@link NumericBounds#narrow}, not just
     * {@link io.github.rcrida.jcsp.domains.BoundedDomain#withBounds}), so this method's
     * infeasible branch is reachable for plain discrete pairs as well, not only mixed
     * discrete/bounded ones.
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(domains.get(getLeft()), getLeft(), reason);
        Propagatable.addIfSingleton(domains.get(getRight()), getRight(), reason);
        return reason;
    }
}
