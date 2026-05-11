package io.github.rcrida.jcsp.solver.tree.decomposition;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Transforms a problem into a tree representation by creating groups of variables (clique/junction tree)
 */
@Slf4j
@Value
public class TreeDecompositionSolver implements Solver {
    @NonNull TreeDecomposer treeDecomposer;
    @NonNull Solver treeSolver;
    @NonNull Solver defaultSolver;
    int maxDomainSize;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return treeDecomposer.decompose(csp, maxDomainSize)
                .filter(treeCsp -> shouldApplyDecomposition(treeCsp, csp))
                .map(treeCsp -> treeSolver.getSolutions(treeCsp).map(this::recomposeAssignment))
                .orElseGet(() -> defaultSolver.getSolutions(csp));
    }

    /**
     * Decides whether tree decomposition is worth applying by comparing estimated complexities.
     * Tree solver cost is O(k × w²) where k is the number of cliques and w is the max clique
     * domain size. This is compared against the original problem's search space.
     *
     * @param treeCsp the decomposed tree CSP
     * @param originalCsp the original problem
     * @return true if tree decomposition is expected to reduce search cost
     */
    static boolean shouldApplyDecomposition(@NonNull ConstraintSatisfactionProblem treeCsp,
                                            @NonNull ConstraintSatisfactionProblem originalCsp) {
        int k = treeCsp.getNumVariables();
        BigInteger maxCliqueDomain = treeCsp.getVariableDomains().values().stream()
                .map(Domain::size)
                .map(BigInteger::valueOf)
                .max(Comparator.naturalOrder())
                .orElse(BigInteger.ONE);
        BigInteger treeComplexity = maxCliqueDomain.pow(2).multiply(BigInteger.valueOf(k));
        BigInteger searchSpace = originalCsp.getSearchSpace();
        log.info("tree decomposition -> {}, search space -> {}", treeComplexity, searchSpace);
        return treeComplexity.compareTo(searchSpace) < 0;
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
                .reduce(Assignment.empty(), Assignment::merge);
    }
}
