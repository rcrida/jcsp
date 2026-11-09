package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.backtrackingsearch.ForcedAssignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The starting point of one neighbourhood run of {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver}:
 * the problem that remains once every decision but the freed ones is fixed at its incumbent value.
 * <p>
 * A decision is fixed by assigning it and propagating, under the objective's cut. One that cannot be, because
 * the value was pruned by the fixings before it, conflicts with them, or propagation fails, is left free
 * instead of abandoning the neighbourhood: a task pinned at the end of a schedule, say, cannot stay where it
 * is and still beat the incumbent's makespan, so it is the cut that names what must be free.
 * <p>
 * This is the solver's mechanism, not an extension point: public only because the solver is in another package.
 */
public final class NeighbourhoodRoot {

    /** The problem and the assignment of the fixed decisions, after propagating them. */
    public record Fixed(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {}

    private final Inference inference;
    private final Assignment root;

    /**
     * @param inference the solver's propagation
     * @param root      an assignment with nothing assigned, carrying the solve's statistics and listener
     */
    public NeighbourhoodRoot(@NonNull Inference inference, @NonNull Assignment root) {
        this.inference = inference;
        this.root = root;
    }

    /**
     * Fixes every decision not in {@code free} at its value in {@code solution}.
     *
     * @param csp          the problem to fix them in, with the objective's cut already applied
     * @param cutVariables the cut's scope when it has not been propagated yet, else empty, as for
     *                     {@link Inference#apply(ConstraintSatisfactionProblem, Variable, Assignment, Set)}
     * @param decisions    the decisions, in the order to fix them
     * @param free         the decisions to leave free
     * @param solution     the incumbent
     * @return the problem and assignment that remain, or empty when the fixed part is itself inconsistent
     */
    public Optional<Fixed> fix(@NonNull ConstraintSatisfactionProblem csp, @NonNull Set<Variable<?>> cutVariables,
                               @NonNull List<Variable<?>> decisions, @NonNull Set<Variable<?>> free,
                               @NonNull Assignment solution) {
        ConstraintSatisfactionProblem current = csp;
        Assignment assignment = root;
        Set<Variable<?>> changed = cutVariables;
        for (Variable<?> decision : decisions) {
            if (free.contains(decision)) continue;
            Optional<Assignment> forced = ForcedAssignment.extend(current, assignment);
            if (forced.isEmpty()) return Optional.empty();
            assignment = forced.get();
            if (assignment.getValue(decision).isPresent()) continue;
            Assignment next = assignment.withValue(decision, solution.getValue(decision).orElseThrow());
            Optional<ConstraintSatisfactionProblem> propagated = propagate(current, decision, next, changed);
            if (propagated.isEmpty()) continue;
            changed = Set.of();
            current = propagated.get();
            assignment = next;
        }
        return Optional.of(new Fixed(current, assignment));
    }

    /** {@code next}'s fixing of {@code decision} propagated in {@code current}, or empty if it cannot be kept. */
    private Optional<ConstraintSatisfactionProblem> propagate(ConstraintSatisfactionProblem current, Variable<?> decision,
                                                              Assignment next, Set<Variable<?>> changed) {
        if (!current.getDomain(decision).contains(next.getValue(decision).orElseThrow())) return Optional.empty();
        if (!next.isConsistentAmong(current.getConstraintsTouching(decision))) return Optional.empty();
        return inference.apply(current, decision, next, changed);
    }
}
