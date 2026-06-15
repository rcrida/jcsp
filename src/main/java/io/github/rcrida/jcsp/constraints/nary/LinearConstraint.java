package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
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
public class LinearConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

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
        return (bound instanceof Double) ? propagateDouble(domains) : propagateInt(domains);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> propagateInt(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        int[] coeffs = new int[n];
        int[] minContribs = new int[n];
        int[] maxContribs = new int[n];

        for (int i = 0; i < n; i++) {
            coeffs[i] = coefficients.get(vars.get(i)).intValue();
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            int domMin = dom.stream().mapToInt(Number::intValue).min().orElseThrow();
            int domMax = dom.stream().mapToInt(Number::intValue).max().orElseThrow();
            minContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMin : coeffs[i] * domMax;
            maxContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMax : coeffs[i] * domMin;
        }

        int totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }
        int k = bound.intValue();

        if ((operator == Operator.EQ  && (k < totalMin || k > totalMax)) ||
            (operator == Operator.LEQ && k < totalMin) ||
            (operator == Operator.GEQ && k > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (coeffs[i] == 0) continue;
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            int restMin = totalMin - minContribs[i];
            int restMax = totalMax - maxContribs[i];

            // c_i * v_i <= k - restMin  (from LEQ/EQ upper bound)
            // c_i * v_i >= k - restMax  (from GEQ/EQ lower bound)
            int newMin, newMax;
            if (coeffs[i] > 0) {
                newMax = (operator != Operator.GEQ) ? Math.floorDiv(k - restMin, coeffs[i]) : Integer.MAX_VALUE;
                newMin = (operator != Operator.LEQ) ? Math.ceilDiv(k - restMax, coeffs[i])  : Integer.MIN_VALUE;
            } else {
                // Negative coefficient reverses inequality direction
                newMin = (operator != Operator.GEQ) ? Math.ceilDiv(k - restMin, coeffs[i])  : Integer.MIN_VALUE;
                newMax = (operator != Operator.LEQ) ? Math.floorDiv(k - restMax, coeffs[i]) : Integer.MAX_VALUE;
            }

            Domain.Builder<N> builder = null;
            for (N val : dom.toList()) {
                int v = val.intValue();
                if (v < newMin || v > newMax) {
                    if (builder == null) builder = dom.toBuilder();
                    builder.delete(val);
                }
            }
            if (builder != null) {
                Domain<N> pruned = builder.build();
                if (pruned.isEmpty()) return Optional.empty();
                updated.put(vars.get(i), pruned);
            }
        }
        return Optional.of(updated);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<Variable<?>, Domain<?>>> propagateDouble(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        double[] coeffs = new double[n];
        double[] minContribs = new double[n];
        double[] maxContribs = new double[n];

        for (int i = 0; i < n; i++) {
            coeffs[i] = coefficients.get(vars.get(i)).doubleValue();
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            double domMin = NumericBounds.min(dom);
            double domMax = NumericBounds.max(dom);
            minContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMin : coeffs[i] * domMax;
            maxContribs[i] = coeffs[i] >= 0 ? coeffs[i] * domMax : coeffs[i] * domMin;
        }

        double totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }
        double k = bound.doubleValue();

        if ((operator == Operator.EQ  && (k < totalMin || k > totalMax)) ||
            (operator == Operator.LEQ && k < totalMin) ||
            (operator == Operator.GEQ && k > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (coeffs[i] == 0) continue;
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            double restMin = totalMin - minContribs[i];
            double restMax = totalMax - maxContribs[i];

            // c_i * v_i <= k - restMin  (from LEQ/EQ upper bound)
            // c_i * v_i >= k - restMax  (from GEQ/EQ lower bound)
            double newMin, newMax;
            if (coeffs[i] > 0) {
                newMax = (operator != Operator.GEQ) ? (k - restMin) / coeffs[i] : Double.POSITIVE_INFINITY;
                newMin = (operator != Operator.LEQ) ? (k - restMax) / coeffs[i] : Double.NEGATIVE_INFINITY;
            } else {
                // Negative coefficient reverses inequality direction
                newMin = (operator != Operator.GEQ) ? (k - restMin) / coeffs[i] : Double.NEGATIVE_INFINITY;
                newMax = (operator != Operator.LEQ) ? (k - restMax) / coeffs[i] : Double.POSITIVE_INFINITY;
            }

            var pruned = NumericBounds.narrow(dom, newMin, newMax);
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(vars.get(i), pruned.get());
            }
        }
        return Optional.of(updated);
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
