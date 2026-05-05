package io.github.rcrida.jcsp.solver.tree.decomposition;

import lombok.Value;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * Transforms a problem into a tree representation by creating groups of variables (clique/junction tree)
 */
@Value
public class TreeDecompositionSolver implements Solver {
    @NonNull TreeDecomposer treeDecomposer;
    @NonNull Solver treeSolver;
    @NonNull Solver defaultSolver;
    int maxDomainSize;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return treeDecomposer.decompose(csp, maxDomainSize)
                .map(treeCsp -> treeSolver.getSolutions(treeCsp).map(this::recomposeAssignment))
                .orElseGet(() -> defaultSolver.getSolutions(csp));
    }

    /**
     * Map solution of tree CSP back to solution of original CSP. Each value of each variable of the tree CSP is
     * an assignment to variables of the original problem, hence we just need to merge all of those assignments
     * together for the final result, given there will be multiple assignments to each of the original variables
     * duplicated in multiple tree variables.
     *
     * @param treeAssignment solution as an assignment of assignments to the original variables
     * @return an assignment to the original variables
     */
    private Assignment recomposeAssignment(@NonNull Assignment treeAssignment) {
        return treeAssignment.getValues().values().stream()
                .map(a -> (Assignment) a)
                .reduce(Assignment.EMPTY, Assignment::merge);
    }
}
