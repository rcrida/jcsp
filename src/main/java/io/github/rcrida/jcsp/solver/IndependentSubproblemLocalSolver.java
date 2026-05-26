package io.github.rcrida.jcsp.solver;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Decomposes a CSP into independent subproblems and solves each with a delegate
 * {@link LocalSolver}, then merges the results. When the problem has no independent
 * structure the full CSP is passed through to the delegate unchanged.
 */
@Slf4j
@Value
@Builder
public class IndependentSubproblemLocalSolver implements LocalSolver {
    @NonNull LocalSolver delegate;

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return solve(csp.decomposeSubproblems(), sub -> delegate.getLocalSolution(sub));
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                  @NonNull ToDoubleFunction<Assignment> objective) {
        return solve(csp.decomposeSubproblems(), sub -> delegate.getLocalSolution(sub, objective));
    }

    private Optional<Assignment> solve(@NonNull Set<ConstraintSatisfactionProblem> subproblems,
                                       @NonNull Function<ConstraintSatisfactionProblem, Optional<Assignment>> solveSubproblem) {
        if (subproblems.size() > 1) {
            log.info("Solving {} independent subproblems", subproblems.size());
        }
        return subproblems.stream()
                .map(solveSubproblem)
                .reduce((a1, a2) -> a1.flatMap(r1 -> a2.map(r1::merge)))
                .orElse(Optional.empty());
    }
}
