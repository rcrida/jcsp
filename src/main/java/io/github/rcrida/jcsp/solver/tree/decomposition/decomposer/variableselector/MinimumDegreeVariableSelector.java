package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * Eliminates the variable with the fewest neighbours in the current graph (minimum degree).
 * This minimises fill-in edges at each elimination step, producing smaller clique bags
 * and lower treewidth decompositions compared to arbitrary elimination order.
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinimumDegreeVariableSelector implements VariableSelectionHeuristic {
    @NonNull
    @Getter(AccessLevel.NONE)
    Map<Variable<?>, Set<Variable<?>>> neighbours;

    @Override
    public int compare(Variable<?> o1, Variable<?> o2) {
        return Comparator.comparingInt((Variable<?> v) -> neighbours.getOrDefault(v, Set.of()).size())
                .compare(o1, o2);
    }

    public static class Factory implements VariableSelectionHeuristic.Factory {
        public static final Factory INSTANCE = new Factory();

        private Factory() {}

        @Override
        public VariableSelectionHeuristic create(@NonNull ConstraintSatisfactionProblem csp) {
            return new MinimumDegreeVariableSelector(csp.getNeighbours());
        }
    }
}
