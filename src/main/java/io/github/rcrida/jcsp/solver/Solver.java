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
import java.util.function.ToDoubleFunction;
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
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);

    default Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    /**
     * Returns a stream of complete assignments in the order they are discovered, each strictly
     * better (lower objective) than the previous. The last element is the global optimum.
     * <p>
     * The default implementation filters the full solution stream — no pruning occurs.
     * Implementations may override this method with branch-and-bound pruning for efficiency.
     * <p>
     * The {@code objective} must satisfy the lower-bound property for partial assignments:
     * {@code objective(partial) ≤ objective(completion)} for any completion. This holds for
     * any additive cost function.
     */
    default Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                             @NonNull ToDoubleFunction<Assignment> objective) {
        double[] incumbent = {Double.MAX_VALUE};
        return getSolutions(csp).filter(candidate -> {
            double cost = objective.applyAsDouble(candidate);
            if (cost < incumbent[0]) {
                incumbent[0] = cost;
                return true;
            }
            return false;
        });
    }

    /**
     * Returns the assignment with the minimum objective value, exhausting the search space.
     */
    default Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp,
                                              @NonNull ToDoubleFunction<Assignment> objective) {
        return getSolutions(csp, objective).reduce((a, b) -> b);
    }

    interface Factory {
        Factory INSTANCE = () -> {
            val backtrackingSearch = new BacktrackingSearch(MinimumRemainingValuesSelector.INSTANCE, LeastConstrainingValueOrderer.INSTANCE, MAC.INSTANCE);
            val branchAndBound = BranchAndBoundSolver.builder().inner(backtrackingSearch).build();
            val treeSolver = new TreeSolver(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);
            val cutsetConditioningSolver = CutsetConditioningSolver.builder()
                    .inner(branchAndBound)
                    .treeSolver(treeSolver)
                    .build();
            val treeDecompositionSolver = TreeDecompositionSolver.builder()
                    .inner(cutsetConditioningSolver)
                    .treeDecomposer(new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE))
                    .treeSolver(treeSolver)
                    .targetTreewidth(7)
                    .build();
            val independentSubproblemSolver = IndependentSubproblemSolver.builder().inner(treeDecompositionSolver).build();
            val arcConsistentSolver = ArcConsistentSolver.builder().inner(independentSubproblemSolver).build();
            return NodeConsistentSolver.builder().inner(arcConsistentSolver).build();
        };

        Solver createSolver();
    }
}
