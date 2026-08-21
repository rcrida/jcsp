package io.github.rcrida.jcsp.solver.tree.cutsetconditioning;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverCancelledException;
import io.github.rcrida.jcsp.solver.SolverDecorator;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Splits a problem into a single tree and remaining cutset (which could contain additional trees). Recursively decomposes remaining
 * cutset until no further trees can be found. It then applies cutset conditioning where for each solution for the cutset, it
 * conditions the domains of the tree and finds solutions for them.
 *
 * <p>{@code inner} is the fallback solver for the cycle cutset (typically {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver})
 * and also the optimization target for {@code getSolutions(csp, objective)}.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CutsetConditioningSolver extends SolverDecorator {
    @NonNull Solver treeSolver;
    @Builder.Default @NonNull Cancellation cancellation = Cancellation.NEVER;
    @Builder.Default @NonNull Statistics statistics = new Statistics();

    /**
     * Represents the decomposition of a CSP into a cycle cutset and tree for the purpose of solving using cutset conditioning.
     *
     * @param cycleCutset a CSP representing the cycle cutset
     * @param tree a CSP representing the tree component of the problem
     * @param overlappingConstraints the set of constraints that straddle between the cycle cutset and the tree
     */
    record Decomposition(@NonNull ConstraintSatisfactionProblem cycleCutset, @NonNull ConstraintSatisfactionProblem tree, @NonNull Set<Constraint> overlappingConstraints) {
        /**
         * Constrains the domains of the tree variable to be consistent with the assignment of variables to the
         * cutset and the {@link #overlappingConstraints}.
         *
         * @param cutsetAssignment an assignment that solves the cycle cutset
         * @return the tree problem with variable domains constrained to be consistent with the cutset assignment.
         */
        public Optional<ConstraintSatisfactionProblem> constrainTree(@NonNull Assignment cutsetAssignment) {
            log.debug("Constrain tree with cycle cutset {}", cutsetAssignment);
            val variableDomains = new HashMap<>(tree.getVariableDomains());
            for (Constraint constraint : overlappingConstraints) {
                val overlappingVariables = new HashSet<>(constraint.getVariables());
                overlappingVariables.retainAll(variableDomains.keySet());
                for (Variable<?> X_i : overlappingVariables) {
                    val D_i = (DiscreteDomain<?>) variableDomains.get(X_i);
                    val revisedDomain = revise(X_i, D_i, cutsetAssignment, constraint);
                    if (revisedDomain.isEmpty()) {
                        log.debug("Domain of variable {} is empty during cutset conditioning", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedDomain);
                }
            }
            val constrainedTree = tree.withDomains(variableDomains);
            log.debug("Constrained tree {}", constrainedTree);
            return Optional.of(constrainedTree);
        }

        private DiscreteDomain<?> revise(@NonNull Variable<?> X_i, @NonNull DiscreteDomain<?> D_i, @NonNull Assignment cutsetAssignment, @NonNull Constraint constraint) {
            val valuesToDelete = D_i.stream()
                    .filter(x -> !constraint.isSatisfiedBy(cutsetAssignment.withValue(X_i, x)))
                    .toList();
            val revisedBuilder = D_i.toBuilder();
            valuesToDelete.forEach(revisedBuilder::delete);
            return revisedBuilder.build();
        }
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.debug("getSolutions {}", csp);
        if (csp.isTree()) {
            log.info("Entire problem is a tree, solve using treeSolver");
            return treeSolver.getSolutions(csp);
        }
        return decomposeCsp(csp)
                .map(decomposition -> getSolutions(decomposition.cycleCutset)
                        .takeWhile(cutsetAssignment -> !cancellation.isCancelled())
                        .flatMap(cutsetAssignment -> decomposition.constrainTree(cutsetAssignment).stream()
                                .flatMap(treeSolver::getSolutions)
                                .map(cutsetAssignment::merge)))
                .orElseGet(() -> getInner().getSolutions(csp));
    }

    /**
     * The cutset-solution enumeration below (recursively decomposing, then trying every cutset
     * assignment against {@link #treeSolver} until one extends to a full solution) is this
     * decorator's own search algorithm -- not simply {@code getSolutions().findFirst()} -- so, like
     * {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, it needs its own {@link
     * #cancellation} check rather than relying solely on {@code getInner()}'s inner search to
     * notice a cancellation: for a constraint graph whose cutset conditioning turns out far more
     * expensive than {@link #isComplexityDecreased} estimated, the combinatorial (cutset assignment
     * x tree attempt) space can be enormous even though every individual step is cheap, and {@code
     * getInner()} is never reached at this level at all when {@link #decomposeCsp} keeps finding a
     * further decomposition. {@link #getSolutions(ConstraintSatisfactionProblem)} above truncates
     * silently via {@link Stream#takeWhile}, matching {@link Stream}'s own contract elsewhere in
     * this codebase; this method instead throws {@link SolverCancelledException} once cancellation
     * is detected and no solution was found, the same distinguishing signal {@link
     * io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution} gives its own caller, so a cancelled
     * search isn't misreported as genuine UNSAT. Processed via {@link #solveByCutsetBatches} rather
     * than one big {@code .parallel()} stream over {@link #getSolutions(ConstraintSatisfactionProblem)}
     * directly -- both a {@link Stream#takeWhile} gate and a per-element check inside {@code
     * flatMap} were tried and confirmed unsafe (real {@link OutOfMemoryError}s, the first from
     * {@code takeWhile}'s own parallel-mode buffering, the second from the JDK's unsized-source
     * spliterator batching growing without bound when nothing ever matches -- neither is specific
     * to the cutset-assignment source itself, so no restructuring of just the check avoids them).
     */
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        if (csp.isTree()) {
            return treeSolver.getSolution(csp);
        }
        Optional<Assignment> solution = decomposeCsp(csp)
                .map(this::solveByCutsetBatches)
                .orElseGet(() -> getInner().getSolution(csp));
        if (solution.isEmpty() && cancellation.isCancelled()) {
            throw new SolverCancelledException(statistics);
        }
        return solution;
    }

    /**
     * Cutset assignments this large in one batch, {@code .parallel()}'d, are safe: a {@link List}
     * has a known, finite size, unlike the raw {@link #getSolutions(ConstraintSatisfactionProblem)}
     * stream itself (see {@link #getSolution}'s own Javadoc for why parallelizing that stream
     * directly isn't).
     */
    private static final int CUTSET_BATCH_SIZE = 10_000;

    /**
     * Pulls {@link #getSolutions(ConstraintSatisfactionProblem)}'s cutset-assignment stream in
     * bounded batches of {@link #CUTSET_BATCH_SIZE} rather than parallelizing over it directly,
     * trying each batch (in parallel) against {@link #treeSolver} before pulling the next; checks
     * {@link #cancellation} between batches, bounding cancellation latency to one batch's worth of
     * work instead of the whole (potentially unbounded) enumeration.
     */
    private Optional<Assignment> solveByCutsetBatches(Decomposition decomposition) {
        Iterator<Assignment> cutsetAssignments = getSolutions(decomposition.cycleCutset).iterator();
        while (cutsetAssignments.hasNext() && !cancellation.isCancelled()) {
            List<Assignment> batch = new ArrayList<>(CUTSET_BATCH_SIZE);
            while (batch.size() < CUTSET_BATCH_SIZE && cutsetAssignments.hasNext()) {
                batch.add(cutsetAssignments.next());
            }
            Optional<Assignment> found = batch.stream()
                    .parallel()
                    .flatMap(cutsetAssignment -> decomposition.constrainTree(cutsetAssignment).stream()
                            .flatMap(treeSolver::getSolutions)
                            .map(cutsetAssignment::merge))
                    .findAny();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to decompose a problem into a combination of a tree and a cycle cutset. Iterates through all the variables
     * until it finds one that can be expanded to a tree. It uses the heuristic that starting with the least connected
     * variables means they are less likely to be part of the cutset.
     *
     * @param csp a problem that may contain a tree
     * @return if a tree is found then a decomposition containing the tree and remaining cycleCutset, otherwise empty
     */
    private Optional<Decomposition> decomposeCsp(@NonNull ConstraintSatisfactionProblem csp) {
        val unsplittableVariables = csp.getUnsplittableVariables();
        val constraintCounts = computeConstraintCounts(csp);
        return csp.getVariableDomains().keySet().stream()
                .filter(Predicate.not(unsplittableVariables::contains))
                .sorted(Comparator.comparing(constraintCounts::get))
                .map(variable -> decomposeCsp(csp, unsplittableVariables, variable, constraintCounts))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Map<Variable<?>, Integer> computeConstraintCounts(@NonNull ConstraintSatisfactionProblem csp) {
        val counts = new HashMap<Variable<?>, Integer>();
        csp.getVariableDomains().keySet().forEach(v -> counts.put(v, 0));
        csp.getConstraints().forEach(c -> c.getVariables().forEach(v -> counts.merge(v, 1, Integer::sum)));
        return counts;
    }

    /**
     * Expands from the specified variable to find the tree of nodes that include that variable.
     *
     * @param csp
     * @param unsplittableVariables variables that should not be included in the tree because they are
     *                              part of uncomposable n-ary constraints.
     * @param variable seed variable to expand to tree
     * @return
     */
    private Optional<Decomposition> decomposeCsp(@NonNull ConstraintSatisfactionProblem csp, @NonNull Set<Variable<?>> unsplittableVariables, @NonNull Variable<?> variable, @NonNull Map<Variable<?>, Integer> constraintCounts) {
        log.debug("Decompose from {}", variable);
        val queue = new ArrayDeque<Variable<?>>();
        queue.add(variable);
        val visited = new HashSet<Variable<?>>();
        val treeVariables = new HashSet<Variable<?>>();
        val neighbours = csp.getNeighbours();
        while (!queue.isEmpty()) {
            val node = queue.poll();
            visited.add(node);
            val cloneSet = new HashSet<>(neighbours.get(node));
            cloneSet.retainAll(treeVariables);
            if (cloneSet.size() < 2) {
                treeVariables.add(node);
                val unvisited = neighbours.get(node).stream()
                        .filter(Predicate.not(unsplittableVariables::contains))
                        .filter(v -> csp.getVariableDomains().containsKey(v))
                        .sorted(Comparator.comparing(constraintCounts::get))
                        .filter(v -> !visited.contains(v))
                        .toList();
                queue.addAll(unvisited);
            }
        }
        val treeSize = treeVariables.size();
        val problemSize = csp.getVariableDomains().size();
        val cycleCutsetSize = problemSize - treeSize;
        if (treeSize > 1 && isComplexityDecreased(csp, cycleCutsetSize)) {
            final Predicate<Variable<?>> treePredicate = treeVariables::contains;
            final Predicate<Variable<?>> cycleCutsetPredicate = Predicate.not(treePredicate);
            val cycleCutset = csp.withVariableSubset(cycleCutsetPredicate);
            val tree = csp.withVariableSubset(treePredicate);
            val overlappingConstraints = new HashSet<>(csp.getConstraints());
            overlappingConstraints.removeAll(cycleCutset.getConstraints());
            overlappingConstraints.removeAll(tree.getConstraints());
            val decomposition = new Decomposition(cycleCutset, tree, overlappingConstraints);
            log.debug("Found decomposition {}", decomposition);
            return Optional.of(decomposition);
        }
        return Optional.empty();
    }

    /**
     * Cutset conditioning enumerates one cutset assignment per combination of cutset-variable
     * values ({@code d^cycleCutsetSize} below) -- a real absolute ceiling on that count, not just a
     * relative one, since {@link #isComplexityDecreased}'s own {@code cspComplexity} baseline
     * (raw {@code d^n}, no propagation credit) is generous enough that a decomposition can look
     * "better than brute force" while still being astronomically intractable in absolute terms.
     * Confirmed on a real instance ({@code ColouredQueens-07.xml.lzma}, a densely-connected 7x7
     * board where every cell is constrained against its row, column, and diagonal neighbors): the
     * tree-expansion heuristic above can only ever find a tiny tree, so the cutset came out to 38 of
     * 49 variables (domain size 7) -- {@code 7^38 ≈ 3e32} cutset assignments, "better" than the
     * {@code ~2.6e41} raw baseline by this method's own math, but hopeless either way, causing
     * {@link #getSolution} to burn CPU (or, once cancellation was wired in, memory) indefinitely.
     * Mirrors {@code Xcsp3CallbackHandler#MAX_MATERIALIZED_DOMAIN_SIZE}'s own value for the same
     * "too big to ever be worth materializing" judgment call.
     */
    private static final double MAX_CUTSET_ASSIGNMENTS = 1_000_000;

    /**
     * Is conditioning applied to this cycle cutset expected to decrease the overall problem complexity?
     *
     * @param csp overall problem
     * @param cycleCutsetSize the number of variables in the cycle cutset
     * @return true if cycle cutset condition would decrease the complexity of solving the problem
     */
    private boolean isComplexityDecreased(@NonNull ConstraintSatisfactionProblem csp, int cycleCutsetSize) {
        val n = csp.getNumVariables();
        val d = csp.getVariableDomains().values().stream()
                .map(Domain::size)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        val c = cycleCutsetSize;
        val cutsetAssignments = Math.pow(d, c);
        if (cutsetAssignments > MAX_CUTSET_ASSIGNMENTS) {
            log.debug("cutset assignments {} exceeds absolute cap {}, declining decomposition", cutsetAssignments, MAX_CUTSET_ASSIGNMENTS);
            return false;
        }
        val cspComplexity = csp.getSearchSpace().doubleValue();
        val cutsetConditioningComplexity = cutsetAssignments * (n - c) * Math.pow(d, 2);
        log.debug("csp -> {}, cutset -> {}", cspComplexity, cutsetConditioningComplexity);
        return cutsetConditioningComplexity < cspComplexity;
    }
}
