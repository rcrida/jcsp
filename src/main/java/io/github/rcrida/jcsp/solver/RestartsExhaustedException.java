package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;

/**
 * The {@link InconclusiveSearchException} for {@link DomWdegLubySearch} using up its restart
 * allowance without either finding a solution or completing a single restart's search.
 *
 * <p>Every restart is cut off by a failure budget, so an abandoned one proves nothing: running out
 * of restarts says only that no attempt was ever given enough budget to finish. The satisfaction
 * chain sets {@code maxRestarts} to {@link Integer#MAX_VALUE} precisely so that {@link
 * io.github.rcrida.jcsp.assignments.SolverLimits} stays the only thing that bounds a search there,
 * which makes this unreachable from {@code createSolver(csp)}; it is reachable for a caller that
 * embeds that search as one bounded phase of a larger one, as {@link BranchAndBoundSolver}'s
 * first-solution search does.
 */
public class RestartsExhaustedException extends InconclusiveSearchException {
    public RestartsExhaustedException(int maxRestarts, Statistics statistics) {
        super("Solver exhausted " + maxRestarts + " restarts after " + statistics.getNodesExplored()
                + " nodes without finding a solution or completing a search", statistics);
    }
}
