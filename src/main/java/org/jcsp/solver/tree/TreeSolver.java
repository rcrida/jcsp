package org.jcsp.solver.tree;

import org.jcsp.TreeConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.search.order.DefaultValueOrderer;
import org.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import org.jcsp.solver.tree.sorter.BFSTopologicalSorter;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

public interface TreeSolver {
    default Optional<Assignment> getSolution(@NonNull TreeConstraintSatisfactionProblem tcsp) {
        return getSolutions(tcsp).findFirst();
    }

    Stream<Assignment> getSolutions(@NonNull TreeConstraintSatisfactionProblem tcsp);

    interface Factory {
        Factory INSTANCE = () -> new TreeSolverImpl(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);

        TreeSolver createTreeSolver();
    }
}
