package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.MAC;
import io.github.rcrida.jcsp.solver.backtrackingsearch.BacktrackingSearch;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.solver.tree.TreeSolver;
import io.github.rcrida.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver;
import io.github.rcrida.jcsp.solver.tree.decomposition.TreeDecompositionSolver;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposerImpl;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.MinimumDegreeVariableSelector;
import io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.tree.sorter.BFSTopologicalSorter;
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
            val treeDecompositionSolver = new TreeDecompositionSolver(new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE), treeSolver, cutsetConditioningSolver, 1024);
            val independentSubproblemSolver = new IndependentSubproblemSolver(treeDecompositionSolver);
            val arcConsistentSolver = new ArcConsistentSolver(independentSubproblemSolver);
            val nodeConsistentSolver = new NodeConsistentSolver(arcConsistentSolver);
            return nodeConsistentSolver;
        };

        Solver createSolver();
    }
}
