package org.jcsp.solver;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Solves a constraint satisfaction problem by decomposing it into independent sub-problems and finding
 * the cartesian product of the solutions of each of the sub-problems. This solution lazily evaluates
 * sub-problem solutions but can do repeatedly. This approach should be efficient if we only want a
 * small number of solutions, but if there were many sub-problems and we wanted all of the solutions
 * we could end up recalculating a lot sub-problem solutions.
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
                    .map(s -> (Supplier<Stream<Assignment>>) () -> subproblemSolver.getSolutions(s))
                    .reduce((s1, s2) ->
                            () -> s1.get().flatMap(a1 -> s2.get().map(a1::merge)))
                    .orElse(Stream::empty).get();
        } else {
            return subproblemSolver.getSolutions(csp);
        }
    }
}
