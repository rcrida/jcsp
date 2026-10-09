package io.github.rcrida.jcsp.solver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LpGateTest {

    /** Runs {@code n} nodes whose LP solve does not cut, returning how many actually solved. */
    private static int solvesOver(LpGate gate, int nodes) {
        int solves = 0;
        for (int i = 0; i < nodes; i++) {
            if (gate.solveThisNode()) {
                solves++;
                gate.record(false);
            }
        }
        return solves;
    }

    @Test
    void beforeThePatienceStreak_everyNodeSolves() {
        LpGate gate = new LpGate(4);
        assertThat(solvesOver(gate, 4)).isEqualTo(4);
    }

    @Test
    void afterThePatienceStreak_solvesOneNodeInPatience() {
        LpGate gate = new LpGate(4);
        solvesOver(gate, 4);
        // Backed off: the next 4 nodes yield one solve, the retry probe.
        assertThat(solvesOver(gate, 4)).isEqualTo(1);
        assertThat(solvesOver(gate, 8)).isEqualTo(2);
    }

    @Test
    void aCutResetsToFullRate() {
        LpGate gate = new LpGate(4);
        solvesOver(gate, 4);
        assertThat(gate.solveThisNode()).isFalse();

        // Reach the retry probe and let it cut this time.
        while (!gate.solveThisNode()) {
            // advancing the skip counter
        }
        gate.record(true);

        assertThat(solvesOver(gate, 4)).isEqualTo(4);
    }

    @Test
    void aCutPartWayThroughAStreakPreventsBackingOff() {
        LpGate gate = new LpGate(4);
        for (int i = 0; i < 3; i++) {
            assertThat(gate.solveThisNode()).isTrue();
            gate.record(false);
        }
        assertThat(gate.solveThisNode()).isTrue();
        gate.record(true);

        // The streak restarted, so a further patience-1 misses still must not back off.
        assertThat(solvesOver(gate, 3)).isEqualTo(3);
    }

    @Test
    void theMissStreakDoesNotRunAwayWhileBackedOff() {
        // record(false) is still called on every retry probe; the streak is capped at patience so it
        // cannot overflow or drift the retry rate over a long solve.
        LpGate gate = new LpGate(2);
        assertThat(solvesOver(gate, 10_000)).isEqualTo(2 + (10_000 - 2) / 2);
    }

    @Test
    void alwaysSolvingNeverBacksOff() {
        // For a search where the LP bound is the only thing bounding a node, because the objective
        // has no expressible cut to keep the incumbent enforced.
        LpGate gate = LpGate.alwaysSolving();

        assertThat(solvesOver(gate, 10_000)).isEqualTo(10_000);
    }

    @Test
    void patienceOfOneBacksOffImmediately() {
        LpGate gate = new LpGate(1);
        // Solves, misses, and from then on every node is a retry probe, so nothing is ever skipped.
        assertThat(solvesOver(gate, 5)).isEqualTo(5);
    }
}
