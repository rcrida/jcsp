package io.github.rcrida.jcsp.solver;

/**
 * Decides whether a search node should spend an LP solve on its bound, backing off once the LP has
 * stopped earning it.
 * <p>
 * {@link BranchAndBoundSolver} solves one LP per node when its objective is a {@link
 * LinearObjective} (<a href="../../../../../../../docs/adr/0009-joint-continuous-discrete-optimization.md">ADR-0009</a>),
 * and on some problems that bound never prunes anything at all, while accounting for most of the
 * solve. Others depend on it, and lose orders of magnitude in nodes without it -- see
 * <a href="../../../../../../../docs/adr/0046-adaptive-lp-gate-keyed-on-cut-rate.md">ADR-0046</a>
 * for both censuses.
 * <p>
 * So the gate is keyed on the one signal that distinguishes them, rather than on a frequency: a
 * streak of solves that failed to cut. Until {@link #PATIENCE} consecutive solves have all failed,
 * every node solves. After that the LP runs on one node in {@link #PATIENCE}, which is enough to
 * notice if it starts cutting again — any cut resets the gate to full rate immediately. A problem
 * whose LP prunes regularly therefore never reaches the streak and is untouched, while one whose
 * LP is dead weight pays about a {@link #PATIENCE}th of the cost.
 * <p>
 * Skipping a bound is always sound: it forgoes pruning, never admits anything. It is only
 * <em>affordable</em> because the incumbent stays enforced regardless, by {@link
 * BranchAndBoundSolver#applyObjectiveCut} applying it as a propagated constraint before the node is
 * branched (ADR-0029). Where there is no such cut to fall back on, a skipped node has no bound at
 * all, so those searches get {@link #alwaysSolving} instead. What a skipped node also forgoes is the
 * LP's most-fractional branching hint, falling back to the configured variable selector.
 * <p>
 * Per-search state, so one is created per {@link BranchAndBoundSolver#getSolutions} call and
 * threaded through the search exactly as the {@link
 * io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector} is, for the same
 * reason: two solves from one solver must not share one.
 */
final class LpGate {

    /**
     * Consecutive non-cutting solves tolerated before backing off, and afterwards the reciprocal of
     * the rate the LP is retried at. One constant rather than separate patience/backoff/cap knobs,
     * which is what an earlier adaptive-gate attempt was rejected for
     * (<a href="../../../../../../../docs/adr/0025-lp-model-reuse-across-search-nodes.md">ADR-0025</a>).
     * <p>
     * 128 because the win does not depend on it while the protection does: an instance whose LP
     * prunes regularly cannot accumulate 128 consecutive misses, so the gate provably never engages
     * there, while a dead LP pays 128 solves against the hundreds of thousands it would otherwise
     * run. Swept against both signals -- see
     * <a href="../../../../../../../docs/adr/0046-adaptive-lp-gate-keyed-on-cut-rate.md">ADR-0046</a>
     * for the figures; do not change it without re-measuring both.
     */
    static final int PATIENCE = 128;

    private final int patience;
    private int missStreak;
    private int skipped;

    LpGate() {
        this(PATIENCE);
    }

    /** Explicit patience, so this policy can be exercised without a 128-node run-up. */
    LpGate(int patience) {
        this.patience = patience;
    }

    /**
     * A gate that never backs off, for a search where the LP bound is the <em>only</em> thing
     * bounding a node: backing off is sound only because the incumbent stays enforced by the
     * objective cut, and an objective with no expressible cut (see {@link
     * ObjectiveCut#isExpressible}) has nothing to stay enforced by. Expressed as unreachable
     * patience rather than a flag, so there is one policy here rather than two.
     */
    static LpGate alwaysSolving() {
        return new LpGate(Integer.MAX_VALUE);
    }

    /**
     * Whether this node should solve the LP, advancing the skip counter when it should not. Call
     * exactly once per node, and report the outcome to {@link #record} when it returns {@code true}.
     */
    boolean solveThisNode() {
        if (missStreak < patience) {
            return true;
        }
        if (++skipped >= patience) {
            skipped = 0;
            return true;
        }
        return false;
    }

    /** Feeds back whether the LP solve this node just ran pruned it. */
    void record(boolean cut) {
        if (cut) {
            missStreak = 0;
            skipped = 0;
        } else if (missStreak < patience) {
            missStreak++;
        }
    }
}
