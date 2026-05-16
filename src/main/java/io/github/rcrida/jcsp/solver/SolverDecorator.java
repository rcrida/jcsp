package io.github.rcrida.jcsp.solver;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.ToDoubleFunction;
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
                .map(inner::getSolutions)
                .orElse(Stream.empty());
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull ToDoubleFunction<Assignment> objective) {
        return preprocess(csp)
                .map(preprocessed -> inner.getSolutions(preprocessed, objective))
                .orElse(Stream.empty());
    }
}
