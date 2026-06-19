package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Unary constraint that compares a number variable to a fixed value using an {@link Operator}.
 * Satisfied when {@code variable <op> value}, e.g. {@code x >= 3} or {@code x != 0}.
 * Implements {@link Propagatable} to clip {@link BoundedDomain} bounds; discrete domains are
 * handled by {@link io.github.rcrida.jcsp.consistency.node.NodeConsistency}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryComparatorConstraint<N extends Number & Comparable<N>> extends UnaryConstraint<N> implements Propagatable {
    @NonNull N value;
    @NonNull Operator operator;

    public static <N extends Number & Comparable<N>> UnaryComparatorConstraint<N> of(
            @NonNull Variable<N> variable, @NonNull Operator operator, @NonNull N value) {
        return UnaryComparatorConstraint.<N>builder()
                .variable(variable).operator(operator).value(value).build();
    }

    @Override
    protected boolean checkValue(@NonNull N v) {
        return operator.compare(v, value);
    }

    @Override
    public String getRelation() {
        return getVariable() + " " + operator.symbol + " " + value;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        Domain<?> domain = domains.get(getVariable());
        if (!(domain instanceof BoundedDomain<?> bounded)) return Optional.of(Map.of());
        double lo = bounded.getMin().doubleValue();
        double hi = bounded.getMax().doubleValue();
        double v = value.doubleValue();
        double newMin = (operator == Operator.GEQ || operator == Operator.GT || operator == Operator.EQ) ? Math.max(lo, v) : lo;
        double newMax = (operator == Operator.LEQ || operator == Operator.LT || operator == Operator.EQ) ? Math.min(hi, v) : hi;
        if (newMin > newMax) return Optional.empty();
        if (newMin == lo && newMax == hi) return Optional.of(Map.of());
        BoundedDomain raw = bounded;
        return Optional.of(Map.of(getVariable(), raw.withBounds(newMin, newMax)));
    }
}
