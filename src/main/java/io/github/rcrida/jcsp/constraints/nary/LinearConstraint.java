package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares a weighted sum of numeric variables to a fixed bound:
 * {@code a1*v1 + a2*v2 + ... + an*vn <op> bound}.
 * <p>
 * Coefficients and variables are supplied as a {@link Map}. For partial assignments the
 * constraint is optimistically satisfied — only evaluated once all variables are assigned.
 * <p>
 * Equivalent to MiniZinc's {@code linear(coefficients, variables, bound)} constraint.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LinearConstraint<N extends Number> extends NaryConstraint {
    @NonNull private final Map<Variable<N>, N> coefficients;
    @NonNull private final Operator operator;
    @NonNull private final N bound;

    public static <N extends Number> LinearConstraint<N> of(@NonNull Map<Variable<N>, N> coefficients,
                                                            @NonNull Operator operator,
                                                            @NonNull N bound) {
        return LinearConstraint.<N>builder()
                .variables(coefficients.keySet())
                .coefficients(Map.copyOf(coefficients))
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return operator.compare(weightedSum(assignment), bound);
    }

    @SuppressWarnings("unchecked")
    private N weightedSum(Assignment assignment) {
        return switch (bound) {
            case Byte b -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    s += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number)(byte) s;
            }
            case Short s -> {
                int sum = 0;
                for (var e : coefficients.entrySet())
                    sum += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number)(short) sum;
            }
            case Integer i -> {
                int sum = 0;
                for (var e : coefficients.entrySet())
                    sum += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number) sum;
            }
            case Long l -> {
                long sum = 0L;
                for (var e : coefficients.entrySet())
                    sum += e.getValue().longValue() * assignment.getValue(e.getKey()).orElseThrow().longValue();
                yield (N) (Number) sum;
            }
            case Float f -> {
                float sum = 0f;
                for (var e : coefficients.entrySet())
                    sum += e.getValue().floatValue() * assignment.getValue(e.getKey()).orElseThrow().floatValue();
                yield (N) (Number) sum;
            }
            case Double d -> {
                double sum = 0.0;
                for (var e : coefficients.entrySet())
                    sum += e.getValue().doubleValue() * assignment.getValue(e.getKey()).orElseThrow().doubleValue();
                yield (N) (Number) sum;
            }
            default -> throw new IllegalStateException("Unsupported bound type: " + bound.getClass());
        };
    }

    @Override
    public String getRelation() {
        String terms = coefficients.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getValue() + "*" + e.getKey())
                .collect(Collectors.joining(" + "));
        return terms + " " + operator.symbol + " " + bound;
    }
}
