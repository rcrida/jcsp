package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;
import lombok.Getter;

/**
 * Thrown by {@link BoundSolver#getSolution()} when a caller-supplied {@link Cancellation} token is
 * cancelled before a solution (or UNSAT proof) is found -- distinguishable from a genuine UNSAT
 * result ({@link java.util.Optional#empty()}) and from {@link LimitExceededException} (a
 * pre-configured budget, not an external signal).
 *
 * <p>Scoped exactly like {@link LimitExceededException}: only thrown from
 * {@link BoundSolver#getSolution()} in the satisfaction chain, from the two decorators with a
 * genuine search algorithm of their own rather than a plain {@code getSolutions().findFirst()} --
 * {@link DomWdegLubySearch}'s Luby-restart search, and {@link
 * io.github.rcrida.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver}'s cutset-assignment
 * enumeration. {@link BoundSolver#getSolutions()} truncates the stream silently instead, and the
 * optimization chain ({@link BranchAndBoundSolver}) returns the best incumbent found so far rather
 * than throwing.
 */
@Getter
public class SolverCancelledException extends RuntimeException {
    private final Statistics statistics;

    public SolverCancelledException(Statistics statistics) {
        super("Solver cancelled after " + statistics.getNodesExplored().get() + " nodes");
        this.statistics = statistics;
    }
}
