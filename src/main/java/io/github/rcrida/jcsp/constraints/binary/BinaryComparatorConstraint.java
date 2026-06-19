package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
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
 * A binary constraint that compares two variables of the same type using an {@link Operator}:
 * {@code left <op> right}, e.g. {@code v1 <= v2} or {@code v1 != v2}.
 * <p>
 * Works with any {@link Comparable} type — not limited to {@link Number} like
 * {@link BinaryOffsetConstraint}. Useful as the binary decomposition of ordering
 * constraints such as {@link io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint}.
 * Implements {@link Propagatable} to clip {@link BoundedDomain} bounds when both variables
 * have bounded domains; discrete domains are handled by AC3.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryComparatorConstraint<T extends Comparable<T>> extends BinaryConstraint<T, T> implements Propagatable {
    @NonNull private final Operator operator;

    public static <T extends Comparable<T>> BinaryComparatorConstraint<T> of(
            @NonNull Variable<T> left, @NonNull Operator operator, @NonNull Variable<T> right) {
        return BinaryComparatorConstraint.<T>builder()
                .left(left).operator(operator).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T leftValue, @NonNull T rightValue) {
        return operator.compare(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return getLeft() + " " + operator.symbol + " " + getRight();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (!(domains.get(getLeft()) instanceof BoundedDomain<?> lb)
                || !(domains.get(getRight()) instanceof BoundedDomain<?> rb)) {
            return Optional.of(Map.of());
        }
        double lMin = lb.getMin().doubleValue(), lMax = lb.getMax().doubleValue();
        double rMin = rb.getMin().doubleValue(), rMax = rb.getMax().doubleValue();
        double newLMin = lMin, newLMax = lMax, newRMin = rMin, newRMax = rMax;
        if (operator == Operator.NEQ) return Optional.of(Map.of());
        if (operator == Operator.LEQ || operator == Operator.LT) {
            newLMax = Math.min(lMax, rMax);
            newRMin = Math.max(rMin, lMin);
        } else if (operator == Operator.GEQ || operator == Operator.GT) {
            newLMin = Math.max(lMin, rMin);
            newRMax = Math.min(rMax, lMax);
        } else { // EQ
            newLMin = newRMin = Math.max(lMin, rMin);
            newLMax = newRMax = Math.min(lMax, rMax);
        }
        if (newLMin > newLMax) return Optional.empty();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (newLMin != lMin || newLMax != lMax) { BoundedDomain raw = lb; updated.put(getLeft(),  raw.withBounds(newLMin, newLMax)); }
        if (newRMin != rMin || newRMax != rMax) { BoundedDomain raw = rb; updated.put(getRight(), raw.withBounds(newRMin, newRMax)); }
        return Optional.of(updated);
    }
}
