package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Whether {@code max(vars) == target}/{@code min(vars) == target} qualifies for {@link
     * #propagateEqCoverage}'s value-level coverage reasoning rather than the bounds-only {@code EQ}
     * pass in {@link #narrowMaxAgainstTarget}: {@code operator} must be {@link Operator#EQ}, and
     * every one of {@code vars} plus {@code target} must have a {@link DiscreteDomain} (no
     * continuous {@link io.github.rcrida.jcsp.domains.BoundedDomain} variable involved). Shared by
     * {@link MaxVariableConstraint#propagate} and {@link MinVariableConstraint#propagate} so the
     * eligibility rule can't drift between the two mirror-image constraints.
     */
    static <N extends Number> boolean eqCoverageEligible(
            Operator operator, Collection<Variable<N>> vars, Variable<N> target,
            Map<Variable<?>, Domain<?>> domains) {
        return operator == Operator.EQ && domains.get(target) instanceof DiscreteDomain<?>
                && vars.stream().allMatch(v -> domains.get(v) instanceof DiscreteDomain<?>);
    }

    /** Value-level result of {@link #narrowMaxEqCoverage}: the exact retained value set for
     * {@code target} and for each maxed variable (parallel to the input {@code varValues} list),
     * as opposed to {@link TargetNarrowResult}'s bounds-only {@code [min,max]} pair. */
    record CoverageResult(Set<Double> targetKept, List<Set<Double>> varsKept) {}

    /**
     * Full generalized-arc-consistency narrowing for {@code max(vars) == target} over discrete
     * domains — strictly stronger than {@link #narrowMaxAgainstTarget}'s bounds-only {@code EQ}
     * pass, which only reasons about each domain's numeric {@code [min,max]} and can leave an
     * "achievable by bound but not literally present anywhere" gap value sitting in target's
     * domain, or an analogous gap in the sole variable capable of covering target's current
     * maximum. {@link #narrowMaxAgainstTarget}'s {@code LEQ}/{@code GEQ} passes need no such
     * treatment — an inequality only needs <em>some</em> achievable bound to exist, never an exact
     * value match, so ordinary bounds consistency already is generalized arc consistency for those
     * two operators (a value only needs to be {@code <=} or {@code >=} some other variable's own
     * real {@code min}/{@code max}, which is itself always a literal present value — no coverage
     * gap is possible). Only {@code EQ} needs this: it demands some variable's value be <em>exactly
     * equal</em> to target's, which a plain bound can silently overstate when domains have holes.
     * <p>
     * A target value {@code t} is consistent iff {@code t >= mLo} (every variable can drop to its
     * own minimum, all {@code <= t}) and {@code t} is literally present in some maxed variable's
     * domain — the "coverage" condition an inequality never needs. The achievable target set
     * {@code TSet} is exactly the set of target values meeting both; infeasible iff it's empty
     * (which alone subsumes both of {@link #narrowMaxAgainstTarget}'s separate {@code mLo > tHi}
     * / {@code mHi < tLo} checks — either one forces every candidate {@code t} to fail one of the
     * two conditions above).
     * <p>
     * A maxed variable's own value {@code v} is consistent iff {@code v} itself is in {@code TSet}
     * (v can be the determining maximum directly, target set to {@code v}) or some <em>other</em>
     * variable can independently cover a {@code TSet} value strictly greater than {@code v} (that
     * other variable becomes the determining maximum instead, {@code v} rides along underneath
     * it, unconstrained beyond that). Every variable can use {@code max(TSet)} for that "other
     * coverer" role except the sole variable that exclusively covers {@code max(TSet)} itself (if
     * such a variable exists) — excluding its own contribution, that one variable's own threshold
     * is instead the largest {@code TSet} value some other variable can still reach (possibly
     * none, i.e. {@link Double#NEGATIVE_INFINITY}, leaving only the direct {@code v ∈ TSet} case
     * for it).
     *
     * @param varValues    each maxed variable's own currently-present values (real domain
     *                     elements), in the order the caller wants {@code varsKept} back in
     * @param targetValues target's own currently-present values
     * @return empty if infeasible ({@code TSet} is empty), otherwise the exact retained value set
     *         for target and for every maxed variable, in {@code varValues}' order
     */
    static Optional<CoverageResult> narrowMaxEqCoverage(List<double[]> varValues, double[] targetValues) {
        int n = varValues.size();
        double mLo = Double.NEGATIVE_INFINITY;
        Set<Double> union = new HashSet<>();
        for (double[] vals : varValues) {
            double localMin = Double.POSITIVE_INFINITY;
            for (double v : vals) {
                union.add(v);
                if (v < localMin) localMin = v;
            }
            if (localMin > mLo) mLo = localMin;
        }

        Set<Double> tSet = new HashSet<>();
        double t1 = Double.NEGATIVE_INFINITY;
        for (double t : targetValues) {
            if (t >= mLo && union.contains(t)) {
                tSet.add(t);
                if (t > t1) t1 = t;
            }
        }
        if (tSet.isEmpty()) return Optional.empty();

        int soleIndex = -1;
        for (int i = 0; i < n; i++) {
            boolean covers = false;
            for (double v : varValues.get(i)) {
                if (v == t1) { covers = true; break; }
            }
            if (covers) {
                if (soleIndex == -1) {
                    soleIndex = i;
                } else {
                    soleIndex = -1;
                    break; // a second coverer disqualifies "sole" -- no need to look further
                }
            }
        }

        double soleThreshold = Double.NEGATIVE_INFINITY;
        if (soleIndex != -1) {
            Set<Double> unionExcludingSole = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if (i == soleIndex) continue;
                for (double v : varValues.get(i)) unionExcludingSole.add(v);
            }
            for (double t : tSet) {
                if (t > soleThreshold && unionExcludingSole.contains(t)) soleThreshold = t;
            }
        }

        List<Set<Double>> varsKept = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double threshold = (i == soleIndex) ? soleThreshold : t1;
            Set<Double> kept = new HashSet<>();
            for (double v : varValues.get(i)) {
                if (v <= threshold || tSet.contains(v)) kept.add(v);
            }
            varsKept.add(kept);
        }

        return Optional.of(new CoverageResult(tSet, varsKept));
    }

    /**
     * End-to-end {@link #narrowMaxEqCoverage} narrowing directly against real {@link Domain}
     * objects: extracts each variable's current value set, runs the coverage computation, and
     * narrows every changed domain via {@link #narrowToValues}. {@code negate=true} runs it for
     * {@code min(vars) == target} instead ({@link MinVariableConstraint}), via the same
     * {@code min(x) = -max(-x)} transform {@link #narrowMax} documents — except here the whole
     * real value <em>sets</em> are negated up front (not just bounds), since the coverage check
     * needs literal value membership, with the retained sets un-negated before ever touching an
     * actual {@link Domain}.
     * <p>
     * Unlike {@link #narrowMax}/{@link #narrowMaxAgainstTarget}, neither {@link #narrowToValues}
     * call here can ever produce an empty domain: {@link CoverageResult#targetKept} is by
     * construction a subset of target's own already-current values (never empty once {@link
     * #narrowMaxEqCoverage} itself returns non-empty), and every maxed variable's kept set always
     * retains at least its own current minimum (which is {@code <= mLo <= t1}, the "generic"
     * threshold every non-sole-coverer variable narrows against) or, for the one sole-coverer
     * variable, at least {@code t1} itself (which that variable covers by definition of being the
     * sole coverer, and which is always in {@link CoverageResult#targetKept}) — so there's no
     * separate infeasibility check to make here beyond {@link #narrowMaxEqCoverage}'s own.
     *
     * @return empty if infeasible, otherwise the map of variables (from {@code vars} and/or
     *         {@code target}) whose domains were narrowed
     */
    @SuppressWarnings("unchecked")
    static <N extends Number> Optional<Map<Variable<?>, Domain<?>>> propagateEqCoverage(
            List<Variable<N>> vars, Variable<N> target, Map<Variable<?>, Domain<?>> domains, boolean negate) {
        double sign = negate ? -1.0 : 1.0;

        List<double[]> varValues = new ArrayList<>(vars.size());
        for (Variable<N> v : vars) {
            DiscreteDomain<N> dom = (DiscreteDomain<N>) domains.get(v);
            varValues.add(dom.stream().mapToDouble(x -> sign * x.doubleValue()).toArray());
        }
        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double[] targetValues = ((DiscreteDomain<N>) targetDomain).stream()
                .mapToDouble(x -> sign * x.doubleValue()).toArray();

        Optional<CoverageResult> narrowed = narrowMaxEqCoverage(varValues, targetValues);
        if (narrowed.isEmpty()) return Optional.empty();
        CoverageResult result = narrowed.get();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        narrowToValues(targetDomain, unsign(result.targetKept(), sign)).ifPresent(d -> updated.put(target, d));
        for (int i = 0; i < vars.size(); i++) {
            Variable<N> var = vars.get(i);
            Domain<N> dom = (Domain<N>) domains.get(var);
            narrowToValues(dom, unsign(result.varsKept().get(i), sign)).ifPresent(d -> updated.put(var, d));
        }
        return Optional.of(updated);
    }

    private static Set<Double> unsign(Set<Double> values, double sign) {
        if (sign == 1.0) return values;
        Set<Double> out = new HashSet<>(values.size());
        for (double v : values) out.add(sign * v);
        return out;
    }

    /**
     * Narrows {@code domain} to exactly {@code keep} (by {@link Number#doubleValue()}
     * membership), mirroring {@link io.github.rcrida.jcsp.constraints.NumericBounds#narrow}'s
     * discrete branch but against an explicit retained value set rather than an interval —
     * {@link #narrowMaxEqCoverage}'s pruning isn't expressible as a single {@code [min,max]} range.
     *
     * @return {@link Optional#empty()} if the domain is unchanged, otherwise the narrowed domain
     *         (which may itself be {@link Domain#isEmpty() empty}, signalling infeasibility)
     */
    @SuppressWarnings("unchecked")
    private static <N extends Number> Optional<Domain<N>> narrowToValues(Domain<N> domain, Set<Double> keep) {
        DiscreteDomain<N> discrete = (DiscreteDomain<N>) domain;
        DiscreteDomain.Builder<N> builder = null;
        for (N val : discrete.toList()) {
            if (!keep.contains(val.doubleValue())) {
                if (builder == null) builder = discrete.toBuilder();
                builder.delete(val);
            }
        }
        return builder == null ? Optional.empty() : Optional.of(builder.build());
    }
}
