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
 * inherit both {@code getSolutions} and {@link #getSolution} for free. Structural decomposers
 * (independent subproblem, cutset conditioning, tree decomposition) override
 * {@link #getSolutions(ConstraintSatisfactionProblem)} <em>and</em>
 * {@link #getSolution(ConstraintSatisfactionProblem)} for their decomposition logic — the two
 * are not simply "first element of the stream" for decomposers with genuinely different
 * single-solution strategies (e.g. {@code IndependentSubproblemSolver} solving subproblems in
 * parallel, {@code CutsetConditioningSolver} short-circuiting on the first cutset assignment that
 * yields a tree solution), so this base class's default cannot be relied on for them.
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
                .map(p -> p.isFullyDetermined() ? forcedSolution(p).stream() : inner.getSolutions(p))
                .orElse(Stream.empty());
    }

    /**
     * Mirrors {@link #getSolutions}, delegating to {@code inner.getSolution} rather than
     * {@code inner.getSolutions().findFirst()} so that a terminal solver's own single-solution
     * strategy (e.g. {@link DomWdegLubySearch}'s Luby-restart search) is actually reached, instead
     * of being masked by every ancestor decorator falling back to the {@link Solver} interface's
     * generic default.
     */
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return preprocess(csp)
                .flatMap(p -> p.isFullyDetermined() ? forcedSolution(p) : inner.getSolution(p));
    }

    /**
     * Extracts the forced assignment from singleton domains and validates it against all constraints.
     * A 0-or-1 result is exactly what {@link Optional} means; {@link Optional#stream()} is the
     * standard bridge for the one caller ({@link #getSolutions}) that needs a {@link Stream}.
     */
    protected static Optional<Assignment> forcedSolution(ConstraintSatisfactionProblem csp) {
        val values = csp.getVariableDomains().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().singleValue().orElseThrow()));
        Assignment a = Assignment.of(values);
        boolean valid = csp.getConstraints().stream().allMatch(c -> c.isSatisfiedBy(a));
        return valid ? Optional.of(a) : Optional.empty();
    }
}
