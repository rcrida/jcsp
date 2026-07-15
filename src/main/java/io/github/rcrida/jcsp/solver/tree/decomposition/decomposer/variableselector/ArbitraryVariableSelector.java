package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;

public class ArbitraryVariableSelector implements VariableSelectionHeuristic {
    static final ArbitraryVariableSelector INSTANCE = new ArbitraryVariableSelector();

    private ArbitraryVariableSelector() {}

    @Override
    public int compare(Variable o1, Variable o2) {
        return 0;
    }

    public static class Factory implements VariableSelectionHeuristic.Factory {
        public static final Factory INSTANCE = new Factory();

        private Factory() {}

        @Override
        public VariableSelectionHeuristic create(@NonNull Map<Variable<?>, Set<Variable<?>>> neighbours) {
            return ArbitraryVariableSelector.INSTANCE;
        }
    }
}
