package org.jcsp.solver.tree.decomposition.decomposer.variableselector;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;

public interface VariableSelectionHeuristic extends Comparator<Variable> {

    interface Factory {
        VariableSelectionHeuristic create(@NonNull ConstraintSatisfactionProblem csp);
    }
}
