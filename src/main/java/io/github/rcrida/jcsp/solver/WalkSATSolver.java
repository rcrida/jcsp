package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * WalkSAT local search for boolean constraint satisfaction and optimization problems.
 * <p>
 * At each step, picks a random unsatisfied constraint, then either flips a random variable
 * from that constraint (noise walk, probability {@link #noiseParameter}) or flips the variable
 * that minimises the total number of violated constraints (greedy). The noise walk prevents
 * getting stuck in local optima that pure greedy repair cannot escape.
 * <p>
 * Runs all {@code maxAttempts} restarts in parallel. For satisfaction, returns the first
 * solution found. For optimization, each attempt stops at its first feasible solution and
 * all attempts run to completion; the one with the minimum objective value is returned.
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
        var constraints = LocalSearchSupport.conflictConstraints(csp).toList();
        return IntStream.range(0, maxAttempts)
                .parallel()
                .unordered()
                .mapToObj(attempt -> runAttempt(csp, constraints, attempt))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                  @NonNull ToDoubleFunction<Assignment> objective) {
        var constraints = LocalSearchSupport.conflictConstraints(csp).toList();
        return IntStream.range(0, maxAttempts)
                .parallel()
                .mapToObj(attempt -> runAttempt(csp, constraints, attempt))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(objective));
    }

    private Optional<Assignment> runAttempt(@NonNull ConstraintSatisfactionProblem csp,
                                             @NonNull List<Constraint> constraints, int attempt) {
        var current = initialAssignmentFactory.getAssignment(csp);
        for (int step = 0; step < maxSteps; step++) {
            if (current.isSolution(csp)) {
                log.info("WalkSAT solution at attempt {} step {}", attempt, step);
                return Optional.of(current);
            }
            var snapshot = current;
            var unsatisfied = constraints.stream().filter(c -> !c.isSatisfiedBy(snapshot)).toList();
            if (unsatisfied.isEmpty()) break;
            var constraint = unsatisfied.get(ThreadLocalRandom.current().nextInt(unsatisfied.size()));
            var vars = constraint.getVariables().stream()
                    .filter(v -> csp.getVariableDomains().containsKey(v))
                    .filter(v -> canFlip(csp.getDomain(v)))
                    .toList();
            if (vars.isEmpty()) continue;
            var toFlip = ThreadLocalRandom.current().nextDouble() < noiseParameter
                    ? vars.get(ThreadLocalRandom.current().nextInt(vars.size()))
                    : greedyFlip(vars, current, constraints);
            current = flip(current, toFlip);
            current.getStatistics().incrementSteps();
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

    static boolean canFlip(@NonNull Domain<?> domain) {
        if (domain instanceof BooleanDomain) return true;
        if (domain instanceof DiscreteDomain<?> dd) {
            var values = dd.toList();
            return values.contains(Boolean.TRUE) && values.contains(Boolean.FALSE);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> Assignment flip(@NonNull Assignment current, @NonNull Variable<T> variable) {
        // Safe: WalkSAT is only invoked for boolean-only CSPs
        boolean value = (Boolean) current.getValue(variable).orElseThrow();
        return current.withValue(variable, (T) (Boolean) !value);
    }
}
