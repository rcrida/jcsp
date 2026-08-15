package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
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
 * An n-ary constraint that compares the maximum value among a set of numeric variables to a fixed bound:
 * {@code max(v1, v2, ..., vn) op bound}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once all
 * variables are assigned.
 * <p>
 * Propagation applies interval-arithmetic bounds narrowing for EQ, LEQ, LT, GEQ, and GT:
 * <ul>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}: clips every variable's upper bound to {@link #bound};
 *       infeasible when any variable's lower bound exceeds {@link #bound}.</li>
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: infeasible when no variable can reach {@link #bound};
 *       when exactly one variable can still reach {@link #bound} its lower bound is raised to {@link #bound}.</li>
 *   <li>{@link Operator#EQ}: combines both upper-clip and lower-force passes.</li>
 *   <li>{@link Operator#NEQ}: skipped (no narrowing applicable).</li>
 * </ul>
 * Both {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
 * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) and discrete domain variables are supported
 * via {@link NumericBounds}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MaxConstraint<N extends Number> extends UniformNaryConstraint<N> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS =
            EnumSet.of(Operator.EQ, Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT);

    @NonNull private final N bound;
    @NonNull private final Operator operator;

    /** Whether the upper-bound pass applies, shared by {@link #propagate} and {@link #explainInfeasible}. */
    private boolean upperPassApplies() {
        return operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.LT;
    }

    /** Whether the lower-bound pass applies, shared by {@link #propagate} and {@link #explainInfeasible}. */
    private boolean lowerPassApplies() {
        return operator == Operator.EQ || operator == Operator.GEQ || operator == Operator.GT;
    }

    public static <N extends Number> MaxConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                         @NonNull Operator operator,
                                                         @NonNull N bound) {
        return MaxConstraint.<N>builder()
                .variables(variables)
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<N> values) {
        if (values.size() < getVariables().size()) return true;
        double max = values.stream().mapToDouble(Number::doubleValue).max().orElseThrow();
        return operator.compare(max, bound.doubleValue());
    }

    /**
     * Bounds propagation for {@code max(vars) op bound}, delegating the shared narrowing math to
     * {@link ExtremumPropagation#narrowMax} — this is the "maximize" direction directly, no
     * coordinate transform needed (that's {@link MinConstraint}'s job).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            mins[i] = NumericBounds.min(dom);
            maxs[i] = NumericBounds.max(dom);
        }

        Optional<double[][]> narrowed = ExtremumPropagation.narrowMax(mins, maxs, bound.doubleValue(), operator);
        if (narrowed.isEmpty()) return Optional.empty();
        double[] newMins = narrowed.get()[0];
        double[] newMaxs = narrowed.get()[1];

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            Optional<Domain<N>> result = NumericBounds.narrow(dom, newMins[i], newMaxs[i]);
            if (result.isEmpty()) continue;
            if (result.get().isEmpty()) return Optional.empty();
            updated.put(vars.get(i), result.get());
        }

        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        String varNames = getVariables().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
        return "max(" + varNames + ") " + operator.symbol + " " + bound;
    }

    /**
     * On infeasibility, tries two independent, always-sound explanations — neither replicates
     * {@link #propagate}'s internal branch order; each is checked directly against the current
     * domains and is valid regardless of which of {@code propagate}'s branches actually detected
     * the conflict (including the discrete-gap corner case, which falls back to an empty reason):
     * <ul>
     *   <li><b>Single culprit</b> (violates the upper bound, EQ/LEQ/LT): any one singleton
     *       variable whose value already exceeds {@link #bound} makes {@code max(vars) op bound}
     *       infeasible by itself, regardless of every other variable — attributed alone as soon
     *       as found.</li>
     *   <li><b>Collective</b> (violates the lower bound, EQ/GEQ/GT): {@code max(vars) >= bound}
     *       needs at least one variable to reach {@link #bound}; only attributable when every
     *       variable is singleton, since a partial subset can't rule out an unlisted open-domain
     *       variable also being unable to reach {@link #bound}. Whenever every variable is
     *       singleton and the single-culprit check above found nothing, every one of them is
     *       guaranteed to individually fall short of {@link #bound} (otherwise the single-culprit
     *       check — or {@link #propagate} itself — would already have resolved the conflict), so
     *       the full set of singleton values is always a sound, self-contained explanation as
     *       soon as it's reached.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return ExtremumPropagation.<N>explainInfeasible(getVariables(), domains, bound.doubleValue(),
                operator == Operator.LT, true, upperPassApplies(), lowerPassApplies());
    }
}
