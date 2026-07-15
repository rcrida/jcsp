package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public interface VariableSelectionHeuristic extends Comparator<Variable<?>> {

    /**
     * Takes the current working adjacency graph directly, rather than a {@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem}: every heuristic only ever needs
     * degree/adjacency information, and {@link io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposerImpl}'s
     * elimination loop maintains that graph as a plain map rather than rebuilding a fully
     * validated CSP on every step (see its javadoc for why).
     */
    interface Factory {
        VariableSelectionHeuristic create(@NonNull Map<Variable<?>, Set<Variable<?>>> neighbours);
    }
}
