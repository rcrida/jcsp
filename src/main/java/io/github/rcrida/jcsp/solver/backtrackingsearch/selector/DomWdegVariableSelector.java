package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Variable selector implementing the dom/wdeg heuristic (Boussemart et al. 2004).
 * <p>
 * Each constraint carries a weight initialised to 1. When MAC inference causes a domain
 * wipeout, the weights of all active constraints on the failing variable are incremented
 * (see {@link #incrementWeights}). The selector picks the unassigned variable with the
 * smallest ratio of {@code domainSize / weightedDegree}, where weighted degree is the sum
 * of weights of constraints that involve the variable and at least one other unassigned
 * variable. Variables with no active constraints get ratio {@code Double.MAX_VALUE} and are
 * therefore chosen last.
 * <p>
 * Instances are stateful and not thread-safe. Create one per solve call; weights accumulate
 * across Luby restarts within the same call.
 */
public class DomWdegVariableSelector implements UnassignedVariableSelector {

    private final Map<Constraint, Long> weights;

    public DomWdegVariableSelector(@NonNull Set<Constraint> constraints) {
        weights = new HashMap<>(constraints.size() * 2);
        constraints.forEach(c -> weights.put(c, 1L));
    }

    /**
     * Increments the weight of every constraint that involves {@code variable} and at least
     * one other variable that is still unassigned in {@code nextAssignment} (the assignment
     * after {@code variable} was assigned). Call this whenever MAC inference returns empty.
     */
    public void incrementWeights(@NonNull ConstraintSatisfactionProblem csp,
                                 @NonNull Variable<?> variable,
                                 @NonNull Assignment nextAssignment) {
        for (Constraint c : csp.getConstraints()) {
            if (isActive(c, variable, nextAssignment)) {
                weights.merge(c, 1L, Long::sum);
            }
        }
    }

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> assignment.getValue(e.getKey()).isEmpty())
                .min(Comparator.comparingDouble(e -> ratio(csp, e.getKey(), e.getValue().size(), assignment)))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No unassigned variable found"));
    }

    private double ratio(@NonNull ConstraintSatisfactionProblem csp,
                         @NonNull Variable<?> variable,
                         long domainSize,
                         @NonNull Assignment assignment) {
        long wdeg = 0;
        for (Constraint c : csp.getConstraints()) {
            if (isActive(c, variable, assignment)) {
                wdeg += weights.getOrDefault(c, 1L);
            }
        }
        return wdeg == 0 ? Double.MAX_VALUE : (double) domainSize / wdeg;
    }

    /** A constraint is "active" w.r.t. {@code variable} if it involves that variable and
     *  has at least one other variable still unassigned in {@code assignment}.
     *  {@link NogoodConstraint}s are never active: they are derived artifacts of the current
     *  search path rather than structural problem constraints, and {@code csp} accumulates them
     *  as search descends (each recursive call's {@code csp} is the propagated result of the
     *  nogood-augmented CSP from its parent) — counting them here would leak weight the
     *  heuristic wasn't designed to see into variable ordering. */
    private boolean isActive(@NonNull Constraint c,
                             @NonNull Variable<?> variable,
                             @NonNull Assignment assignment) {
        if (c instanceof NogoodConstraint) return false;
        if (!c.getVariables().contains(variable)) return false;
        return c.getVariables().stream()
                .anyMatch(v -> !v.equals(variable) && assignment.getValue(v).isEmpty());
    }
}
