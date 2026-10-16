package io.github.rcrida.jcsp.consistency.arc;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.ObjectSingletonDomain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

/**
 * Represents the Maintaining Arc Consistency (MAC) inference algorithm which is used in constraint satisfaction problems (CSPs).
 * This class ensures arc consistency for a given assignment of a variable by enforcing constraints on binary relations.
 */
public class MAC implements Inference {
    public static final Inference INSTANCE = new MAC();

    private MAC() {}

    /**
     * Applies the Maintaining Arc Consistency (MAC) inference algorithm to enforce arc consistency
     * for a constraint satisfaction problem (CSP) after assigning a value to a variable.
     *
     * @param problem The constraint satisfaction problem to which inference will be applied.
     * @param variable The variable that has been assigned a new value in the CSP.
     * @param assignment The current assignment of values to variables in the CSP.
     * @return An {@link Optional} containing the updated constraint satisfaction problem if inference
     *         is successful and maintains consistency; otherwise, an empty {@link Optional}.
     */
    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment) {
        return AC3.INSTANCE.applyQueue(
                problem.withDomain(variable, assignedDomain(variable, assignment)),
                seedQueue(problem, variable, assignment));
    }

    /**
     * The singleton domain {@link #apply} narrows {@code variable} to: the value {@code assignment}
     * gave it.
     */
    public static Domain<?> assignedDomain(Variable<?> variable, Assignment assignment) {
        return new ObjectSingletonDomain<>(assignment.getValue(variable).orElseThrow());
    }

    /**
     * Whether {@link #apply} has no arc to revise after {@code variable} was assigned, so that it
     * does nothing beyond narrowing {@code variable} to {@link #assignedDomain}. A caller that can
     * apply that narrowing itself then needn't build a problem for it.
     */
    public static boolean revisesNothing(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                          Assignment assignment) {
        for (Arc arc : AC3.INSTANCE.arcsInto(problem, variable)) {
            if (!assignment.isAssigned(arc.getFrom())) return false;
        }
        return true;
    }

    /**
     * Single-pass combination of {@link #apply} and re-deriving a reason for a wipeout: delegates
     * to {@link AC3#applyQueueWithReason}, which computes a reason inline at the exact arc a
     * wipeout is found rather than a second, from-scratch traversal — identical arc-revision cost
     * to {@link #apply} on the feasible path.
     */
    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment) {
        return AC3.INSTANCE.applyQueueWithReason(
                problem.withDomain(variable, assignedDomain(variable, assignment)),
                seedQueue(problem, variable, assignment));
    }

    /**
     * The arcs {@code (X_j, variable)} worth revising after {@code variable} was just assigned: every
     * arc pointing <em>at</em> it, minus those whose own {@code from} side is already assigned (a
     * singleton domain that revision could only confirm or wipe out, and a wipeout there is already
     * caught by the direct consistency check that precedes inference).
     * <p>
     * Reads {@link AC3#arcsInto} rather than filtering {@link
     * ConstraintSatisfactionProblem#getAllBinaryArcs()}, which is what both callers did until
     * 2026-09-18: that scanned every arc in the whole problem and collected a fresh {@link
     * java.util.HashSet} on every search node -- {@code O(|arcs|)} for a result the memoized
     * by-target index already holds, which on a large instance means tens of thousands of arc
     * comparisons per node to find the handful incident on one variable.
     */
    private static Queue<Arc> seedQueue(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                         Assignment assignment) {
        val queue = new ArrayDeque<Arc>();
        for (Arc arc : AC3.INSTANCE.arcsInto(problem, variable)) {
            if (!assignment.isAssigned(arc.getFrom())) queue.add(arc);
        }
        return queue;
    }
}
