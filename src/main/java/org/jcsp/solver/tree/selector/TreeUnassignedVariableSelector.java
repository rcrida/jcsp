package org.jcsp.solver.tree.selector;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A functional interface that defines a strategy for selecting an unassigned variable
 * from a constraint satisfaction problem (CSP) during the search process.
 * <p>
 * Implementations of this interface are typically used in search algorithms to decide
 * which variable to assign a value to next. The selection strategy can significantly
 * influence the efficiency of the search, with different heuristics being suitable
 * for different types of problems.
 */
@FunctionalInterface
public interface TreeUnassignedVariableSelector {
    Variable select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment);

    interface Factory {
        Factory INSTANCE = TopologicalUnassignedVariableSelector::new;

        TreeUnassignedVariableSelector createSelector(@NonNull List<Arc> arcs);
    }
}
