package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
public class LinearBoundConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @Getter @NonNull private final Map<Variable<N>, N> coefficients;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final N bound;

    public static <N extends Number> LinearBoundConstraint<N> of(@NonNull Map<Variable<N>, N> coefficients,
                                                            @NonNull Operator operator,
                                                            @NonNull N bound) {
        return LinearBoundConstraint.<N>builder()
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

    /**
     * Bounds propagation for weighted sums. For each variable {@code v_i} with coefficient {@code c_i},
     * computes the tightest domain bounds consistent with the constraint given the current domains of
     * all other variables. Negative coefficients flip the min/max contributions and reverse the bound
     * direction when deriving per-variable limits. Only applied for EQ, LEQ, and GEQ operators.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }
        return (bound instanceof Double || bound instanceof Float) ? propagateDouble(domains) : propagateInt(domains);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> propagateInt(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int[] coeffs = new int[vars.size()];
        for (int i = 0; i < vars.size(); i++) coeffs[i] = coefficients.get(vars.get(i)).intValue();
        return LinearBoundPropagation.propagateInt(vars, coeffs, bound.intValue(), operator, domains);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> propagateDouble(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        double[] coeffs = new double[vars.size()];
        for (int i = 0; i < vars.size(); i++) coeffs[i] = coefficients.get(vars.get(i)).doubleValue();
        return LinearBoundPropagation.propagateDouble(vars, coeffs, bound.doubleValue(), operator, domains);
    }

    /**
     * On infeasibility, the weighted sum's violation depends on the combined contribution of
     * every variable, not any single variable in isolation — unlike {@link MaxConstraint}/
     * {@link MinConstraint}, a weighted sum has no monotonic "one value alone already breaks the
     * bound" case. {@link #propagate}'s own infeasibility test ({@code totalMin}/{@code totalMax}
     * against {@link #bound}) only ever depends on each variable's current domain bounds, not on
     * any variable being singleton, so {@link RangeNogoodConstraint#fromCurrentBounds} is tried
     * first, falling back to {@link Propagatable#allSingletonReason}'s fully collective ground
     * reason only when it can't safely cite some variable's domain as a range.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
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
