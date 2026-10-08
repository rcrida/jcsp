package io.github.rcrida.jcsp.solver;

/**
 * Decides whether a search node should spend an LP solve on its bound, backing off once the LP has
 * stopped earning it.
 * <p>
 * {@link BranchAndBoundSolver} solves one LP per node when its objective is a {@link
 * LinearObjective} (ADR-0009), and on some problems that bound never prunes anything at all: an
 * instrumented census of {@code Vrp-P-n16-k8} recorded 322,995 LP solves and <em>zero</em> cuts,
 * with the LP accounting for 74% of the solve. Others depend on it — {@code Knapsack-30-100-00}
 * cuts on 73 of 277 solves, and removing the LP there costs 396 nodes to 192,852.
 * <p>
 * So the gate is keyed on the one signal that distinguishes them, rather than on a frequency: a
 * streak of solves that failed to cut. Until {@link #PATIENCE} consecutive solves have all failed,
 * every node solves. After that the LP runs on one node in {@code PATIENCE}, which is enough to
 * notice if it starts cutting again — any cut resets the gate to full rate immediately. A problem
 * whose LP prunes regularly therefore never reaches the streak and is untouched, while one whose
 * LP is dead weight pays about a {@code PATIENCE}th of the cost.
 * <p>
 * Skipping a bound is always sound: it forgoes pruning, never admits anything. The incumbent stays
 * enforced regardless, because {@link BranchAndBoundSolver#applyObjectiveCut} applies it as a
 * propagated constraint before the node is branched (ADR-0029). What a skipped node also forgoes is
 * the LP's most-fractional branching hint, falling back to the configured variable selector.
 * <p>
 * Per-search state, so one is created per {@link BranchAndBoundSolver#getSolutions} call and
 * threaded through the search exactly as the {@code AdaptiveVariableSelector} is, for the same
 * reason: two solves from one solver must not share one.
 */
final class LpGate {

    /**
     * Consecutive non-cutting solves tolerated before backing off, and afterwards the reciprocal of
     * the rate the LP is retried at. One constant rather than separate patience/backoff/cap knobs,
     * which is what an earlier adaptive-gate attempt was rejected for (ADR-0025).
     * <p>
     * 128 because the win does not depend on it while the protection does. Swept against both
     * signals at once -- {@code Vrp-P-n16-k8} reported {@code o 558} at 16, 32, 64 and 128 alike,
     * while {@code Knapsack-30-100-00} took 486, 431, 431 and 396 nodes -- so the largest value is
     * free on the instance the gate is for and exactly recovers the ungated node count on the
     * instance it must not disturb. At 128 that is not luck: {@code Knapsack-30-100-00} runs 277 LP
     * solves of which 73 cut, so it cannot accumulate 128 consecutive misses and the gate provably
     * never engages there. A dead LP pays 128 solves before backing off, negligible against the
     * 322,995 {@code Vrp-P-n16-k8} would otherwise run.
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
