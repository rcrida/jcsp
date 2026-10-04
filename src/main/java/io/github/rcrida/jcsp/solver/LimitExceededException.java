package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;

/**
 * The {@link InconclusiveSearchException} for a
 * {@link io.github.rcrida.jcsp.assignments.SolverLimits} node or time limit being exceeded before a
 * solution (or an UNSAT proof) was found.
 *
 * <p>Only thrown from {@link BoundSolver#getSolution()}, not from
 * {@link BoundSolver#getSolutions()}, which truncates the stream silently instead.
 */
public class LimitExceededException extends InconclusiveSearchException {
    public LimitExceededException(Statistics statistics) {
        super("Solver limit exceeded after " + statistics.getNodesExplored() + " nodes", statistics);
    }
}
