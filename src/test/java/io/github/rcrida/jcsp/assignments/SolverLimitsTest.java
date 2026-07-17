package io.github.rcrida.jcsp.assignments;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolverLimitsTest {

    @Test
    void unlimitedNeverExceedsNodeLimit() {
        SolverLimits limits = SolverLimits.unlimited();
        assertThat(limits.isNodeLimitExceeded(Long.MAX_VALUE)).isFalse();
        assertThat(limits.isNodeLimitExceeded(0)).isFalse();
    }

    @Test
    void unlimitedDeadlineIsMaxValue() {
        assertThat(SolverLimits.unlimited().deadlineNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void unlimitedNeverExceedsTimeLimit() {
        assertThat(SolverLimits.unlimited().isTimeLimitExceeded(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void ofNodesFactorySetsNodeLimitOnly() {
        SolverLimits limits = SolverLimits.ofNodes(10);
        assertThat(limits.getNodeLimit()).isEqualTo(10);
        assertThat(limits.getTimeLimit()).isNull();
        assertThat(limits.deadlineNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void ofTimeFactorySetsTimeLimitOnly() {
        SolverLimits limits = SolverLimits.ofTime(Duration.ofSeconds(5));
        assertThat(limits.getNodeLimit()).isZero();
        assertThat(limits.getTimeLimit()).isEqualTo(Duration.ofSeconds(5));
        assertThat(limits.isNodeLimitExceeded(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void ofCombinedFactorySetsBoth() {
        SolverLimits limits = SolverLimits.of(100, Duration.ofMillis(500));
        assertThat(limits.getNodeLimit()).isEqualTo(100);
        assertThat(limits.getTimeLimit()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void isNodeLimitExceededFalseBeforeLimit() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        assertThat(limits.isNodeLimitExceeded(4)).isFalse();
    }

    @Test
    void isNodeLimitExceededTrueAtLimit() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        assertThat(limits.isNodeLimitExceeded(5)).isTrue();
    }

    @Test
    void isNodeLimitExceededTrueBeyondLimit() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        assertThat(limits.isNodeLimitExceeded(100)).isTrue();
    }

    @Test
    void isTimeLimitExceededFalseWhenDeadlineIsMaxValue() {
        assertThat(SolverLimits.ofTime(Duration.ofSeconds(60)).isTimeLimitExceeded(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void isTimeLimitExceededFalseWhenDeadlineNotYetReached() {
        long futureDeadline = System.nanoTime() + 60_000_000_000L;
        assertThat(SolverLimits.ofTime(Duration.ofSeconds(60)).isTimeLimitExceeded(futureDeadline)).isFalse();
    }

    @Test
    void isTimeLimitExceededTrueWhenPastDeadline() {
        long pastDeadline = System.nanoTime() - 1;
        assertThat(SolverLimits.ofTime(Duration.ofSeconds(1)).isTimeLimitExceeded(pastDeadline)).isTrue();
    }

    @Test
    void negativeNodeLimitThrows() {
        assertThatThrownBy(() -> SolverLimits.ofNodes(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeLimit");
    }

    @Test
    void isLimitReachedFalseInitially() {
        assertThat(SolverLimits.ofNodes(5).isLimitReached()).isFalse();
    }

    @Test
    void markLimitReachedSetsIsLimitReachedTrue() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        limits.markLimitReached();
        assertThat(limits.isLimitReached()).isTrue();
    }

    @Test
    void markLimitReachedIsIdempotent() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        limits.markLimitReached();
        limits.markLimitReached();
        assertThat(limits.isLimitReached()).isTrue();
    }

    @Test
    void resetLimitReachedClearsFlag() {
        SolverLimits limits = SolverLimits.ofNodes(5);
        limits.markLimitReached();
        limits.resetLimitReached();
        assertThat(limits.isLimitReached()).isFalse();
    }
}
