package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shared bounds propagation for {@code max(vars) op bound} ({@link MaxConstraint}) and
 * {@code min(vars) op bound} ({@link MinConstraint}) — mirror-image constraints ({@code
 * min(x) = -max(-x)}) reduced to one implementation of the "maximize" case, {@link #narrowMax}.
 * {@link MinConstraint} calls it by transforming its own real per-variable {@code [min,max]}
 * bounds into this maximize coordinate space first — negated and swapped, {@code [-max,-min]} —
 * with {@link #flip} applied to its operator and the bound negated ({@code x >= k <=> -x <= -k}),
 * then un-transforms the narrowed result bounds back ({@code realMin = -extremumMax}, {@code
 * realMax = -extremumMin}) before calling {@link io.github.rcrida.jcsp.constraints.NumericBounds#narrow}
 * against its own real, never-negated {@link Domain} objects — the transform is pure {@code
 * double} arithmetic on bounds, it never touches a domain directly.
 */
final class ExtremumPropagation {
    private ExtremumPropagation() {}

    /** {@code x <op> k <=> -x <flip(op)> -k}: negating both sides of an inequality flips its direction. */
    static Operator flip(Operator operator) {
        return switch (operator) {
            case LEQ -> Operator.GEQ;
            case GEQ -> Operator.LEQ;
            case LT -> Operator.GT;
            case GT -> Operator.LT;
            default -> operator; // EQ, NEQ: unaffected by negation
        };
    }

    /**
     * Bounds narrowing for {@code max(vars) op bound}, phrased purely over per-variable {@code
     * double} bounds (not {@link Domain} objects) so {@link MinConstraint} can reuse it via the
     * coordinate transform described in this class's own Javadoc.
     * <p>
     * Upper-bound pass ({@code EQ}/{@code LEQ}/{@code LT}): clips every variable's upper bound
     * down to {@code k}, sound because the class-level feasibility check ({@code globalMin <= k})
     * guarantees each variable's own {@code mins[i]} — a real, currently-present value — already
     * satisfies the target range, so this pass can never itself produce an empty result. Lower-bound
     * pass ({@code EQ}/{@code GEQ}/{@code GT}): when exactly one variable can still reach {@code
     * k}, raises its lower bound up to {@code k} — unlike the upper pass, {@code k} itself isn't
     * guaranteed to be a real present value of a gapped discrete domain, so the caller must check
     * whether narrowing to the returned bounds actually empties that variable's domain.
     *
     * @return {@link Optional#empty()} if infeasible, otherwise the narrowed {@code [min,max]}
     *         pair for every variable (unchanged from the input for one this call didn't narrow)
     */
    static Optional<double[][]> narrowMax(double[] mins, double[] maxs, double k, Operator operator) {
        int n = mins.length;
        double[] newMins = mins.clone();
        double[] newMaxs = maxs.clone();

        boolean upperPassApplies = operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.LT;
        boolean lowerPassApplies = operator == Operator.EQ || operator == Operator.GEQ || operator == Operator.GT;

        if (upperPassApplies) {
            double globalMin = Double.NEGATIVE_INFINITY;
            for (double m : newMins) globalMin = Math.max(globalMin, m);
            boolean strict = operator == Operator.LT;
            if (strict ? globalMin >= k : globalMin > k) return Optional.empty();

            for (int i = 0; i < n; i++) {
                if (newMaxs[i] > k) newMaxs[i] = k;
            }
        }

        if (lowerPassApplies) {
            double globalMax = Double.NEGATIVE_INFINITY;
            for (double m : newMaxs) globalMax = Math.max(globalMax, m);
            boolean strict = operator == Operator.GT;
            if (strict ? globalMax <= k : globalMax < k) return Optional.empty();

            int reachCount = 0, reachIdx = -1;
            for (int i = 0; i < n; i++) {
                if (strict ? newMaxs[i] > k : newMaxs[i] >= k) {
                    reachCount++;
                    reachIdx = i;
                }
            }
            if (reachCount == 1 && newMins[reachIdx] < k) {
                newMins[reachIdx] = k;
            }
        }

        return Optional.of(new double[][]{newMins, newMaxs});
    }

    /** {@code max(vars) op target}'s narrowed bounds: every maxed variable, plus target itself. */
    record TargetNarrowResult(double[] mins, double[] maxs, double targetLo, double targetHi) {}

    /**
     * Bounds narrowing for {@code max(vars) op target}, target itself a variable rather than a
     * fixed bound — the "maximize" direction directly; {@link MinVariableConstraint} reuses this
     * via the same coordinate transform {@link #narrowMax} documents (negate+swap each variable's
     * bounds and {@code target}'s own bounds, flip the operator, then un-transform the result).
     * <p>
     * Deliberately narrows the maxed variables against {@code tLo}/{@code tHi} as given — not the
     * freshly-computed {@code newTLo}/{@code newTHi} — since each of {@code propagate}'s steps is
     * independently sound from the domains this call started with regardless of the others; the
     * surrounding fixpoint loop calls this again until nothing changes, so using {@code target}'s
     * bounds before this call's own target-narrowing step only costs a little tightness within a
     * single call, not soundness.
     *
     * @return {@link Optional#empty()} if infeasible, otherwise the narrowed bounds for every
     *         maxed variable and for {@code target} itself
     */
    static Optional<TargetNarrowResult> narrowMaxAgainstTarget(double[] mins, double[] maxs,
                                                                 double tLo, double tHi, Operator operator) {
        int n = mins.length;
        double[] newMins = mins.clone();
        double[] newMaxs = maxs.clone();
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        double mLo = Double.NEGATIVE_INFINITY, mHi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            mLo = Math.max(mLo, newMins[i]);
            mHi = Math.max(mHi, newMaxs[i]);
        }

        if (leqLike && mLo > tHi) return Optional.empty();
        if (geqLike && mHi < tLo) return Optional.empty();

        double newTLo = leqLike ? Math.max(tLo, mLo) : tLo;
        double newTHi = geqLike ? Math.min(tHi, mHi) : tHi;

        if (leqLike) {
            for (int i = 0; i < n; i++) {
                if (newMaxs[i] > tHi) newMaxs[i] = tHi;
            }
        }

        if (geqLike) {
            int reachCount = 0, reachIdx = -1;
            for (int i = 0; i < n; i++) {
                if (newMaxs[i] >= tLo) {
                    reachCount++;
                    reachIdx = i;
                }
            }
            if (reachCount == 1 && newMins[reachIdx] < tLo) {
                newMins[reachIdx] = tLo;
            }
        }

        return Optional.of(new TargetNarrowResult(newMins, newMaxs, newTLo, newTHi));
    }

    /**
     * Shared {@code explainInfeasible} for both directions, taking the two independent, always-sound
     * explanations {@link MaxConstraint}/{@link MinConstraint} each try: a <em>single-culprit</em>
     * singleton variable already past {@code k} in the direction {@code greaterThan} checks (found
     * as soon as one is seen), or the fully <em>collective</em> reason ({@link
     * Propagatable#allSingletonReason}) when every variable is singleton. Neither replicates {@code
     * propagate}'s own internal branch order — each is checked directly against the current domains
     * and is valid regardless of which branch actually detected the conflict.
     */
    @SuppressWarnings("unchecked")
    static <N extends Number> Optional<NogoodConstraint> explainInfeasible(
            Collection<? extends Variable<?>> variables, Map<Variable<?>, Domain<?>> domains,
            double k, boolean strict, boolean greaterThan,
            boolean singleCulpritPassApplies, boolean collectivePassApplies) {
        if (singleCulpritPassApplies) {
            for (Variable<?> var : variables) {
                Domain<N> dom = (Domain<N>) domains.get(var);
                if (!dom.isSingleton()) continue;
                N value = dom.singleValue().orElseThrow();
                double v = value.doubleValue();
                boolean violates = greaterThan ? (strict ? v >= k : v > k) : (strict ? v <= k : v < k);
                if (violates) {
                    Map<Variable<?>, Object> reason = new HashMap<>();
                    reason.put(var, value);
                    return GroundNogoodConstraint.fromReason(reason);
                }
            }
        }

        if (collectivePassApplies) {
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(variables, domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        return Optional.empty();
    }
}
