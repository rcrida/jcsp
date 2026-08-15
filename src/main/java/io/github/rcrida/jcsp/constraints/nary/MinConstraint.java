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
 * An n-ary constraint that compares the minimum value among a set of numeric variables to a fixed bound:
 * {@code min(v1, v2, ..., vn) op bound}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once all
 * variables are assigned.
 * <p>
 * Propagation applies interval-arithmetic bounds narrowing for EQ, LEQ, LT, GEQ, and GT:
 * <ul>
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: raises every variable's lower bound to {@link #bound};
 *       infeasible when any variable's upper bound falls below {@link #bound}.</li>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}: infeasible when no variable can reach {@link #bound};
 *       when exactly one variable can still reach {@link #bound} its upper bound is lowered to {@link #bound}.</li>
 *   <li>{@link Operator#EQ}: combines both lower-raise and upper-force passes.</li>
 *   <li>{@link Operator#NEQ}: skipped (no narrowing applicable).</li>
 * </ul>
 * Both {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
 * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) and discrete domain variables are supported
 * via {@link NumericBounds}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MinConstraint<N extends Number> extends UniformNaryConstraint<N> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS =
            EnumSet.of(Operator.EQ, Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT);

    @NonNull private final N bound;
    @NonNull private final Operator operator;

    /** Whether the lower-bound pass applies, shared by {@link #propagate} and {@link #explainInfeasible}. */
    private boolean lowerPassApplies() {
        return operator == Operator.EQ || operator == Operator.GEQ || operator == Operator.GT;
    }

    /** Whether the upper-bound pass applies, shared by {@link #propagate} and {@link #explainInfeasible}. */
    private boolean upperPassApplies() {
        return operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.LT;
    }

    public static <N extends Number> MinConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                         @NonNull Operator operator,
                                                         @NonNull N bound) {
        return MinConstraint.<N>builder()
                .variables(variables)
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<N> values) {
        if (values.size() < getVariables().size()) return true;
        double min = values.stream().mapToDouble(Number::doubleValue).min().orElseThrow();
        return operator.compare(min, bound.doubleValue());
    }

    /**
     * Bounds propagation for {@code min(vars) op bound}, delegating the shared narrowing math to
     * {@link ExtremumPropagation#narrowMax} via the coordinate transform described in that class's
     * own Javadoc: real {@code [min,max]} bounds become extremum-space {@code [-max,-min]}, the
     * bound negates, and the operator flips ({@link ExtremumPropagation#flip}) — {@code min(x) =
     * -max(-x)}. The narrowed extremum-space result is un-transformed back to real bounds
     * ({@code realMin = -extremumMax}, {@code realMax = -extremumMin}) before ever touching an
     * actual {@link Domain}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
        int n = vars.size();
        double[] extremumMins = new double[n];
        double[] extremumMaxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            extremumMins[i] = -NumericBounds.max(dom);
            extremumMaxs[i] = -NumericBounds.min(dom);
        }

        Optional<double[][]> narrowed = ExtremumPropagation.narrowMax(
                extremumMins, extremumMaxs, -bound.doubleValue(), ExtremumPropagation.flip(operator));
        if (narrowed.isEmpty()) return Optional.empty();
        double[] newExtremumMins = narrowed.get()[0];
        double[] newExtremumMaxs = narrowed.get()[1];

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            Optional<Domain<N>> result = NumericBounds.narrow(dom, -newExtremumMaxs[i], -newExtremumMins[i]);
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
        return "min(" + varNames + ") " + operator.symbol + " " + bound;
    }

    /**
     * On infeasibility, tries two independent, always-sound explanations — the dual of
     * {@link MaxConstraint#propagateWithReasons}. Neither replicates {@link #propagate}'s
     * internal branch order; each is checked directly against the current domains and is valid
     * regardless of which branch actually detected the conflict (including the discrete-gap
     * corner case, which falls back to an empty reason):
     * <ul>
     *   <li><b>Single culprit</b> (violates the lower bound, EQ/GEQ/GT): any one singleton
     *       variable whose value already falls below {@link #bound} makes
     *       {@code min(vars) op bound} infeasible by itself, regardless of every other
     *       variable — attributed alone as soon as found.</li>
     *   <li><b>Collective</b> (violates the upper bound, EQ/LEQ/LT): {@code min(vars) <= bound}
     *       needs at least one variable to reach {@link #bound}; only attributable when every
     *       variable is singleton, since a partial subset can't rule out an unlisted open-domain
     *       variable also being unable to reach {@link #bound}. Whenever every variable is
     *       singleton and the single-culprit check above found nothing, every one of them is
     *       guaranteed to individually stay above {@link #bound} (otherwise the single-culprit
     *       check — or {@link #propagate} itself — would already have resolved the conflict), so
     *       the full set of singleton values is always a sound, self-contained explanation as
     *       soon as it's reached.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return ExtremumPropagation.<N>explainInfeasible(getVariables(), domains, bound.doubleValue(),
                operator == Operator.GT, false, lowerPassApplies(), upperPassApplies());
    }
}
