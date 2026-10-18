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
import java.util.Arrays;
import java.util.Collection;
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
 * (see {@link #onConflict}). The selector picks the unassigned variable with the
 * smallest ratio of {@code domainSize / weightedDegree}, where weighted degree is the sum
 * of weights of constraints that involve the variable and at least one other unassigned
 * variable. Variables with no active constraints get ratio {@code Double.MAX_VALUE} and are
 * therefore chosen last.
 * <p>
 * The constructor's {@code constraints} set is fixed for the life of the instance and indexed
 * once into integer arrays (see {@link #variableIndex}), rather than re-derived from the live CSP
 * on every {@link #select}/{@link #onConflict} call: {@link NogoodConstraint}s are never active
 * (see below) and {@code csp.getConstraints()} in production always includes every nogood
 * learned so far (up to {@code 20 * variableCount}, see {@link io.github.rcrida.jcsp.assignments.NogoodStore}), so re-scanning it
 * per unassigned variable per node would mean re-discovering and discarding every one of those
 * nogoods again and again for no benefit.
 * <p>
 * Instances are stateful and not thread-safe. Create one per solve call; weights accumulate
 * across Luby restarts within the same call.
 * <p>
 * {@link #onRestart} lets a caller (namely {@link
 * io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, once per Luby restart, via a
 * configured {@link RestartRandomization}) control how ties in {@link #select}'s minimum ratio are
 * broken. Left unseeded ({@code null}, the default), ties are broken by {@code
 * csp.getVariableDomains()}'s iteration order (a {@code LinkedHashMap}, so this is the
 * first-declared tied variable, deterministically) -- today's exact behaviour, unchanged.
 * <p>
 * {@link #onValueRejected} implements last-conflict reasoning (Lecoutre, Saïs, Tabary &amp; Vion
 * 2009): {@link #select} checks it first, ahead of the dom/wdeg ratio computation (and its own
 * tie-break) entirely, and immediately re-selects the most recently failed variable as long as it's
 * still unassigned. This is a different mechanism from dom/wdeg's own weight accumulation, not a
 * substitute for it -- weights are a slow, aggregate signal that dominates the ratio only after
 * many nodes' worth of evidence, while last-conflict is an immediate, per-backtrack override that
 * exploits failure locality (whatever variable just blocked progress is likely to block it again
 * nearby) to fail fast and prune a larger subtree, instead of wasting nodes on other variables
 * before naturally working back around to the same bottleneck.
 */
public class DomWdegVariableSelector implements AdaptiveVariableSelector {

    /**
     * The constraint graph as integer indices into flat arrays, built once, so that {@link #select},
     * which runs at every search node, flags the assigned variables in one pass, counts each
     * constraint's unassigned ones in another, and otherwise reads arrays -- no per-constraint
     * hash lookup (a {@link Constraint#hashCode} is not {@code O(1)} for a large table) and no
     * {@link Assignment} query per scope entry.
     * <p>
     * Only variables that appear in some structural constraint are indexed; any other has no
     * weighted degree, so {@link #select} gives it the worst ratio without looking further.
     */
    private final Map<Variable<?>, Integer> variableIndex = new HashMap<>();
    private final Variable<?>[] variables;
    /** Per constraint, the indices of its variables. */
    private final int[][] scope;
    /** Per variable, the indices of the constraints it appears in. */
    private final int[][] incident;
    /** Per constraint; each starts at 1 and is raised by {@link #onConflict}. */
    private final long[] weights;
    /** Per variable, scratch for {@link #select}: whether the assignment holds it. */
    private final boolean[] assigned;
    /** Per constraint, scratch for {@link #select}: how many of its variables are unassigned. */
    private final int[] unassigned;
    private @Nullable Random tieBreakRandom;
    private @Nullable Variable<?> lastConflictVariable;

    /**
     * {@link NogoodConstraint}s in {@code constraints} are dropped entirely rather than indexed:
     * they are derived artifacts of the current search path rather than structural problem
     * constraints, so counting them would leak weight the heuristic wasn't designed to see into
     * variable ordering. In production this constructor only ever sees the top-level CSP's
     * structural constraints (before any nogood has been learned), so this is a no-op there —
     * the filter exists for direct/test construction with a nogood already present.
     */
    public DomWdegVariableSelector(@NonNull Set<Constraint> constraints) {
        List<Constraint> structural = constraints.stream().filter(c -> !(c instanceof NogoodConstraint)).toList();
        List<Variable<?>> indexed = new ArrayList<>();
        scope = new int[structural.size()][];
        for (int ci = 0; ci < structural.size(); ci++) {
            Collection<? extends Variable<?>> vars = structural.get(ci).getVariables();
            scope[ci] = new int[vars.size()];
            int k = 0;
            for (Variable<?> v : vars) {
                Integer index = variableIndex.get(v);
                if (index == null) {
                    index = indexed.size();
                    variableIndex.put(v, index);
                    indexed.add(v);
                }
                scope[ci][k++] = index;
            }
        }
        variables = indexed.toArray(new Variable<?>[0]);
        int[] degree = new int[variables.length];
        for (int[] vars : scope) {
            for (int v : vars) degree[v]++;
        }
        incident = new int[variables.length][];
        for (int v = 0; v < variables.length; v++) incident[v] = new int[degree[v]];
        int[] filled = new int[variables.length];
        for (int ci = 0; ci < scope.length; ci++) {
            for (int v : scope[ci]) incident[v][filled[v]++] = ci;
        }
        weights = new long[scope.length];
        Arrays.fill(weights, 1L);
        assigned = new boolean[variables.length];
        unassigned = new int[scope.length];
    }

    /**
     * Increments the weight of every constraint that involves {@code variable} and at least
     * one other variable that is still unassigned in {@code nextAssignment} (the assignment
     * after {@code variable} was assigned) -- dom/wdeg's response to
     * {@link AdaptiveVariableSelector#onConflict}, which MAC inference returning empty raises.
     */
    @Override
    public void onConflict(@NonNull Variable<?> variable, @NonNull Assignment nextAssignment) {
        Integer index = variableIndex.get(variable);
        if (index == null) return;
        for (int ci : incident[index]) {
            if (isActive(ci, index, nextAssignment)) {
                weights[ci]++;
            }
        }
    }

    /**
     * Sets (or clears, via {@code null}) the {@link Random} used to break ties in {@link #select}'s
     * minimum ratio -- dom/wdeg's response to {@link AdaptiveVariableSelector#onRestart}, raised
     * once per Luby restart by {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}
     * with whatever the configured {@link RestartRandomization} returns for that restart. {@code
     * null}, for {@link RestartRandomization#NONE}, restores deterministic first-tied-candidate
     * behaviour.
     */
    @Override
    public void onRestart(@Nullable Random random) {
        this.tieBreakRandom = random;
    }

    /**
     * Returns every constraint weight to its initial {@code 1} and clears the last-conflict
     * variable, so the next {@link #select} is guided by domain sizes alone rather than by failures
     * accumulated before this point -- dom/wdeg's response to
     * {@link AdaptiveVariableSelector#onStagnation}.
     * <p>
     * Raised only by {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, and only
     * after a run of restarts that made no progress -- weights surviving a restart is what makes
     * each one build on the last, so discarding them pays only once that accumulation is
     * demonstrably steering every restart back into the same dead region. Affects variable ordering
     * alone, so it cannot change soundness or completeness.
     */
    @Override
    public void onStagnation() {
        Arrays.fill(weights, 1L);
        lastConflictVariable = null;
    }

    /**
     * Records {@code variable} as the site of the most recent search failure -- dom/wdeg's
     * response to {@link AdaptiveVariableSelector#onValueRejected}, which every rejected candidate
     * raises, whether an inference pass detected the wipeout (the {@link #onConflict} sites) or the
     * direct consistency check that precedes inference did. See this class's own Javadoc for why
     * last-conflict is a different mechanism from the weighting, not a substitute for it.
     */
    @Override
    public void onValueRejected(@NonNull Variable<?> variable) {
        lastConflictVariable = variable;
    }

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        if (lastConflictVariable != null && csp.getVariableDomains().containsKey(lastConflictVariable)
                && !assignment.isAssigned(lastConflictVariable)) {
            return lastConflictVariable;
        }
        markAssigned(assignment);
        double bestRatio = Double.MAX_VALUE;
        List<Variable<?>> tied = new ArrayList<>();
        for (Map.Entry<Variable<?>, Domain<?>> e : csp.getVariableDomains().entrySet()) {
            Integer index = variableIndex.get(e.getKey());
            double ratio;
            if (index == null) {
                // In no structural constraint, so nothing to weigh it by -- but still has to be unassigned.
                if (assignment.isAssigned(e.getKey())) continue;
                ratio = Double.MAX_VALUE;
            } else {
                if (assigned[index]) continue;
                ratio = ratio(index, e.getValue().size());
            }
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

    /**
     * Fills {@link #assigned} and {@link #unassigned} from {@code assignment}: one pass over the
     * assignment's variables, then one over every constraint's scope.
     */
    private void markAssigned(@NonNull Assignment assignment) {
        Arrays.fill(assigned, false);
        for (Variable<?> held : assignment.variables()) {
            Integer index = variableIndex.get(held);
            if (index != null) assigned[index] = true;
        }
        for (int ci = 0; ci < scope.length; ci++) {
            int open = 0;
            for (int v : scope[ci]) {
                if (!assigned[v]) open++;
            }
            unassigned[ci] = open;
        }
    }

    /**
     * The weighted degree sums the weights of {@code variable}'s constraints that have at least one
     * other unassigned variable. {@code variable} is itself unassigned, so that is a constraint
     * with two or more unassigned variables.
     */
    private double ratio(int variable, long domainSize) {
        long wdeg = 0;
        for (int ci : incident[variable]) {
            if (unassigned[ci] >= 2) {
                wdeg += weights[ci];
            }
        }
        return wdeg == 0 ? Double.MAX_VALUE : (double) domainSize / wdeg;
    }

    /** A constraint is "active" w.r.t. {@code variable} if it has at least one other variable
     *  still unassigned in {@code assignment}. Callers only ever pass a constraint drawn from
     *  {@code variable}'s own {@link #incident} entry, so it's already guaranteed to involve it and
     *  to not be a {@link NogoodConstraint}. Only {@link #onConflict} asks, which is rare beside
     *  {@link #select}, so it questions the {@link Assignment} directly. */
    private boolean isActive(int constraint, int variable, @NonNull Assignment assignment) {
        for (int v : scope[constraint]) {
            if (v != variable && !assignment.isAssigned(variables[v])) return true;
        }
        return false;
    }
}
