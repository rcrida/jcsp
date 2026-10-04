package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Statistics;
import lombok.Getter;

/**
 * Thrown by {@link BoundSolver#getSolution()} when it stopped without settling the question: it
 * found no solution <em>and</em> did not prove there is none.
 *
 * <p>This type exists so that the two outcomes can be told apart at all. {@link
 * java.util.Optional#empty()} from {@link BoundSolver#getSolution()} means <strong>proven
 * unsatisfiable</strong> -- a search that ran to completion and found nothing -- and every way of
 * stopping early throws one of these instead. Before this type existed, a cancellation during the
 * chain's one-time preprocessing pass and an exhausted restart budget both surfaced as {@code
 * Optional.empty()}, so a caller inferring UNSAT from an empty result could report a refutation that
 * had never happened; {@code Xcsp3ProblemRunner} did exactly that, printing {@code s UNSATISFIABLE}.
 * See <a href="../../../../../../../docs/adr/0043-inconclusive-is-not-unsatisfiable.md">ADR-0043</a>.
 *
 * <p>The subclasses say how the search stopped, and a caller that only cares <em>that</em> it
 * stopped catches this: {@link LimitExceededException} (a pre-configured {@link
 * io.github.rcrida.jcsp.assignments.SolverLimits} budget), {@link SolverCancelledException} (an
 * external {@link Cancellation} signal), {@link RestartsExhaustedException} (a restart cap reached).
 *
 * <p>{@link BoundSolver#getSolutions()} is unaffected and still truncates its stream silently, as
 * does the optimization chain's {@link BranchAndBoundSolver}, which returns the best incumbent found
 * so far rather than throwing.
 */
@Getter
public abstract class InconclusiveSearchException extends RuntimeException {
    private final Statistics statistics;

    protected InconclusiveSearchException(String message, Statistics statistics) {
        super(message);
        this.statistics = statistics;
    }
}
