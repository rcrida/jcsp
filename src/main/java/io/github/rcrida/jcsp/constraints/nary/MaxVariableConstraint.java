package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the maximum value among a set of numeric variables to a
 * variable target, rather than a fixed bound: {@code max(v1, v2, ..., vn) <op> target}. The
 * sibling of {@link MaxConstraint} for when the right-hand side is itself a decision variable —
 * see {@link SumVariableConstraint}'s own Javadoc for why this needs its own class rather than
 * folding {@link #target} into {@link MaxConstraint} with a nullable field ({@link MaxConstraint}
 * extends {@link UniformNaryConstraint}, whose {@code isSatisfiedBy} is {@code final}).
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once every
 * maxed variable and {@link #target} are all assigned. Unlike {@link SumVariableConstraint},
 * {@link #isSatisfiedBy} never needs to synthesize a typed {@code N} result — a plain {@code
 * double} comparison via {@link Operator#compare(Object, Object)} is enough, since a maximum never
 * needs to reconstruct an exact numeric value the way a sum's total does.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MaxVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Variable<N>> maxedVariables;
    @Getter @NonNull private final Variable<N> target;
    @Getter @NonNull private final Operator operator;

    public static <N extends Number> MaxVariableConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                                   @NonNull Operator operator,
                                                                   @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(variables);
        allVars.add(target);
        return MaxVariableConstraint.<N>builder()
                .variables(allVars)
                .maxedVariables(Set.copyOf(variables))
                .target(target)
                .operator(operator)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        double max = maxedVariables.stream()
                .mapToDouble(v -> assignment.getValue(v).orElseThrow().doubleValue())
                .max().orElseThrow();
        double targetValue = assignment.getValue(target).orElseThrow().doubleValue();
        return operator.compare(max, targetValue);
    }

    /**
     * Bounds-consistency propagation for {@code max(vars) <op> target}. Only {@link Operator#EQ},
     * {@link Operator#LEQ}, and {@link Operator#GEQ} propagate (matching {@link
     * NumericBounds#propagateWeightedSumVsTarget} and every other target-comparing propagator in
     * this codebase) — {@link Operator#LT}/{@link Operator#GT}/{@link Operator#NEQ} are checked
     * only via {@link #isSatisfiedBy} at solution time.
     * <p>
     * The achievable range of {@code max(vars)} over the current box is {@code [mLo, mHi]}, where
     * {@code mLo = max_i(min_i)} (setting every variable to its own minimum still leaves the
     * variable with the largest minimum determining the max — it can never go lower) and {@code
     * mHi = max_i(max_i)} (achieved by whichever variable's own maximum is largest). From there:
     * <ul>
     *   <li><b>{@link #target} narrowing</b>: a target value below {@code mLo} can never satisfy
     *       {@code max(vars) <= target} (LEQ/EQ), so {@link #target}'s lower bound rises to
     *       {@code mLo}; a target value above {@code mHi} can never satisfy {@code max(vars) >=
     *       target} (GEQ/EQ), so its upper bound falls to {@code mHi}.</li>
     *   <li><b>Variable upper-clip</b> (LEQ/EQ): if any {@code var_i} could exceed {@link
     *       #target}'s current maximum, then {@code var_i} alone would force {@code max(vars) >
     *       target} regardless of every other variable, so its own maximum clips down to {@link
     *       #target}'s current maximum — the same reasoning {@link MaxConstraint}'s own upper pass
     *       uses against a fixed bound, just substituting {@link #target}'s bound for the constant.</li>
     *   <li><b>Variable lower-force</b> (GEQ/EQ): if only one {@code var_i} can still reach {@link
     *       #target}'s current minimum, that variable must actually reach it (the same "single
     *       reacher" reasoning {@link MaxConstraint}'s own lower pass uses).</li>
     * </ul>
     * Each of these is independently sound from the domains {@link #propagate} was called with,
     * regardless of the others — so using {@link #target}'s bounds <em>before</em> this call's own
     * target-narrowing step (rather than threading the narrowed value through) doesn't cost
     * soundness, only a little tightness within a single call; the surrounding fixpoint loop calls
     * this again until nothing changes. Given the earlier infeasibility checks pass, target
     * narrowing is provably never empty: {@code mLo <= mHi} always (the variable achieving {@code
     * mLo} also has its own maximum {@code >= mLo}, and that maximum is itself {@code <= mHi}), so
     * the new target range's bounds never cross.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>(maxedVariables);
        int n = vars.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            mins[i] = NumericBounds.min(dom);
            maxs[i] = NumericBounds.max(dom);
        }
        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);

        var narrowed = ExtremumPropagation.narrowMaxAgainstTarget(mins, maxs, tLo, tHi, operator);
        if (narrowed.isEmpty()) return Optional.empty();
        var result = narrowed.get();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        NumericBounds.<N>narrow(targetDomain, result.targetLo(), result.targetHi())
                .ifPresent(d -> updated.put(target, d));
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            Optional<Domain<N>> pruned = NumericBounds.narrow(dom, result.mins()[i], result.maxs()[i]);
            if (pruned.isEmpty()) continue;
            if (pruned.get().isEmpty()) return Optional.empty();
            updated.put(vars.get(i), pruned.get());
        }

        return Optional.of(updated);
    }

    /**
     * Same shape as {@link SumVariableConstraint#explainInfeasible}: both of {@link #propagate}'s
     * infeasibility checks ({@code mLo > tHi}, {@code mHi < tLo}) are derived purely from every
     * cited variable's current bounding range, so {@link RangeNogoodConstraint#fromCurrentBounds}
     * is always a sound explanation whenever every domain can be safely cited as a range; falls
     * back to {@link Propagatable#allSingletonReason}'s fully collective ground reason otherwise.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        String varNames = maxedVariables.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
        return "max(" + varNames + ") " + operator.symbol + " " + target;
    }
}
