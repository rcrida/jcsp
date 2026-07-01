package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.consistency.PropagationResult;
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
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: raises every variable's lower bound to {@code bound};
 *       infeasible when any variable's upper bound falls below {@code bound}.</li>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}: infeasible when no variable can reach {@code bound};
 *       when exactly one variable can still reach {@code bound} its upper bound is lowered to {@code bound}.</li>
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
     * Bounds propagation for {@code min(vars) op bound}.
     * <p>
     * Lower-bound pass (EQ/GEQ/GT): raises every variable's minimum to {@code bound}.
     * Upper-bound pass (EQ/LEQ/LT): when only one variable can still reach {@code bound},
     * lowers its maximum to {@code bound}. Returns {@link Optional#empty()} on infeasibility.
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

        double k = bound.doubleValue();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Lower-bound pass: min(vars) ≥ k — every variable must be ≥ k
        if (operator == Operator.EQ || operator == Operator.GEQ || operator == Operator.GT) {
            double globalSmallestMax = Double.POSITIVE_INFINITY;
            for (double m : maxs) globalSmallestMax = Math.min(globalSmallestMax, m);
            boolean strict = operator == Operator.GT;
            if (strict ? globalSmallestMax <= k : globalSmallestMax < k) return Optional.empty();

            for (int i = 0; i < n; i++) {
                if (mins[i] < k) {
                    Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
                    // mins[i] < k guarantees narrow produces a change; globalSmallestMax >= k guarantees non-empty
                    updated.put(vars.get(i), NumericBounds.narrow(dom, k, maxs[i]).orElseThrow());
                    mins[i] = k;
                }
            }
        }

        // Upper-bound pass: min(vars) ≤ k — at least one variable must reach k
        if (operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.LT) {
            double globalSmallestMin = Double.POSITIVE_INFINITY;
            for (double m : mins) globalSmallestMin = Math.min(globalSmallestMin, m);
            boolean strict = operator == Operator.LT;
            if (strict ? globalSmallestMin >= k : globalSmallestMin > k) return Optional.empty();

            // If exactly one variable can reach ≤ k, force its maximum down to k
            int reachCount = 0, reachIdx = -1;
            for (int i = 0; i < n; i++) {
                if (strict ? mins[i] < k : mins[i] <= k) {
                    reachCount++;
                    reachIdx = i;
                }
            }
            if (reachCount == 1 && maxs[reachIdx] > k) {
                Domain<N> dom = updated.containsKey(vars.get(reachIdx))
                        ? (Domain<N>) updated.get(vars.get(reachIdx))
                        : (Domain<N>) domains.get(vars.get(reachIdx));
                // maxs[reachIdx] > k guarantees narrow produces a change; may be empty for discrete with gaps
                Domain<N> clipped = NumericBounds.narrow(dom, mins[reachIdx], k).orElseThrow();
                if (clipped.isEmpty()) return Optional.empty();
                updated.put(vars.get(reachIdx), clipped);
            }
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
     *       variable whose value already falls below {@code bound} makes
     *       {@code min(vars) op bound} infeasible by itself, regardless of every other
     *       variable — attributed alone as soon as found.</li>
     *   <li><b>Collective</b> (violates the upper bound, EQ/LEQ/LT): {@code min(vars) <= bound}
     *       needs at least one variable to reach {@code bound}; only attributable when every
     *       variable is singleton, since a partial subset can't rule out an unlisted open-domain
     *       variable also being unable to reach {@code bound}. Whenever every variable is
     *       singleton and the single-culprit check above found nothing, every one of them is
     *       guaranteed to individually stay above {@code bound} (otherwise the single-culprit
     *       check — or {@code propagate()} itself — would already have resolved the conflict), so
     *       the full set of singleton values is always a sound, self-contained explanation as
     *       soon as it's reached.</li>
     * </ul>
     */
    @Override
    public PropagationResult propagateWithReasons(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains)
                .map(updated -> PropagationResult.feasible(updated, Map.of()))
                .orElseGet(() -> PropagationResult.infeasible(explainInfeasible(domains)));
    }

    @SuppressWarnings("unchecked")
    private Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        double k = bound.doubleValue();
        boolean lowerStrict = operator == Operator.GT;

        if (operator == Operator.EQ || operator == Operator.GEQ || operator == Operator.GT) {
            for (Variable<?> var : getVariables()) {
                Domain<N> dom = (Domain<N>) domains.get(var);
                if (!dom.isSingleton()) continue;
                N value = dom.singleValue().orElseThrow();
                boolean fallsBelow = lowerStrict ? value.doubleValue() <= k : value.doubleValue() < k;
                if (fallsBelow) {
                    Map<Variable<?>, Object> reason = new HashMap<>();
                    reason.put(var, value);
                    return reason;
                }
            }
        }

        if (operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.LT) {
            boolean allSingleton = getVariables().stream()
                    .allMatch(var -> ((Domain<N>) domains.get(var)).isSingleton());
            if (allSingleton) {
                Map<Variable<?>, Object> reason = new HashMap<>();
                for (Variable<?> var : getVariables()) {
                    Domain<N> dom = (Domain<N>) domains.get(var);
                    reason.put(var, dom.singleValue().orElseThrow());
                }
                return reason;
            }
        }

        return Map.of();
    }
}
