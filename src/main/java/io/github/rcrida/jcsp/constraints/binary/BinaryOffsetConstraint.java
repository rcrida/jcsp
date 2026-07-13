package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a binary constraint with an offset applied to the left operand.
 * This class is an extension of the {@link BinaryConstraint} and allows for a numerical offset
 * adjustment to the left operand before comparison using a specified {@link Operator}.
 * <p>
 * The constraint is considered satisfied if the condition defined by the operator
 * holds true after the offset is applied to the left operand and compared to the right operand.
 * If either operand is null, the constraint is treated as implicitly satisfied.
 * <p>
 * This class supports various numerical types for the offset, including {@code Byte}, {@code Short},
 * {@code Integer}, {@code Long}, {@code Float}, and {@code Double}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryOffsetConstraint<N extends Number> extends BinaryConstraint<N, N> implements Propagatable {
    @NonNull Number offset;
    @NonNull Operator operator;

    public static <N extends Number> BinaryOffsetConstraint<N> of(@NonNull Variable<N> left, @NonNull N offset,
                                                                  @NonNull Operator operator, @NonNull Variable<N> right) {
        return BinaryOffsetConstraint.<N>builder().left(left).right(right).operator(operator).offset(offset).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull N left, @NonNull N right) {
        return operator.compare(offsetValue(left), right);
    }

    @Override
    public String getRelation() {
        return isOffsetNegative()
                ? String.format("%s - %s %s %s", getLeft(), negatedOffset(), operator.symbol, getRight())
                : String.format("%s + %s %s %s", getLeft(), offset, operator.symbol, getRight());
    }

    Number negatedOffset() {
        return switch (offset) {
            case Byte b -> (byte) -b;
            case Short s -> (short) -s;
            case Integer i -> -i;
            case Long l -> -l;
            case Float f -> -f;
            case Double d -> -d;
            default -> throw new IllegalStateException("Unsupported offset type: " + offset.getClass());
        };
    }

    private boolean isOffsetNegative() {
        return switch (offset) {
            case Byte b -> b < 0;
            case Short s -> s < 0;
            case Integer i -> i < 0;
            case Long l -> l < 0L;
            case Float f -> f < 0.0f;
            case Double d -> d < 0.0;
            default -> throw new IllegalStateException("Unsupported offset type: " + offset.getClass());
        };
    }

    private Number offsetValue(@NonNull N value) {
        return switch (value) {
            case Byte b -> (byte) (b + offset.byteValue());
            case Short s -> (short) (s + offset.shortValue());
            case Integer i -> i + offset.intValue();
            case Long l -> l + offset.longValue();
            case Float f -> f + offset.floatValue();
            case Double d -> d + offset.doubleValue();
            default -> throw new IllegalStateException("Unsupported value type: " + value.getClass());
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (operator == Operator.NEQ) return Optional.of(Map.of());
        Domain<N> lDomain = (Domain<N>) domains.get(getLeft());
        Domain<N> rDomain = (Domain<N>) domains.get(getRight());
        double o = offset.doubleValue();
        double lMin = NumericBounds.min(lDomain), lMax = NumericBounds.max(lDomain);
        double rMin = NumericBounds.min(rDomain), rMax = NumericBounds.max(rDomain);
        double newLMin = lMin, newLMax = lMax, newRMin = rMin, newRMax = rMax;
        if (operator == Operator.LEQ || operator == Operator.LT) {
            newLMax = Math.min(lMax, rMax - o);
            newRMin = Math.max(rMin, lMin + o);
        } else if (operator == Operator.GEQ || operator == Operator.GT) {
            newLMin = Math.max(lMin, rMin - o);
            newRMax = Math.min(rMax, lMax + o);
        } else { // EQ
            newLMin = Math.max(lMin, rMin - o);
            newLMax = Math.min(lMax, rMax - o);
            newRMin = Math.max(rMin, lMin + o);
            newRMax = Math.min(rMax, lMax + o);
        }
        if (newLMin > newLMax) return Optional.empty();

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
     * {@link io.github.rcrida.jcsp.constraints.NumericBounds#narrow}, not just
     * {@link io.github.rcrida.jcsp.domains.BoundedDomain#withBounds}), so this method's
     * infeasible branch is reachable for plain discrete pairs as well, not only mixed
     * discrete/bounded ones.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(domains.get(getLeft()), getLeft(), reason);
        Propagatable.addIfSingleton(domains.get(getRight()), getRight(), reason);
        return GroundNogoodConstraint.fromReason(reason);
    }
}
