package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.SetBranchingSolver;
import io.github.rcrida.jcsp.variables.Variable;

/**
 * Backtracking-search-tree mechanics, fired by {@link DomWdegLubySearch}, {@link
 * BranchAndBoundSolver} (both derive candidate {@link Assignment}s via {@link
 * Assignment#withValue}), and {@link SetBranchingSolver} -- except {@link #onRestart}, specific to
 * {@link DomWdegLubySearch}'s Luby restarts ({@link BranchAndBoundSolver}/{@link SetBranchingSolver}
 * have no restart concept), and {@link #onSetBranchExplored}, specific to {@link SetBranchingSolver}
 * (its force-in/exclude-from branch decisions have no {@link Assignment} to report the way {@link
 * #onNodeExplored}/{@link #onBacktrack} do, so they get their own event rather than being forced
 * into that shape).
 */
public interface SearchTreeListener {
    /** A variable was assigned a value at a search node. */
    default void onNodeExplored(Variable<?> variable, Object value, Assignment assignment) {}

    /** Search backtracked at {@code variable}. */
    default void onBacktrack(Variable<?> variable, Assignment assignment) {}

    /** A nogood was learned and recorded (only when CDCL is enabled). */
    default void onNogoodLearned(NogoodConstraint nogood) {}

    /** A Luby restart budget was exhausted and search is restarting ({@link DomWdegLubySearch} only). */
    default void onRestart(int restartNumber) {}

    /**
     * A {@link SetBranchingSolver} branch step was explored ({@link SetBranchingSolver} only):
     * {@code element} was tried forced into ({@code forcedIn} true) or excluded from ({@code
     * forcedIn} false) {@code variable}, narrowing its domain to {@code narrowedDomain} -- the
     * direct result of this one decision, before repropagation's own further narrowing (reported
     * separately via {@link PropagationListener#onPropagatorProgress}). {@code feasible} is whether
     * repropagation after this decision succeeded ({@code true}) or the branch was immediately
     * pruned ({@code false} -- the set-CP analogue of {@link #onBacktrack}, folded into this one
     * event since neither outcome has an {@link Assignment} to report separately).
     */
    default void onSetBranchExplored(Variable<?> variable, Object element, boolean forcedIn,
                                      Domain<?> narrowedDomain, boolean feasible) {}
}
