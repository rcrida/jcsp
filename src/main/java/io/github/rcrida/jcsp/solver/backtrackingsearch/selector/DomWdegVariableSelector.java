package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 * The constructor's {@code constraints} set is fixed for the life of the instance and indexed
 * once into {@link #constraintsByVariable}, rather than re-derived from the live CSP on every
 * {@link #select}/{@link #incrementWeights} call: {@link NogoodConstraint}s are never active
 * (see below) and {@code csp.getConstraints()} in production always includes every nogood
 * learned so far (up to {@code 20 * variableCount}, see {@link io.github.rcrida.jcsp.assignments.NogoodStore}), so re-scanning it
 * per unassigned variable per node would mean re-discovering and discarding every one of those
 * nogoods again and again for no benefit.
 * <p>
 * Instances are stateful and not thread-safe. Create one per solve call; weights accumulate
 * across Luby restarts within the same call.
 * <p>
 * {@link #reseedTieBreak} lets a caller (namely {@link
 * io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, once per Luby restart, via a
 * configured {@link RestartRandomization}) control how ties in {@link #select}'s minimum ratio are
 * broken. Left unseeded ({@code null}, the default), ties are broken by {@code
 * csp.getVariableDomains()}'s iteration order (a {@code LinkedHashMap}, so this is the
 * first-declared tied variable, deterministically) -- today's exact behaviour, unchanged.
 */
public class DomWdegVariableSelector implements UnassignedVariableSelector {

    private final Map<Constraint, Long> weights;
    private final Map<Variable<?>, List<Constraint>> constraintsByVariable;
    private @Nullable Random tieBreakRandom;

    /**
     * {@link NogoodConstraint}s in {@code constraints} are dropped entirely rather than indexed:
     * they are derived artifacts of the current search path rather than structural problem
     * constraints, so counting them would leak weight the heuristic wasn't designed to see into
     * variable ordering. In production this constructor only ever sees the top-level CSP's
     * structural constraints (before any nogood has been learned), so this is a no-op there —
     * the filter exists for direct/test construction with a nogood already present.
     */
    public DomWdegVariableSelector(@NonNull Set<Constraint> constraints) {
        weights = new HashMap<>(constraints.size() * 2);
        constraintsByVariable = new HashMap<>();
        for (Constraint c : constraints) {
            if (c instanceof NogoodConstraint) continue;
            weights.put(c, 1L);
            for (Variable<?> v : c.getVariables()) {
                constraintsByVariable.computeIfAbsent(v, k -> new ArrayList<>()).add(c);
            }
        }
    }

    /**
     * Increments the weight of every constraint that involves {@code variable} and at least
     * one other variable that is still unassigned in {@code nextAssignment} (the assignment
     * after {@code variable} was assigned). Call this whenever MAC inference returns empty.
     */
    public void incrementWeights(@NonNull Variable<?> variable, @NonNull Assignment nextAssignment) {
        for (Constraint c : constraintsByVariable.getOrDefault(variable, List.of())) {
            if (isActive(c, variable, nextAssignment)) {
                weights.merge(c, 1L, Long::sum);
            }
        }
    }

    /**
     * Sets (or clears, via {@code null}) the {@link Random} used to break ties in {@link #select}'s
     * minimum ratio. Called once per Luby restart by {@link
     * io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, with whatever the configured
     * {@link RestartRandomization} returns for that restart -- {@code null} for {@link
     * RestartRandomization#NONE}, restoring today's deterministic first-tied-candidate behaviour.
     */
    public void reseedTieBreak(@Nullable Random random) {
        this.tieBreakRandom = random;
    }

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        double bestRatio = Double.MAX_VALUE;
        List<Variable<?>> tied = new ArrayList<>();
        for (Map.Entry<Variable<?>, Domain<?>> e : csp.getVariableDomains().entrySet()) {
            if (assignment.getValue(e.getKey()).isPresent()) continue;
            double ratio = ratio(e.getKey(), e.getValue().size(), assignment);
            if (ratio < bestRatio) {
                bestRatio = ratio;
                tied.clear();
                tied.add(e.getKey());
            } else if (ratio == bestRatio) {
                tied.add(e.getKey());
            }
        }
        if (tied.isEmpty()) throw new IllegalStateException("No unassigned variable found");
        return tieBreakRandom == null || tied.size() == 1
                ? tied.get(0)
                : tied.get(tieBreakRandom.nextInt(tied.size()));
    }

    private double ratio(@NonNull Variable<?> variable, long domainSize, @NonNull Assignment assignment) {
        long wdeg = 0;
        for (Constraint c : constraintsByVariable.getOrDefault(variable, List.of())) {
            if (isActive(c, variable, assignment)) {
                wdeg += weights.getOrDefault(c, 1L);
            }
        }
        return wdeg == 0 ? Double.MAX_VALUE : (double) domainSize / wdeg;
    }

    /** A constraint is "active" w.r.t. {@code variable} if it has at least one other variable
     *  still unassigned in {@code assignment}. Callers only ever pass a constraint drawn from
     *  {@link #constraintsByVariable}'s entry for {@code variable}, so it's already guaranteed
     *  to involve {@code variable} and to not be a {@link NogoodConstraint}. */
    private boolean isActive(@NonNull Constraint c, @NonNull Variable<?> variable, @NonNull Assignment assignment) {
        return c.getVariables().stream()
                .anyMatch(v -> !v.equals(variable) && assignment.getValue(v).isEmpty());
    }
}
