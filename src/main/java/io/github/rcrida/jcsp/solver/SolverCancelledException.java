package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;

/**
 * The {@link InconclusiveSearchException} for a caller-supplied {@link Cancellation} token being
 * cancelled before a solution (or an UNSAT proof) was found -- as opposed to {@link
 * LimitExceededException}, a pre-configured budget rather than an external signal.
 *
 * <p>Thrown from {@link BoundSolver#getSolution()} in the satisfaction chain: from the two
 * decorators with a genuine search algorithm of their own rather than a plain {@code
 * getSolutions().findFirst()} -- {@link DomWdegLubySearch}'s Luby-restart search and {@link
 * io.github.rcrida.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver}'s cutset-assignment
 * enumeration -- and from the chain's one-time preprocessing pass, which used to swallow it.
 */
public class SolverCancelledException extends InconclusiveSearchException {
    public SolverCancelledException(Statistics statistics) {
        super("Solver cancelled after " + statistics.getNodesExplored() + " nodes", statistics);
    }
}
