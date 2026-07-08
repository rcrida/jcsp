package io.github.rcrida.jcsp.solver.tree.decomposition;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverDecorator;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Transforms a problem into a tree representation by creating groups of variables (clique/junction tree).
 *
 * <p>The clique domain size limit is computed as {@code d^targetTreewidth} (capped at
 * {@value #MAX_DOMAIN_SIZE_CAP}), where {@code d} is the largest variable domain in the problem.
 * This makes the bound domain-aware: binary problems tolerate higher effective treewidth than
 * large-domain ones within the same memory budget.
 *
 * <p>{@code inner} is the fallback solver used when tree decomposition is not applicable or not
 * worth applying. It also receives the problem directly for optimization calls.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TreeDecompositionSolver extends SolverDecorator {
    static final int MAX_DOMAIN_SIZE_CAP = 1_000_000;

    @NonNull TreeDecomposer treeDecomposer;
    @NonNull Solver treeSolver;
    int targetTreewidth;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return decomposeIfWorthwhile(csp)
                .map(treeCsp -> treeSolver.getSolutions(treeCsp).map(this::recomposeAssignment))
                .orElseGet(() -> getInner().getSolutions(csp));
    }

    /**
     * Mirrors {@link #getSolutions}, delegating to {@code treeSolver.getSolution} /
     * {@code getInner().getSolution} instead of taking the first element of the corresponding
     * stream, so a single-solution call still gets the decomposition's speedup rather than
     * silently skipping straight to {@code inner} (which finds a correct but potentially much
     * slower solution without it).
     */
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return decomposeIfWorthwhile(csp)
                .flatMap(treeCsp -> treeSolver.getSolution(treeCsp).map(this::recomposeAssignment))
                .or(() -> getInner().getSolution(csp));
    }

    /**
     * Shared by {@link #getSolutions} and {@link #getSolution}: decomposes {@code csp} into a
     * tree representation when doing so is expected to help, or {@link Optional#empty()} when
     * decomposition is skipped (minimum degree too high, no decomposition found, or the
     * decomposition's estimated complexity doesn't beat the original problem's).
     */
    private Optional<ConstraintSatisfactionProblem> decomposeIfWorthwhile(@NonNull ConstraintSatisfactionProblem csp) {
        if (isMinDegreeTooHigh(csp, targetTreewidth)) {
            log.debug("Skipping tree decomposition: minimum degree >= targetTreewidth {}", targetTreewidth);
            return Optional.empty();
        }
        int d = csp.getVariableDomains().values().stream()
                .mapToInt(Domain::size)
                .max()
                .orElse(1);
        int maxDomainSize = (int) Math.min(Math.pow(d, targetTreewidth), MAX_DOMAIN_SIZE_CAP);
        log.info("d={}, targetTreewidth={}, maxDomainSize={}", d, targetTreewidth, maxDomainSize);
        return treeDecomposer.decompose(csp, maxDomainSize)
                .filter(treeCsp -> shouldApplyDecomposition(treeCsp, csp));
    }

    /**
     * Returns true when every constrained variable has at least {@code targetTreewidth} neighbours,
     * guaranteeing that minimum-degree elimination will immediately produce a clique whose joint
     * domain exceeds {@code maxDomainSize} — so the decomposer is certain to return empty.
     * Skipping the decomposer in this case is exact, not just a heuristic.
     */
    static boolean isMinDegreeTooHigh(@NonNull ConstraintSatisfactionProblem csp, int targetTreewidth) {
        return csp.getNeighbours().values().stream()
                .filter(neighbours -> !neighbours.isEmpty())
                .mapToInt(Set::size)
                .min()
                .orElse(0) >= targetTreewidth;
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
