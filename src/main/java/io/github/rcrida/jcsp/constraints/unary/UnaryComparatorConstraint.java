package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Unary constraint that compares a number variable to a fixed value using an {@link Operator}.
 * Satisfied when {@code variable <op> value}, e.g. {@code x >= 3} or {@code x != 0}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryComparatorConstraint<N extends Number & Comparable<N>> extends UnaryConstraint<N> {
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
}
