package io.github.rcrida.jcsp.solver;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base for solver decorators. Holds the {@code inner} solver that receives the preprocessed
 * problem, and exposes a {@link #preprocess} hook that subclasses override to transform the CSP
 * before delegating.
 *
 * <p>Both {@code getSolutions} overloads run through the same {@code preprocess → inner} pipeline:
 * <ul>
 *   <li>Preprocessing decorators (node/arc consistency) override only {@link #preprocess} and
 *       inherit both {@code getSolutions} methods for free.</li>
 *   <li>Structural decomposers (independent subproblem, cutset conditioning, tree decomposition)
 *       override {@link #getSolutions(ConstraintSatisfactionProblem)} for their decomposition logic
 *       while inheriting the objective overload, which bypasses decomposition and routes straight to
 *       {@code inner} — typically a {@link BranchAndBoundSolver}.</li>
 * </ul>
 */
@Value
@NonFinal
@SuperBuilder
public abstract class SolverDecorator implements Solver {
    @NonNull Solver inner;

    /**
     * Optionally transforms the problem before solving. Returns empty to signal infeasibility.
     * Default is a passthrough — override to apply a consistency algorithm.
     */
    @NonNull
    protected Optional<ConstraintSatisfactionProblem> preprocess(@NonNull ConstraintSatisfactionProblem csp) {
        return Optional.of(csp);
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return preprocess(csp)
                .map(p -> allSingleton(p) ? forcedSolution(p) : inner.getSolutions(p))
                .orElse(Stream.empty());
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull ToDoubleFunction<Assignment> objective) {
        return preprocess(csp)
                .map(p -> allSingleton(p) ? forcedSolution(p) : inner.getSolutions(p, objective))
                .orElse(Stream.empty());
    }

    /** Returns true when every domain has exactly one value — the problem is fully determined. */
    private static boolean allSingleton(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream().allMatch(d -> d.size() == 1);
    }

    /**
     * Extracts the forced assignment from singleton domains and validates it against all constraints.
     * Returns a singleton stream when valid, empty otherwise.
     */
    private static Stream<Assignment> forcedSolution(ConstraintSatisfactionProblem csp) {
        val values = csp.getVariableDomains().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().findFirst().orElseThrow()));
        Assignment a = Assignment.of(values);
        boolean valid = csp.getConstraints().stream().allMatch(c -> c.isSatisfiedBy(a));
        return valid ? Stream.of(a) : Stream.empty();
    }
}
