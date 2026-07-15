package io.github.rcrida.jcsp.solver;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.ConflictedVariableSelector;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * Tabu search local search for CSPs: the same min-conflicts move selection as
 * {@link MinConflictsSolver}, plus a short-term memory that forbids reverting a variable to the
 * value it just held for {@code tabuTenure} steps — unless doing so would strictly improve on the
 * best total conflict weight (summed over every constraint in the CSP, not just the moved
 * variable's own) seen so far this attempt (the aspiration criterion). The memory tracks a single
 * forbidden value per variable (the value it was moved away from), which is enough to break the
 * two-step cycles plain min-conflicts can get stuck repeating.
 * <p>
 * The total weight is maintained incrementally rather than rescanned from the whole CSP on every
 * candidate: only constraints touching the moved variable can change status when it's the only
 * variable that changed, so each candidate's total weight is derived as a delta off the variable's
 * own (already-computed) conflict weight. This keeps the per-step cost the same order as
 * {@link MinConflictsSolver}'s instead of scaling with the CSP's total constraint count.
 * <p>
 * Runs all {@code maxAttempts} restarts in parallel, each with its own tabu memory. For
 * satisfaction, returns the first solution found. For optimization, every attempt runs to
 * completion (or exhausts {@code maxSteps}) and the lowest-cost feasible assignment is returned.
 */
@Slf4j
@Value
@Builder(toBuilder = true)
public class TabuSearchSolver implements LocalSolver, CancellableLocalSolver {
    int maxAttempts;
    int maxSteps;
    @NonNull InitialAssignmentFactory initialAssignmentFactory;
    @Builder.Default UnassignedVariableSelector conflictedVariableSelector = ConflictedVariableSelector.INSTANCE;
    /** Number of steps a reverted value stays forbidden for the variable it was moved away from. */
    @Builder.Default int tabuTenure = 10;

    /**
     * Cooperative cancellation, only ever set by {@link RaceLocalSolver} via {@link #withCancellation}.
     * Excluded from equals/hashCode/toString since it's ephemeral race state, not configuration —
     * same reasoning as {@link io.github.rcrida.jcsp.assignments.SolverLimits}'s limitHitStats field.
     */
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    Cancellation cancellation = Cancellation.NEVER;

    public static TabuSearchSolver of(int maxAttempts, int maxSteps, @NonNull InitialAssignmentFactory factory) {
        return builder().maxAttempts(maxAttempts).maxSteps(maxSteps).initialAssignmentFactory(factory).build();
    }

    @Override
    public TabuSearchSolver withCancellation(@NonNull Cancellation cancellation) {
        return toBuilder().cancellation(cancellation).build();
    }

    record TabuEntry(Object forbiddenValue, int expiresAtStep) {}

    private record MoveResult(Assignment assignment, double totalWeight) {}

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return IntStream.range(0, maxAttempts)
                .parallel()
                .unordered()
                .mapToObj(attempt -> solveAttempt(csp, attempt))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    private Optional<Assignment> solveAttempt(@NonNull ConstraintSatisfactionProblem csp, int attempt) {
        var current = initialAssignmentFactory.getAssignment(csp);
        val constraintWeights = new HashMap<Constraint, Double>();
        val tabu = new HashMap<Variable<?>, TabuEntry>();
        var currentTotalWeight = LocalSearchSupport.totalWeight(csp, current, constraintWeights);
        var bestTotalWeight = Double.MAX_VALUE;
        for (int step = 0; step < maxSteps && !cancellation.isCancelled(); step++) {
            if (current.isSolution(csp)) {
                log.info("Tabu search solution at attempt {} step {}", attempt, step);
                return Optional.of(current);
            }
            val variable = conflictedVariableSelector.select(csp, current);
            val result = applyTabuMove(csp, variable, current, constraintWeights, tabu, step, currentTotalWeight, bestTotalWeight);
            current = result.assignment();
            currentTotalWeight = result.totalWeight();
            bestTotalWeight = Math.min(bestTotalWeight, currentTotalWeight);
            current.getStatistics().incrementSteps();
            currentTotalWeight += LocalSearchSupport.incrementViolatedWeights(csp, constraintWeights, current);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                  @NonNull ToDoubleFunction<Assignment> objective) {
        return IntStream.range(0, maxAttempts)
                .parallel()
                .mapToObj(attempt -> solveAttemptWithObjective(csp, attempt, objective))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(objective));
    }

    private Optional<Assignment> solveAttemptWithObjective(@NonNull ConstraintSatisfactionProblem csp, int attempt,
                                                            @NonNull ToDoubleFunction<Assignment> objective) {
        var current = initialAssignmentFactory.getAssignment(csp);
        val constraintWeights = new HashMap<Constraint, Double>();
        val tabu = new HashMap<Variable<?>, TabuEntry>();
        var currentTotalWeight = LocalSearchSupport.totalWeight(csp, current, constraintWeights);
        var bestTotalWeight = Double.MAX_VALUE;
        for (int step = 0; step < maxSteps && !cancellation.isCancelled(); step++) {
            if (current.isSolution(csp)) {
                double cost = objective.applyAsDouble(current);
                log.info("Tabu search found solution at attempt {} step {} with cost {}", attempt, step, cost);
                return Optional.of(current);
            }
            val variable = conflictedVariableSelector.select(csp, current);
            val result = applyTabuMoveWithObjective(csp, variable, current, constraintWeights, tabu, step, currentTotalWeight, bestTotalWeight, objective);
            current = result.assignment();
            currentTotalWeight = result.totalWeight();
            bestTotalWeight = Math.min(bestTotalWeight, currentTotalWeight);
            current.getStatistics().incrementSteps();
            currentTotalWeight += LocalSearchSupport.incrementViolatedWeights(csp, constraintWeights, current);
        }
        return Optional.empty();
    }

    private @NonNull MoveResult applyTabuMove(@NonNull ConstraintSatisfactionProblem csp,
                                               @NonNull Variable<?> variable,
                                               @NonNull Assignment current,
                                               @NonNull Map<Constraint, Double> constraintWeights,
                                               @NonNull Map<Variable<?>, TabuEntry> tabu,
                                               int step, double currentTotalWeight, double bestTotalWeight) {
        return applyTabuMoveTyped(csp, variable, current, constraintWeights, tabu, step, currentTotalWeight, bestTotalWeight);
    }

    private <T> @NonNull MoveResult applyTabuMoveTyped(@NonNull ConstraintSatisfactionProblem csp,
                                                        @NonNull Variable<T> variable,
                                                        @NonNull Assignment current,
                                                        @NonNull Map<Constraint, Double> constraintWeights,
                                                        @NonNull Map<Variable<?>, TabuEntry> tabu,
                                                        int step, double currentTotalWeight, double bestTotalWeight) {
        record ValueWeight<V>(V value, double localWeight, double totalWeight) {}

        val variableConstraints = LocalSearchSupport.conflictConstraints(csp)
                .filter(c -> c.getVariables().contains(variable))
                .toList();
        T oldValue = current.getValue(variable).orElseThrow();
        double oldLocalWeight = LocalSearchSupport.weighConflicts(variable, oldValue, current, variableConstraints, constraintWeights);
        val costs = ((DiscreteDomain<T>) csp.getDomain(variable)).stream()
                .map(v -> {
                    double localWeight = LocalSearchSupport.weighConflicts(variable, v, current, variableConstraints, constraintWeights);
                    return new ValueWeight<>(v, localWeight, currentTotalWeight - oldLocalWeight + localWeight);
                })
                .toList();

        val tabuEntry = tabu.get(variable);
        val admissible = costs.stream()
                .filter(c -> isAdmissible(tabuEntry, c.value(), step, c.totalWeight(), bestTotalWeight))
                .toList();

        double minLocalWeight = admissible.stream().mapToDouble(ValueWeight::localWeight).min().orElseThrow();
        val candidates = admissible.stream()
                .filter(c -> c.localWeight() == minLocalWeight)
                .toList();
        val chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        recordMove(tabu, variable, oldValue, chosen.value(), step);
        log.debug("{} -> {}", variable, chosen.value());
        return new MoveResult(current.toBuilder().value(variable, chosen.value()).build(), chosen.totalWeight());
    }

    private @NonNull MoveResult applyTabuMoveWithObjective(@NonNull ConstraintSatisfactionProblem csp,
                                                            @NonNull Variable<?> variable,
                                                            @NonNull Assignment current,
                                                            @NonNull Map<Constraint, Double> constraintWeights,
                                                            @NonNull Map<Variable<?>, TabuEntry> tabu,
                                                            int step, double currentTotalWeight, double bestTotalWeight,
                                                            @NonNull ToDoubleFunction<Assignment> objective) {
        return applyTabuMoveWithObjectiveTyped(csp, variable, current, constraintWeights, tabu, step, currentTotalWeight, bestTotalWeight, objective);
    }

    private <T> @NonNull MoveResult applyTabuMoveWithObjectiveTyped(@NonNull ConstraintSatisfactionProblem csp,
                                                                     @NonNull Variable<T> variable,
                                                                     @NonNull Assignment current,
                                                                     @NonNull Map<Constraint, Double> constraintWeights,
                                                                     @NonNull Map<Variable<?>, TabuEntry> tabu,
                                                                     int step, double currentTotalWeight, double bestTotalWeight,
                                                                     @NonNull ToDoubleFunction<Assignment> objective) {
        record ValueCost<V>(V value, double violations, double totalWeight, double objective) {}

        val variableConstraints = LocalSearchSupport.conflictConstraints(csp)
                .filter(c -> c.getVariables().contains(variable))
                .toList();
        T oldValue = current.getValue(variable).orElseThrow();
        double oldLocalWeight = LocalSearchSupport.weighConflicts(variable, oldValue, current, variableConstraints, constraintWeights);
        val costs = ((DiscreteDomain<T>) csp.getDomain(variable)).stream()
                .map(v -> {
                    val candidateAssignment = current.withValue(variable, v);
                    double localWeight = LocalSearchSupport.weighConflicts(variable, v, current, variableConstraints, constraintWeights);
                    return new ValueCost<>(v, localWeight, currentTotalWeight - oldLocalWeight + localWeight,
                            objective.applyAsDouble(candidateAssignment));
                })
                .toList();

        val tabuEntry = tabu.get(variable);
        val admissible = costs.stream()
                .filter(c -> isAdmissible(tabuEntry, c.value(), step, c.totalWeight(), bestTotalWeight))
                .toList();

        // Lexicographic: minimise violations first (constraint repair always takes priority),
        // then use the objective as a tie-breaker — no scaling constant needed.
        double minViolations = admissible.stream().mapToDouble(ValueCost::violations).min().orElseThrow();
        double minObjective = admissible.stream().filter(c -> c.violations() == minViolations)
                .mapToDouble(ValueCost::objective).min().orElseThrow();
        val candidates = admissible.stream()
                .filter(c -> c.violations() == minViolations && c.objective() == minObjective)
                .toList();
        val chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        recordMove(tabu, variable, oldValue, chosen.value(), step);
        log.debug("{} -> {}", variable, chosen.value());
        return new MoveResult(current.toBuilder().value(variable, chosen.value()).build(), chosen.totalWeight());
    }

    /**
     * A candidate value is admissible unless it's tabu, in which case it's still admissible if
     * choosing it would strictly improve on the best total conflict weight seen so far this
     * attempt (the aspiration criterion). Package-visible so it can be unit-tested directly with
     * exact inputs — the combination of a tabu'd value whose total weight beats the historical
     * best is otherwise too rare to reliably trigger through search dynamics alone.
     */
    static <T> boolean isAdmissible(@Nullable TabuEntry entry, T candidateValue, int step,
                                     double candidateTotalWeight, double bestTotalWeight) {
        return !isTabu(entry, candidateValue, step) || candidateTotalWeight < bestTotalWeight;
    }

    private static <T> boolean isTabu(@Nullable TabuEntry entry, T candidateValue, int step) {
        return entry != null && step < entry.expiresAtStep() && Objects.equals(entry.forbiddenValue(), candidateValue);
    }

    private <T> void recordMove(@NonNull Map<Variable<?>, TabuEntry> tabu, @NonNull Variable<T> variable,
                                 T oldValue, T newValue, int step) {
        if (!Objects.equals(oldValue, newValue)) {
            tabu.put(variable, new TabuEntry(oldValue, step + tabuTenure));
        }
    }
}
