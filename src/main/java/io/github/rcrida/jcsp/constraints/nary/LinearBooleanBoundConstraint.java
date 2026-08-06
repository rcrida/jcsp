package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares a weighted sum of boolean indicator variables to a fixed
 * numeric bound: {@code a1*b1 + a2*b2 + ... + an*bn <op> bound}, where each {@code true}
 * contributes its coefficient and each {@code false} contributes zero — the "pseudo-boolean"
 * sibling of {@link LinearBoundConstraint}. Needed because {@code linearConstraint}'s {@code
 * Map<Variable<N>, N>} requires the variable and the coefficient to share one {@link Number}
 * type, so a {@code Variable<Boolean>} (e.g. a {@code reifyConstraint} indicator) can never be a
 * key there; overloading {@code linearConstraint} itself on the map's key type isn't possible
 * either, since {@code Map<Variable<N>, N>} and {@code Map<Variable<Boolean>, N>} erase to the
 * same raw {@code Map} and collide (JLS 8.4.2) — hence the distinct name.
 * <p>
 * Propagation and pruning arithmetic is shared with {@link LinearBooleanVariableConstraint} via
 * {@link LinearBooleanSupport}. For partial assignments the constraint is optimistically
 * satisfied — only evaluated once all variables are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LinearBooleanBoundConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @Getter @NonNull private final Map<Variable<Boolean>, N> coefficients;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final N bound;

    public static <N extends Number> LinearBooleanBoundConstraint<N> of(@NonNull Map<Variable<Boolean>, N> coefficients,
                                                                          @NonNull Operator operator,
                                                                          @NonNull N bound) {
        return LinearBooleanBoundConstraint.<N>builder()
                .variables(coefficients.keySet())
                .coefficients(Map.copyOf(coefficients))
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return operator.compare(LinearBooleanSupport.weightedSumOfTrue(coefficients, assignment, bound), bound);
    }

    /**
     * A boolean variable's domain only ever has one or two possible values, so each variable's
     * contribution range collapses to testing the two candidates ({@code 0} and its coefficient)
     * directly via {@link LinearBooleanSupport#feasible} — real domain consistency, not just
     * bounds consistency. Only applied for EQ, LEQ, and GEQ operators.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
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

        double totalMin = 0, totalMax = 0;
        for (int i = 0; i < n; i++) { totalMin += minContribs[i]; totalMax += maxContribs[i]; }
        double k = bound.doubleValue();

        if ((operator == Operator.EQ && (k < totalMin || k > totalMax)) ||
            (operator == Operator.LEQ && k < totalMin) ||
            (operator == Operator.GEQ && k > totalMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Variable<Boolean> var = vars.get(i);
            DiscreteDomain<Boolean> dom = (DiscreteDomain<Boolean>) domains.get(var);
            // Already resolved: skip rather than re-derive the same singleton every fixpoint round,
            // which would report a spurious "change" and never let the fixpoint loop terminate.
            if (dom.size() < 2) continue;

            double restMin = totalMin - minContribs[i];
            double restMax = totalMax - maxContribs[i];
            boolean falseOk = LinearBooleanSupport.feasible(operator, 0, restMin, restMax, k);
            boolean trueOk = LinearBooleanSupport.feasible(operator, coeffs[i], restMin, restMax, k);
            if (!falseOk && !trueOk) return Optional.empty();

            LinearBooleanSupport.narrowed(dom, falseOk, trueOk).ifPresent(pruned -> updated.put(var, pruned));
        }
        return Optional.of(updated);
    }

    /**
     * On infeasibility, the weighted sum's violation depends on the combined contribution of
     * every variable, not any single one in isolation — same shape as {@link
     * LinearBoundConstraint#explainInfeasible}.
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
        return terms + " " + operator.symbol + " " + bound;
    }
}
