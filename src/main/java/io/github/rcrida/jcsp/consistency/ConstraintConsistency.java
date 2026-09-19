package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * A single consistency/propagation pass over a {@link ConstraintSatisfactionProblem}.
 * Returns the reduced problem, or {@link Optional#empty()} if infeasibility is detected.
 */
@FunctionalInterface
public interface ConstraintConsistency {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp);

    /**
     * Variant of {@link #apply(ConstraintSatisfactionProblem)} that accepts a hint of which
     * variables' domains changed since this consistency pass last ran in the current fixpoint
     * loop (see {@link io.github.rcrida.jcsp.solver.FixpointPropagation#applyFixpoint}), or {@code null} meaning "unknown
     * — assume everything may have changed". A pass with nothing to skip inherits this default,
     * which simply ignores the hint and delegates to {@link #apply}. Three implementors override
     * it. {@link io.github.rcrida.jcsp.consistency.arc.AC3} (and {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3BitRm}) seeds its revise queue from the hint instead
     * of re-enqueuing every arc in the problem — its cost scales with <em>arc</em> count, not
     * constraint count, so an unseeded queue re-revises the whole graph at every search node. Both
     * {@link io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency} and {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency} override it: each can
     * back a constraint count that grows past "fixed and small" (a learned nogood set grows
     * unboundedly over a search, per {@link io.github.rcrida.jcsp.assignments.NogoodStore}; an
     * XCSP3 {@code <group>}-templated constraint type can have thousands of instances from a single
     * parsed instance file), so skipping constraint objects that don't reference any changed
     * variable is where this hint pays for itself.
     */
    default Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp,
                                                           @Nullable Set<Variable<?>> changedSinceLastRun) {
        return apply(csp);
    }

    /**
     * Every variable whose narrowing could let this pass prune something, or {@code null} for "any
     * variable might -- always re-run me". Used by {@link
     * io.github.rcrida.jcsp.solver.FixpointPropagation}'s propagator worklist to decide which
     * propagators a given domain change needs to wake, instead of re-running every one of them.
     * <p>
     * The {@code null} default is the safe answer, not a degenerate one: over-waking a pass only
     * costs a call that narrows nothing, whereas under-waking one would silently weaken the
     * fixpoint. An implementor should override this only when it can name its variables cheaply and
     * exactly -- and, critically, when that set cannot change between calls for a given {@link
     * ConstraintSatisfactionProblem} structure, since the worklist memoizes the resulting index
     * against the constraint graph. That rules out {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency}, whose nogood set grows
     * as search learns; it keeps this default and is cheap to over-wake anyway, since it sits last
     * in the propagator ordering and so is reached only once per drain cycle regardless of how many
     * times it was woken.
     */
    default @Nullable Set<Variable<?>> variablesCovered(ConstraintSatisfactionProblem csp) {
        return null;
    }

    /**
     * Whether this pass leaves its own constraints at a fixpoint before returning, so that nothing it
     * narrowed itself can give it more to do. {@link
     * io.github.rcrida.jcsp.solver.FixpointPropagation}'s propagator worklist uses this to skip
     * re-waking a pass for its own changes -- the difference between converging in one invocation and
     * being re-entered once per wave of narrowing, which on a problem with thousands of constraints of
     * one type is the difference between a win and a regression.
     * <p>
     * {@code false} is the safe default: a pass that iterates a <em>fixed</em> set of constraints to
     * fixpoint (as {@link io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency} does)
     * has not necessarily finished, since narrowing a variable can make a constraint outside that set
     * revisable, and it needs the outer worklist to bring it back.
     */
    default boolean convergesInternally() {
        return false;
    }

    /**
     * Re-runs this consistency pass with reason tracking and returns the nogood that explains
     * the conflict, or {@link Optional#empty()} if this pass did not detect a conflict.
     * The default returns empty; subclasses that support explanation override this.
     */
    default Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        return Optional.empty();
    }

    /**
     * Variant of {@link #apply(ConstraintSatisfactionProblem, Set)} that also explains a failure as
     * part of the same pass, rather than requiring a caller to separately re-derive one afterward via
     * {@link #explainConflict}. The default preserves that two-step fallback (apply, then on failure
     * explainConflict) for any implementor that doesn't override it; {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency}, {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3}, and {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency} override it with a
     * genuine single traversal instead: each calls its underlying {@code propagate}/{@code revise}
     * exactly once per constraint/arc — identical cost to {@link #apply} on the feasible path — and
     * only computes a reason at the exact point a wipeout is found, never as a separate replay.
     */
    default ConsistencyResult applyWithReason(ConstraintSatisfactionProblem csp,
                                              @Nullable Set<Variable<?>> changedSinceLastRun) {
        return apply(csp, changedSinceLastRun)
                .map(ConsistencyResult::feasible)
                .orElseGet(() -> ConsistencyResult.infeasible(explainConflict(csp).orElse(null)));
    }
}
