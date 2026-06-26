package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * Large Neighborhood Search (LNS) solver for boolean CSPs containing {@link ExactlyOneConstraint}s.
 *
 * <p>Each step selects {@code slotsPerStep} random ExactlyOne slots to relax (their variables are
 * temporarily freed). All valid refilling combinations — exactly one variable true per slot — are
 * enumerated exhaustively, and the combination with the fewest violations (ties broken by objective
 * value) is accepted. For {@code k} slots with {@code n} candidates each, at most {@code n^k}
 * combinations are evaluated per step, making this tractable for typical scheduling problems.
 *
 * <p>Runs all {@code maxAttempts} restarts in parallel. For satisfaction, returns the first feasible
 * solution found. For optimization, each attempt tracks the best feasible solution and the global
 * minimum across all attempts is returned.
 */
@Slf4j
@Value
@Builder
public class LargeNeighborhoodSolver implements LocalSolver {
    int maxAttempts;
    int maxSteps;
    @NonNull InitialAssignmentFactory initialAssignmentFactory;
    @Builder.Default int slotsPerStep = 2;

    public static LargeNeighborhoodSolver of(int maxAttempts, int maxSteps,
                                              @NonNull InitialAssignmentFactory factory) {
        return builder().maxAttempts(maxAttempts).maxSteps(maxSteps)
                        .initialAssignmentFactory(factory).build();
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
        var slots = extractSlots(csp);
        return IntStream.range(0, maxAttempts)
                .parallel()
                .unordered()
                .mapToObj(attempt -> solveAttempt(csp, slots, attempt))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                  @NonNull ToDoubleFunction<Assignment> objective) {
        var slots = extractSlots(csp);
        return IntStream.range(0, maxAttempts)
                .parallel()
                .mapToObj(attempt -> solveAttemptWithObjective(csp, slots, attempt, objective))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(objective));
    }

    @SuppressWarnings("unchecked")
    private List<List<Variable<Boolean>>> extractSlots(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getConstraints().stream()
                .filter(c -> c instanceof ExactlyOneConstraint)
                .map(c -> c.getVariables().stream()
                        .map(v -> (Variable<Boolean>) v)
                        .filter(v -> !csp.getDomain(v).isSingleton())
                        .toList())
                .filter(slot -> !slot.isEmpty())
                .toList();
    }

    private Optional<Assignment> solveAttempt(@NonNull ConstraintSatisfactionProblem csp,
                                               @NonNull List<List<Variable<Boolean>>> slots,
                                               int attempt) {
        var current = initialAssignmentFactory.getAssignment(csp);
        for (int step = 0; step < maxSteps; step++) {
            current = bestNeighbor(csp, current, pickSlots(slots, Math.min(slotsPerStep, slots.size())), null);
            current.getStatistics().incrementSteps();
            if (current.isSolution(csp)) {
                log.info("LNS solution at attempt {} step {}", attempt, step);
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    private Optional<Assignment> solveAttemptWithObjective(@NonNull ConstraintSatisfactionProblem csp,
                                                            @NonNull List<List<Variable<Boolean>>> slots,
                                                            int attempt,
                                                            @NonNull ToDoubleFunction<Assignment> objective) {
        var current = initialAssignmentFactory.getAssignment(csp);
        Assignment bestFeasible = current.isSolution(csp) ? current : null;
        for (int step = 0; step < maxSteps; step++) {
            current = bestNeighbor(csp, current, pickSlots(slots, Math.min(slotsPerStep, slots.size())), objective);
            current.getStatistics().incrementSteps();
            if (current.isSolution(csp)) {
                if (bestFeasible == null || objective.applyAsDouble(current) < objective.applyAsDouble(bestFeasible)) {
                    bestFeasible = current;
                    log.info("Better solution at attempt {} step {}: cost={}", attempt, step,
                            objective.applyAsDouble(bestFeasible));
                }
            }
        }
        return Optional.ofNullable(bestFeasible);
    }

    private List<List<Variable<Boolean>>> pickSlots(@NonNull List<List<Variable<Boolean>>> slots, int k) {
        var shuffled = new ArrayList<>(slots);
        Collections.shuffle(shuffled, new Random(ThreadLocalRandom.current().nextLong()));
        return shuffled.subList(0, k);
    }

    // Only check constraints involving at least one relaxed variable — the contribution of frozen
    // variables is constant across all combinations and cancels out in the comparison.
    private Assignment bestNeighbor(@NonNull ConstraintSatisfactionProblem csp,
                                     @NonNull Assignment current,
                                     @NonNull List<List<Variable<Boolean>>> relaxedSlots,
                                     @Nullable ToDoubleFunction<Assignment> objective) {
        Set<Variable<Boolean>> relaxedVars = new HashSet<>();
        for (var slot : relaxedSlots) relaxedVars.addAll(slot);

        var relaxedConstraints = csp.getConstraints().stream()
                .filter(c -> !Collections.disjoint(c.getVariables(), relaxedVars))
                .toList();

        var baseBuilder = current.toBuilder();
        for (var v : relaxedVars) baseBuilder.value(v, false);
        var base = baseBuilder.build();

        return enumerate(relaxedSlots).stream()
                .map(combo -> applyCombo(base, combo))
                .min(Comparator.<Assignment>comparingLong(a -> violationCount(a, relaxedConstraints))
                               .thenComparingDouble(a -> objective != null ? objective.applyAsDouble(a) : 0.0))
                .orElse(current);
    }

    // Cartesian product of per-slot assignments: each map assigns exactly one variable per slot
    // to true and the rest to false. Total size is Π(slot.size()) across all slots.
    private List<Map<Variable<Boolean>, Boolean>> enumerate(@NonNull List<List<Variable<Boolean>>> slots) {
        List<Map<Variable<Boolean>, Boolean>> result = new ArrayList<>();
        result.add(new HashMap<>());
        for (var slot : slots) {
            var next = new ArrayList<Map<Variable<Boolean>, Boolean>>();
            for (var prefix : result) {
                for (var chosen : slot) {
                    var combo = new HashMap<>(prefix);
                    for (var v : slot) combo.put(v, v.equals(chosen));
                    next.add(combo);
                }
            }
            result = next;
        }
        return result;
    }

    private Assignment applyCombo(@NonNull Assignment base,
                                   @NonNull Map<Variable<Boolean>, Boolean> combo) {
        var builder = base.toBuilder();
        combo.forEach((v, val) -> builder.value(v, val));
        return builder.build();
    }

    private long violationCount(@NonNull Assignment a, @NonNull List<Constraint> constraints) {
        return constraints.stream().filter(c -> !c.isSatisfiedBy(a)).count();
    }
}
