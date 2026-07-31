package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.variables.Variable;

/**
 * Backtracking-search-tree mechanics, fired by both {@link DomWdegLubySearch} and
 * {@link BranchAndBoundSolver} (both derive candidate {@link Assignment}s via
 * {@link Assignment#withValue}) -- except {@link #onRestart}, which is specific to
 * {@link DomWdegLubySearch}'s Luby restarts; {@link BranchAndBoundSolver} has no restart concept.
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
}
