package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;
import lombok.Getter;

/**
 * Thrown by {@link BoundSolver#getSolution()} when a
 * {@link io.github.rcrida.jcsp.assignments.SolverLimits} node or time limit is exceeded
 * before a solution (or UNSAT proof) is found.
 *
 * <p>Callers can distinguish a genuine UNSAT result ({@link java.util.Optional#empty()})
 * from a limit-hit (this exception) and inspect how much work was done via
 * {@link #getStatistics()}.
 *
 * <p>Only thrown from {@link BoundSolver#getSolution()}, not from
 * {@link BoundSolver#getSolutions()}, which truncates the stream silently instead.
 */
@Getter
public class LimitExceededException extends RuntimeException {
    private final Statistics statistics;

    public LimitExceededException(Statistics statistics) {
        super("Solver limit exceeded after " + statistics.getNodesExplored().get() + " nodes");
        this.statistics = statistics;
    }
}
