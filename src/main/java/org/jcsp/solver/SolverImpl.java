package org.jcsp.solver;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.node.NodeConsistency;
import org.jcsp.search.BacktrackingSearch;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * An implementation of the {@link Solver} interface that provides methods for solving
 * constraint satisfaction problems (CSPs) using a combination of node consistency,
 * arc consistency (AC3), and backtracking search.
 * <p>
 * This class leverages the following techniques:
 * <p>
 * - **Node Consistency**: Ensures that all variables' values are consistent with their
 *   unary constraints.
 * - **Arc Consistency (AC3)**: Enforces binary constraints between variables by iterating
 *   over arcs and progressively reducing the search space.
 * - **Backtracking Search**: Explores possible assignments of variables recursively,
 *   applying inference and heuristics to quickly find solutions while reducing the search space.
 */
@Slf4j
@Value
public class SolverImpl implements Solver {
    @NonNull
    BacktrackingSearch backtrackingSearch;

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return NodeConsistency.INSTANCE.apply(csp)
                .flatMap(nodeConsistent -> {
                    log.info("Applying AC3 to node consistent problem {}", nodeConsistent);
                    return AC3.INSTANCE.apply(nodeConsistent);
                })
                .stream()
                .flatMap(arcConsistent -> {
                    log.info("Searching arc-consistent problem {}", arcConsistent);
                    return backtrackingSearch.searchStream(arcConsistent);
                });
    }
}
