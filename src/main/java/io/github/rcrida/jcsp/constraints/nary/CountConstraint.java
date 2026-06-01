package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Set;

/**
 * An n-ary constraint that counts how many variables in a set take a specific value,
 * and compares that count to a bound using a specified {@link Operator}:
 * {@code count(vars, value) <op> n}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CountConstraint<T> extends UniformNaryConstraint<T> {
    @NonNull private final T value;
    @NonNull private final Operator operator;
    private final int n;

    public static <T> CountConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                            @NonNull T value,
                                            @NonNull Operator operator,
                                            int n) {
        return CountConstraint.<T>builder()
                .variables(variables)
                .value(value)
                .operator(operator)
                .n(n)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        if (values.size() < getVariables().size()) return true;
        int count = (int) values.stream().filter(value::equals).count();
        return operator.compare(count, n);
    }

    @Override
    public String getRelation() {
        return "count(" + value + ") " + operator.symbol + " " + n;
    }
}
