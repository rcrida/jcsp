package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;

public interface VariableSelectionHeuristic extends Comparator<Variable> {

    interface Factory {
        VariableSelectionHeuristic create(@NonNull ConstraintSatisfactionProblem csp);
    }
}
