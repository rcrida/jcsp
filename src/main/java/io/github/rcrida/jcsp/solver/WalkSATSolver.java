package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * WalkSAT local search for boolean constraint satisfaction problems.
 * <p>
 * At each step, picks a random unsatisfied constraint, then either flips a random variable
 * from that constraint (noise walk, probability {@link #noiseParameter}) or flips the variable
 * that minimises the total number of violated constraints (greedy). The noise walk prevents
 * getting stuck in local optima that pure greedy repair cannot escape.
 * <p>
 * Runs all {@code maxAttempts} restarts in parallel; returns the first solution found.
 * Only suitable for satisfaction (no objective). Use {@link MinConflictsSolver} for optimization.
 */
@Slf4j
@Value
@Builder
public class WalkSATSolver implements LocalSolver {
    int maxAttempts;
    int maxSteps;
    @NonNull InitialAssignmentFactory initialAssignmentFactory;
    /** Probability of taking a random-walk flip rather than the greedy flip. Typical: 0.3–0.5. */
    @Builder.Default double noiseParameter = 0.4;

    public static WalkSATSolver of(int maxAttempts, int maxSteps, @NonNull InitialAssignmentFactory factory) {
        return builder().maxAttempts(maxAttempts).maxSteps(maxSteps).initialAssignmentFactory(factory).build();
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
        var constraints = candidates(csp).toList();
        return IntStream.range(0, maxAttempts)
                .parallel()
                .unordered()
                .mapToObj(attempt -> solveAttempt(csp, constraints, attempt))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    private Optional<Assignment> solveAttempt(@NonNull ConstraintSatisfactionProblem csp,
                                               @NonNull List<Constraint> constraints, int attempt) {
        var current = initialAssignmentFactory.getAssignment(csp);
        for (int step = 0; step < maxSteps; step++) {
            if (current.isSolution(csp)) {
                log.info("WalkSAT solution at attempt {} step {}", attempt, step);
                return Optional.of(current);
            }
            var unsatisfied = constraints.stream().filter(c -> !c.isSatisfiedBy(current)).toList();
            if (unsatisfied.isEmpty()) break;
            var constraint = unsatisfied.get(ThreadLocalRandom.current().nextInt(unsatisfied.size()));
            var vars = constraint.getVariables().stream()
                    .filter(v -> csp.getVariableDomains().containsKey(v))
                    .toList();
            if (vars.isEmpty()) continue;
            var toFlip = ThreadLocalRandom.current().nextDouble() < noiseParameter
                    ? vars.get(ThreadLocalRandom.current().nextInt(vars.size()))
                    : greedyFlip(vars, current, constraints);
            current = flip(current, toFlip);
        }
        return Optional.empty();
    }

    private static Variable<?> greedyFlip(@NonNull List<Variable<?>> vars,
                                            @NonNull Assignment current,
                                            @NonNull List<Constraint> constraints) {
        var best = vars.get(0);
        long bestViolations = Long.MAX_VALUE;
        for (var v : vars) {
            long violations = constraints.stream().filter(c -> !c.isSatisfiedBy(flip(current, v))).count();
            if (violations < bestViolations) {
                bestViolations = violations;
                best = v;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private static <T> Assignment flip(@NonNull Assignment current, @NonNull Variable<T> variable) {
        // Safe: WalkSAT is only invoked for boolean-only CSPs
        boolean value = (Boolean) current.getValue(variable).orElseThrow();
        return current.withValue(variable, (T) (Boolean) !value);
    }

    // Same constraint set as MinConflictsSolver: binary decompositions for granularity where
    // available, plus non-decomposable n-ary constraints so they are never silently dropped.
    private static Stream<Constraint> candidates(@NonNull ConstraintSatisfactionProblem csp) {
        var binaryAndUnary = Stream.concat(
                csp.getAllBinaryConstraints().stream(),
                csp.getConstraints().stream().filter(c -> c instanceof UnaryConstraint));
        var nonDecomposableNary = csp.getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint
                        && (!(c instanceof BinaryDecomposable bd) || bd.getAsBinaryConstraints().isEmpty()));
        return Stream.concat(binaryAndUnary, nonDecomposableNary);
    }
}
