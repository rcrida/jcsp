package io.github.rcrida.jcsp.solver;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base for solver decorators. Holds the {@code inner} solver that receives the preprocessed
 * problem, and exposes a {@link #preprocess} hook that subclasses override to transform the CSP
 * before delegating.
 *
 * <p>Preprocessing decorators (node/arc consistency) override only {@link #preprocess} and
 * inherit {@code getSolutions} for free. Structural decomposers (independent subproblem, cutset
 * conditioning, tree decomposition) override {@link #getSolutions(ConstraintSatisfactionProblem)}
 * for their decomposition logic.
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
                .map(p -> p.isFullyDetermined() ? forcedSolution(p) : inner.getSolutions(p))
                .orElse(Stream.empty());
    }

    /**
     * Extracts the forced assignment from singleton domains and validates it against all constraints.
     * Returns a singleton stream when valid, empty otherwise.
     */
    protected static Stream<Assignment> forcedSolution(ConstraintSatisfactionProblem csp) {
        val values = csp.getVariableDomains().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().singleValue().orElseThrow()));
        Assignment a = Assignment.of(values);
        boolean valid = csp.getConstraints().stream().allMatch(c -> c.isSatisfiedBy(a));
        return valid ? Stream.of(a) : Stream.empty();
    }
}
