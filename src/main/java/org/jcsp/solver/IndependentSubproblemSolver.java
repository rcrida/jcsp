package org.jcsp.solver;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Solves a constraint satisfaction problem by decomposing it into independent sub-problems and finding
 * the cartesian product of their solutions. Each sub-problem's solutions are lazily computed and cached
 * in a {@link LazyList}, so they are computed at most once regardless of how many combined solutions are
 * requested. This is efficient for both finding the first solution (only the first element of each
 * sub-problem is computed) and finding all solutions (inner sub-problem solutions are replayed from cache).
 */
@Slf4j
@Value
public class IndependentSubproblemSolver implements Solver {
    @NonNull
    Solver subproblemSolver;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        val subproblems = csp.decomposeSubproblems();
        if (subproblems.size() > 1) {
            log.info("Solving subproblems {}", subproblems);
            return subproblems.stream()
                    // solve the bigger problems first so that the smaller problems are the ones cached and replayed
                    .sorted(Comparator.comparing(ConstraintSatisfactionProblem::getSearchSpace).reversed())
                    .map(s -> new LazyList<>(subproblemSolver.getSolutions(s)))
                    .reduce((ll1, ll2) -> new LazyList<>(ll1.stream().flatMap(a1 -> ll2.stream().map(a1::merge))))
                    .map(LazyList::stream)
                    .orElse(Stream.empty());
        } else {
            return subproblemSolver.getSolutions(csp);
        }
    }
}
