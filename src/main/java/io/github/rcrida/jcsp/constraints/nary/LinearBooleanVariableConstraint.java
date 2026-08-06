package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link LinearBooleanBoundConstraint} sibling for when the right-hand side is itself a
 * decision variable rather than a fixed bound: {@code a1*b1 + a2*b2 + ... <op> target}. Mirrors
 * {@link LinearVariableConstraint}'s relationship to {@link LinearBoundConstraint} — see {@link
 * LinearBooleanBoundConstraint}'s Javadoc for why this needs its own name rather than an overload
 * of {@code linearConstraint}/{@code linearBooleanConstraint} itself.
 * <p>
 * {@link #target}'s own domain is folded into the same zero-sum feasibility window as {@link
 * LinearBoundConstraint}/{@link LinearVariableConstraint} use for their numeric-only case (target
 * as an implicit {@code -1}-coefficient term, narrowed via {@link NumericBounds#narrow}); each
 * boolean's own narrowing still goes through {@link LinearBooleanSupport}, since {@code target}'s
 * domain is numeric but the coefficient variables' domains are not.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LinearBooleanVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Map<Variable<Boolean>, N> coefficients;
    @Getter @NonNull private final Variable<N> target;
    @Getter @NonNull private final Operator operator;

    public static <N extends Number> LinearBooleanVariableConstraint<N> of(@NonNull Map<Variable<Boolean>, N> coefficients,
                                                                             @NonNull Operator operator,
                                                                             @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(coefficients.keySet());
        allVars.add(target);
        return LinearBooleanVariableConstraint.<N>builder()
                .variables(allVars)
                .coefficients(Map.copyOf(coefficients))
                .target(target)
                .operator(operator)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        N targetValue = assignment.getValue(target).orElseThrow();
        return operator.compare(LinearBooleanSupport.weightedSumOfTrue(coefficients, assignment, targetValue), targetValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }

        List<Variable<Boolean>> vars = new ArrayList<>(coefficients.keySet());
        int n = vars.size();
        double[] coeffs = new double[n];
        double[] minContribs = new double[n];
        double[] maxContribs = new double[n];

        for (int i = 0; i < n; i++) {
            coeffs[i] = coefficients.get(vars.get(i)).doubleValue();
            DiscreteDomain<Boolean> dom = (DiscreteDomain<Boolean>) domains.get(vars.get(i));
            var range = LinearBooleanSupport.contributionRange(dom, coeffs[i]);
            minContribs[i] = range.min();
            maxContribs[i] = range.max();
        }

        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double targetMin = NumericBounds.min(targetDomain), targetMax = NumericBounds.max(targetDomain);
        // target is the (n+1)th term, coefficient -1; the whole sum (booleans + target) must equal 0.
        double targetMinContrib = -targetMax, targetMaxContrib = -targetMin;

        double totalMin = targetMinContrib, totalMax = targetMaxContrib;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }

        if ((operator == Operator.EQ && (0 < totalMin || 0 > totalMax)) ||
            (operator == Operator.LEQ && 0 < totalMin) ||
            (operator == Operator.GEQ && 0 > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Variable<Boolean> var = vars.get(i);
            DiscreteDomain<Boolean> dom = (DiscreteDomain<Boolean>) domains.get(var);
            // Already resolved: skip rather than re-derive the same singleton every fixpoint round,
            // which would report a spurious "change" and never let the fixpoint loop terminate.
            if (dom.size() < 2) continue;

            double restMin = totalMin - minContribs[i];
            double restMax = totalMax - maxContribs[i];
            boolean falseOk = LinearBooleanSupport.feasible(operator, 0, restMin, restMax, 0);
            boolean trueOk = LinearBooleanSupport.feasible(operator, coeffs[i], restMin, restMax, 0);
            if (!falseOk && !trueOk) return Optional.empty();

            LinearBooleanSupport.narrowed(dom, falseOk, trueOk).ifPresent(pruned -> updated.put(var, pruned));
        }

        double restMin = totalMin - targetMinContrib;
        double restMax = totalMax - targetMaxContrib;
        double newMin = (operator != Operator.GEQ) ? (0 - restMin) / -1.0 : Double.NEGATIVE_INFINITY;
        double newMax = (operator != Operator.LEQ) ? (0 - restMax) / -1.0 : Double.POSITIVE_INFINITY;
        var pruned = NumericBounds.narrow(targetDomain, newMin, newMax);
        if (pruned.isPresent()) {
            if (pruned.get().isEmpty()) return Optional.empty();
            updated.put(target, pruned.get());
        }

        return Optional.of(updated);
    }

    /**
     * Same shape as {@link LinearVariableConstraint#explainInfeasible}: the weighted sum's
     * violation depends on the combined contribution of every variable (including {@link
     * #target}, already folded into {@link #getVariables()}), not any single one in isolation.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        String terms = coefficients.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getValue() + "*" + e.getKey())
                .collect(Collectors.joining(" + "));
        return terms + " " + operator.symbol + " " + target;
    }
}
