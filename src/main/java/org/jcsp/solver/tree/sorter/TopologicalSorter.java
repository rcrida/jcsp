package org.jcsp.solver.tree.sorter;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Used to provide a list of the arcs in a tree {@link ConstraintSatisfactionProblem} topologically
 * sorted relative to a provided root variable.
 */
public interface TopologicalSorter {
    List<Arc> sort(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable root);
}
