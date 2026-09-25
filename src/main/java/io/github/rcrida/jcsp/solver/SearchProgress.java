package io.github.rcrida.jcsp.solver;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Depth-first progress accumulator: the fraction of a search tree already resolved, used to turn a
 * root search space into the space still unexplored.
 * <p>
 * A node whose subtree is a fraction {@code w} of the whole tree and which branches over {@code d}
 * candidate values gives each child a weight of {@code w / d}. Whenever a child is finished with —
 * exhausted, refuted by a consistency check, or pruned by inference, which are indistinguishable
 * here and all mean "will not be visited again" — its weight is added to {@link #explored}. The
 * root subtree has weight {@code 1.0}, so the accumulator runs from 0 to 1 over a complete
 * traversal. This is the classic SAT progress estimate (MiniSat's {@code progressEstimate}) with a
 * per-node branching factor rather than a fixed one, since a CSP variable's live domain size varies
 * from node to node.
 * <p>
 * Scoped to a single depth-first descent. A solver that restarts calls {@link #reset} each time,
 * because a restart re-descends from the root and — with nogood learning off by default — keeps no
 * record of what earlier restarts refuted, so two descents' explored fractions overlap and summing
 * them would overcount. Not thread-safe: one instance belongs to one search.
 */
final class SearchProgress {

    private double explored;

    /** Fraction of the current descent's tree already resolved, from 0.0 to 1.0. */
    double explored() {
        return explored;
    }

    /** Discards the current descent's progress, for a solver about to restart from the root. */
    void reset() {
        explored = 0.0;
    }

    /** Records that a child subtree of the given weight will not be visited again. */
    void complete(double weight) {
        explored += weight;
    }

    /**
     * {@code rootSpace} scaled by the unexplored fraction, rounded down to a whole number of
     * assignments. Clamped into {@code [0, rootSpace]}: the accumulator is floating-point and sums
     * many small weights, so it can drift a little past 1.0 on a long descent, and a negative or
     * oversized "remaining" figure would be worse than a slightly imprecise one.
     */
    BigInteger remainingOf(BigInteger rootSpace) {
        double unexplored = Math.min(1.0, Math.max(0.0, 1.0 - explored));
        return new BigDecimal(rootSpace).multiply(BigDecimal.valueOf(unexplored)).toBigInteger();
    }
}
