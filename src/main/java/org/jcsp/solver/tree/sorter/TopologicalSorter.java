package org.jcsp.solver.tree.sorter;

import org.jcsp.TreeConstraintSatisfactionProblem;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Used to provide a list of the arcs in a {@link TreeConstraintSatisfactionProblem} topologically
 * sorted relative to a provided root variable.
 */
public interface TopologicalSorter {
    List<Arc> sort(@NonNull TreeConstraintSatisfactionProblem tcsp, @NonNull Variable root);
}
