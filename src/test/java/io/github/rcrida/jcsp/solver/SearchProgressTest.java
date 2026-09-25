package io.github.rcrida.jcsp.solver;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SearchProgressTest {

    private static final BigInteger ROOT = BigInteger.valueOf(1_000_000L);

    @Test
    void beforeAnythingIsResolved_theWholeRootSpaceRemains() {
        var progress = new SearchProgress();
        assertThat(progress.explored()).isZero();
        assertThat(progress.remainingOf(ROOT)).isEqualTo(ROOT);
    }

    @Test
    void eachCompletedChildRemovesItsOwnShareOfTheRootSpace() {
        var progress = new SearchProgress();
        progress.complete(0.25); // one of four children at the root
        assertThat(progress.explored()).isEqualTo(0.25);
        assertThat(progress.remainingOf(ROOT)).isEqualTo(BigInteger.valueOf(750_000L));

        progress.complete(0.125); // half of a sibling, one level deeper
        assertThat(progress.remainingOf(ROOT)).isEqualTo(BigInteger.valueOf(625_000L));
    }

    @Test
    void aFullyTraversedTreeLeavesNothing() {
        var progress = new SearchProgress();
        progress.complete(0.5);
        progress.complete(0.5);
        assertThat(progress.remainingOf(ROOT)).isZero();
    }

    /** Summing many small weights can drift past 1.0; the result must not go negative. */
    @Test
    void floatingPointDriftPastFullyExplored_clampsAtZeroRatherThanGoingNegative() {
        var progress = new SearchProgress();
        progress.complete(1.5);
        assertThat(progress.remainingOf(ROOT)).isZero();
    }

    @Test
    void reset_discardsTheDescentSoTheNextRestartStartsFromTheWholeTree() {
        var progress = new SearchProgress();
        progress.complete(0.75);
        progress.reset();
        assertThat(progress.explored()).isZero();
        assertThat(progress.remainingOf(ROOT)).isEqualTo(ROOT);
    }
}
