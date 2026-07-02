package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class SumConstraint<N extends Number> extends UniformNaryConstraint<N> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);
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

    /**
     * Bounds propagation: for each variable, tightens its domain to the range of values that
     * could still participate in a satisfying sum, given the current domains of all other variables.
     * Only applied for EQ, LEQ, and GEQ operators; other operators return an empty update map.
     *
     * @param domains current variable domains
     * @return updated domains for variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is provably infeasible
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }
        return (bound instanceof Double || bound instanceof Float) ? propagateDouble(domains) : propagateInt(domains);
    }

    @SuppressWarnings({"unchecked"})
    private Optional<Map<Variable<?>, Domain<?>>> propagateInt(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        int[] mins = new int[n];
        int[] maxs = new int[n];
        for (int i = 0; i < n; i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            mins[i] = dom.stream().mapToInt(Number::intValue).min().orElseThrow();
            maxs[i] = dom.stream().mapToInt(Number::intValue).max().orElseThrow();
        }
        int totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += mins[i]; totalMax += maxs[i]; }
        int k = bound.intValue();

        if ((operator == Operator.EQ  && (k < totalMin || k > totalMax)) ||
            (operator == Operator.LEQ && k < totalMin) ||
            (operator == Operator.GEQ && k > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(vars.get(i));
            // Upper bound: k - (sum of minimums of other variables)
            int newMax = (operator != Operator.GEQ) ? k - (totalMin - mins[i]) : Integer.MAX_VALUE;
            // Lower bound: k - (sum of maximums of other variables)
            int newMin = (operator != Operator.LEQ) ? k - (totalMax - maxs[i]) : Integer.MIN_VALUE;

            DiscreteDomain.Builder<N> builder = null;
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

    @SuppressWarnings({"unchecked"})
    private Optional<Map<Variable<?>, Domain<?>>> propagateDouble(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            mins[i] = NumericBounds.min(dom);
            maxs[i] = NumericBounds.max(dom);
        }
        double totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += mins[i]; totalMax += maxs[i]; }
        double k = bound.doubleValue();

        if ((operator == Operator.EQ  && (k < totalMin || k > totalMax)) ||
            (operator == Operator.LEQ && k < totalMin) ||
            (operator == Operator.GEQ && k > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            // Upper bound: k - (sum of minimums of other variables)
            double newMax = (operator != Operator.GEQ) ? k - (totalMin - mins[i]) : Double.POSITIVE_INFINITY;
            // Lower bound: k - (sum of maximums of other variables)
            double newMin = (operator != Operator.LEQ) ? k - (totalMax - maxs[i]) : Double.NEGATIVE_INFINITY;

            var pruned = NumericBounds.narrow(dom, newMin, newMax);
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(vars.get(i), pruned.get());
            }
        }
        return Optional.of(updated);
    }

    /**
     * On infeasibility, the sum's violation depends on the combined total of every variable, not
     * any single variable in isolation — unlike {@link MaxConstraint}/{@link MinConstraint}, sum
     * has no monotonic "one value alone already breaks the bound" case. See
     * {@link Propagatable#allSingletonReason} for why the fully collective explanation is the
     * only sound, self-contained one.
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return Propagatable.allSingletonReason(getVariables(), domains);
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
