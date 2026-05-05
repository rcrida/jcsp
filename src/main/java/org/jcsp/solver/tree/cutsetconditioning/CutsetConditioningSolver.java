package org.jcsp.solver.tree.cutsetconditioning;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.solver.Solver;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Splits a problem into a single tree and remaining cutset (which could contain additional trees). Recursively decomposes remaining
 * cutset until no further trees can be found. It then applies cutset conditioning where for each solution for the cutset, it
 * conditions the domains of the tree and finds solutions for them.
 */
@Slf4j
@Value
public class CutsetConditioningSolver implements Solver {
    @NonNull
    Solver cycleCutsetSolver;
    @NonNull
    Solver treeSolver;

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
            log.info("Constrain tree with cycle cutset {}", cutsetAssignment);
            val variableDomains = new HashMap<>(tree.getVariableDomains());
            for (Constraint constraint : overlappingConstraints) {
                val overlappingVariables = new HashSet<>(constraint.getVariables());
                overlappingVariables.retainAll(variableDomains.keySet());
                for (Variable X_i : overlappingVariables) {
                    val D_i = variableDomains.get(X_i);
                    val revisedDomain = revise(X_i, D_i, cutsetAssignment, constraint);
                    if (revisedDomain.isEmpty()) {
                        log.warn("Domain of variable {} is empty during cutset conditioning", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedDomain);
                }
            }
            val constrainedTree = tree.toBuilder().variableDomains(variableDomains).build();
            log.info("Constrained tree {}", constrainedTree);
            return Optional.of(constrainedTree);
        }

        private Domain revise(@NonNull Variable X_i, @NonNull Domain D_i, @NonNull Assignment cutsetAssignment, @NonNull Constraint constraint) {
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
                        .flatMap(cutsetAssignment -> decomposition.constrainTree(cutsetAssignment).stream()
                                .flatMap(treeSolver::getSolutions)
                                .map(cutsetAssignment::merge)))
                .orElseGet(() -> cycleCutsetSolver.getSolutions(csp));
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
        return csp.getVariableDomains().keySet().stream()
                .filter(Predicate.not(unsplittableVariables::contains))
                .sorted(Comparator.comparing(csp::countConstraints)) // start with least constrained variables
                .map(variable -> decomposeCsp(csp, unsplittableVariables, variable))
                .flatMap(Optional::stream)
                .findFirst();
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
    private Optional<Decomposition> decomposeCsp(@NonNull ConstraintSatisfactionProblem csp, @NonNull Set<Variable> unsplittableVariables, @NonNull Variable variable) {
        log.debug("Decompose from {}", variable);
        val queue = new ArrayDeque<>(List.of(variable));
        val visited = new HashSet<Variable>();
        val treeVariables = new HashSet<Variable>();
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
                        .sorted(Comparator.comparing(csp::countConstraints))
                        .filter(v -> !visited.contains(v))
                        .toList();
                queue.addAll(unvisited);
            }
        }
        val treeSize = treeVariables.size();
        val problemSize = csp.getVariableDomains().size();
        val cycleCutsetSize = problemSize - treeSize;
        if (treeSize > 1 && isComplexityDecreased(csp, cycleCutsetSize)) {
            final Predicate<Variable> treePredicate = treeVariables::contains;
            final Predicate<Variable> cycleCutsetPredicate = Predicate.not(treePredicate);
            val cycleCutset = csp.withVariableSubset(cycleCutsetPredicate);
            val tree = csp.withVariableSubset(treePredicate);
            val overlappingConstraints = new HashSet<>(csp.getConstraints());
            overlappingConstraints.removeAll(cycleCutset.getConstraints());
            overlappingConstraints.removeAll(tree.getConstraints());
            val decomposition = new Decomposition(cycleCutset, tree, overlappingConstraints);
            log.info("Found decomposition {}", decomposition);
            return Optional.of(decomposition);
        }
        return Optional.empty();
    }

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
        val cspComplexity = csp.getSearchSpace().doubleValue();
        val cutsetConditioningComplexity = Math.pow(d, c) * (n - c) * Math.pow(d, 2);
        log.info("csp -> {}, cutset -> {}", cspComplexity, cutsetConditioningComplexity);
        return cutsetConditioningComplexity < cspComplexity;
    }
}
