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
 * An n-ary constraint that compares the minimum value among a set of numeric variables to a
 * variable target, rather than a fixed bound: {@code min(v1, v2, ..., vn) <op> target}. The
 * variable-target sibling of {@link MinConstraint}, mirroring {@link MaxVariableConstraint}'s own
 * shape — see that class's Javadoc for why this needs its own class rather than folding {@link
 * #target} into {@link MinConstraint} with a nullable field.
 * <p>
 * {@link #propagate} delegates to {@link ExtremumPropagation#narrowMaxAgainstTarget} via the same
 * {@code min(x) = -max(-x)} coordinate transform {@link MinConstraint} already uses against a
 * fixed bound: negate+swap every maxed variable's bounds and {@code target}'s own bounds, flip the
 * operator, then un-transform the narrowed result back to real bounds before ever touching an
 * actual {@link Domain}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MinVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Variable<N>> minedVariables;
    @Getter @NonNull private final Variable<N> target;
    @Getter @NonNull private final Operator operator;

    public static <N extends Number> MinVariableConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                                    @NonNull Operator operator,
                                                                    @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(variables);
        allVars.add(target);
        return MinVariableConstraint.<N>builder()
                .variables(allVars)
                .minedVariables(Set.copyOf(variables))
                .target(target)
                .operator(operator)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        double min = minedVariables.stream()
                .mapToDouble(v -> assignment.getValue(v).orElseThrow().doubleValue())
                .min().orElseThrow();
        double targetValue = assignment.getValue(target).orElseThrow().doubleValue();
        return operator.compare(min, targetValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>(minedVariables);
        int n = vars.size();
        double[] extremumMins = new double[n];
        double[] extremumMaxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            extremumMins[i] = -NumericBounds.max(dom);
            extremumMaxs[i] = -NumericBounds.min(dom);
        }
        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double extremumTLo = -NumericBounds.max(targetDomain);
        double extremumTHi = -NumericBounds.min(targetDomain);

        var narrowed = ExtremumPropagation.narrowMaxAgainstTarget(
                extremumMins, extremumMaxs, extremumTLo, extremumTHi, ExtremumPropagation.flip(operator));
        if (narrowed.isEmpty()) return Optional.empty();
        var result = narrowed.get();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        NumericBounds.<N>narrow(targetDomain, -result.targetHi(), -result.targetLo())
                .ifPresent(d -> updated.put(target, d));
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            Optional<Domain<N>> pruned = NumericBounds.narrow(dom, -result.maxs()[i], -result.mins()[i]);
            if (pruned.isEmpty()) continue;
            if (pruned.get().isEmpty()) return Optional.empty();
            updated.put(vars.get(i), pruned.get());
        }

        return Optional.of(updated);
    }

    /**
     * Same shape as {@link MaxVariableConstraint#explainInfeasible} — both of {@link #propagate}'s
     * infeasibility checks are derived purely from every cited variable's current bounding range
     * (the transform's negation doesn't change that), so {@link RangeNogoodConstraint#fromCurrentBounds}
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
        String varNames = minedVariables.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
        return "min(" + varNames + ") " + operator.symbol + " " + target;
    }
}
