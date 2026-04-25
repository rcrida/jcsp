package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.MAC;
import org.jcsp.solver.backtrackingsearch.BacktrackingSearch;
import org.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import org.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import org.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import org.jcsp.solver.tree.TreeSolver;
import org.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver;
import org.jcsp.solver.tree.decomposition.TreeDecompositionSolver;
import org.jcsp.solver.tree.decomposition.decomposer.TreeDecomposerImpl;
import org.jcsp.solver.tree.decomposition.decomposer.variableselector.ArbitraryVariableSelector;
import org.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import org.jcsp.solver.tree.sorter.BFSTopologicalSorter;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents a generic interface responsible for solving constraint satisfaction problems (CSPs).
 * A CSP consists of a set of variables, their potential values (domains), and constraints
 * that specify relationships between these variables.
 * <p>
 * Implementations of the Solver interface provide methods to derive solutions for a given CSP,
 * either as a single solution or as a stream of solutions.
 */
public interface Solver {
    default Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);

    interface Factory {
        Factory INSTANCE = () -> {
            val backtrackingSearch = new BacktrackingSearch(MinimumRemainingValuesSelector.INSTANCE, LeastConstrainingValueOrderer.INSTANCE, MAC.INSTANCE);
            val treeSolver = new TreeSolver(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);
            val cutsetConditioningSolver = new CutsetConditioningSolver(
                    backtrackingSearch,
                    treeSolver);
            val treeDecompositionSolver = new TreeDecompositionSolver(new TreeDecomposerImpl(ArbitraryVariableSelector.Factory.INSTANCE), treeSolver, cutsetConditioningSolver, 1024);
            val independentSubproblemSolver = new IndependentSubproblemSolver(treeDecompositionSolver);
            val arcConsistentSolver = new ArcConsistentSolver(independentSubproblemSolver);
            val nodeConsistentSolver = new NodeConsistentSolver(arcConsistentSolver);
            return nodeConsistentSolver;
        };

        Solver createSolver();
    }
}
