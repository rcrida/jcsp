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
import java.util.IdentityHashMap;
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
 * <p>
 * {@link #recordConflict} implements last-conflict reasoning (Lecoutre, Saïs, Tabary &amp; Vion
 * 2009): {@link #select} checks it first, ahead of the dom/wdeg ratio computation (and its own
 * tie-break) entirely, and immediately re-selects the most recently failed variable as long as it's
 * still unassigned. This is a different mechanism from dom/wdeg's own weight accumulation, not a
 * substitute for it -- weights are a slow, aggregate signal that dominates the ratio only after
 * many nodes' worth of evidence, while last-conflict is an immediate, per-backtrack override that
 * exploits failure locality (whatever variable just blocked progress is likely to block it again
 * nearby) to fail fast and prune a larger subtree, instead of wasting nodes on other variables
 * before naturally working back around to the same bottleneck.
 */
public class DomWdegVariableSelector implements UnassignedVariableSelector {

    /**
     * Keyed by object identity, not {@link Constraint#equals}/{@link Constraint#hashCode}: the
     * constraint objects populating this map are fixed at construction and the exact same
     * references are looked up throughout the life of the selector (structural constraints never
     * get rebuilt mid-search -- only nogoods are added, tracked separately and never indexed
     * here), so identity is not just adequate but the actually-intended semantics for this
     * per-instance bookkeeping. Matters because {@link #ratio} calls {@link Map#getOrDefault} once
     * per active constraint per unassigned variable per node -- for a constraint whose {@code
     * hashCode}/{@code equals} isn't {@code O(1)} (e.g. {@link
     * io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint} over a large table), a plain
     * {@link HashMap} recomputes that cost on every single lookup. Confirmed via JFR profiling of
     * a real XCSP3 instance ({@code Steiner3-08.xml.lzma}, 36 table constraints averaging 80,640
     * tuples each) where this was found to dominate search wall-clock time.
     */
    private final Map<Constraint, Long> weights;
    private final Map<Variable<?>, List<Constraint>> constraintsByVariable;
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
        weights = new IdentityHashMap<>(constraints.size() * 2);
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

    /**
     * Records {@code variable} as the site of the most recent search failure -- call this at
     * every backtrack/domain-wipeout site, both the {@link #incrementWeights} sites (an inference
     * pass detected a domain wipeout) and the plain direct-consistency-violation sites that
     * precede inference (see {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch}'s own call
     * sites for both). See this class's own Javadoc for why this is a different mechanism from
     * {@link #incrementWeights}, not a substitute for it.
     */
    public void recordConflict(@NonNull Variable<?> variable) {
        lastConflictVariable = variable;
    }

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        if (lastConflictVariable != null && csp.getVariableDomains().containsKey(lastConflictVariable)
                && !assignment.isAssigned(lastConflictVariable)) {
            return lastConflictVariable;
        }
        double bestRatio = Double.MAX_VALUE;
        List<Variable<?>> tied = new ArrayList<>();
        for (Map.Entry<Variable<?>, Domain<?>> e : csp.getVariableDomains().entrySet()) {
            if (assignment.isAssigned(e.getKey())) continue;
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
     *  to involve {@code variable} and to not be a {@link NogoodConstraint}.
     *  <p>
     *  A plain loop over {@link Assignment#getValues()} rather than a {@code stream().anyMatch(...)}
     *  over {@link Assignment#getValue}: this runs once per (unassigned variable × incident
     *  constraint) at every search node, so the stream pipeline and the {@link java.util.Optional}
     *  per variable examined were among the largest allocation sources in the whole solver -- the
     *  same stream-construction cost {@code NogoodFixpointConsistency#relevant} and {@link
     *  Assignment#isConsistentAmong} were each already fixed for. */
    private boolean isActive(@NonNull Constraint c, @NonNull Variable<?> variable, @NonNull Assignment assignment) {
        for (Variable<?> v : c.getVariables()) {
            if (!v.equals(variable) && !assignment.isAssigned(v)) return true;
        }
        return false;
    }
}
