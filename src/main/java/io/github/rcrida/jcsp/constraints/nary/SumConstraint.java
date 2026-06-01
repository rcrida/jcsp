package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the sum of a set of numeric variables to a fixed bound
 * using a specified {@link Operator}: {@code v1 + v2 + ... + vn <op> bound}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SumConstraint<N extends Number> extends UniformNaryConstraint<N> {
    @NonNull private final N bound;
    @NonNull private final Operator operator;

    public static <N extends Number> SumConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                         @NonNull Operator operator,
                                                         @NonNull N bound) {
        return SumConstraint.<N>builder()
                .variables(variables)
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<N> values) {
        if (values.size() < getVariables().size()) return true;
        return operator.compare(sum(values), bound);
    }

    @SuppressWarnings("unchecked")
    private N sum(Collection<N> values) {
        return switch (bound) {
            case Byte b    -> (N) (Number)(byte)  values.stream().mapToInt(Number::intValue).sum();
            case Short s   -> (N) (Number)(short) values.stream().mapToInt(Number::intValue).sum();
            case Integer i -> (N) (Number)        values.stream().mapToInt(Number::intValue).sum();
            case Long l    -> (N) (Number)        values.stream().mapToLong(Number::longValue).sum();
            case Float f -> {
                float s = 0f;
                for (N v : values) s += v.floatValue();
                yield (N) (Number) s;
            }
            case Double d  -> (N) (Number)        values.stream().mapToDouble(Number::doubleValue).sum();
            default -> throw new IllegalStateException("Unsupported bound type: " + bound.getClass());
        };
    }

    @Override
    public String getRelation() {
        String varSum = getVariables().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(" + "));
        return varSum + " " + operator.symbol + " " + bound;
    }
}
