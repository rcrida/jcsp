package io.github.rcrida.jcsp.solver;

import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RestartScheduleTest {

    private static long[] take(LongSupplier budgets, int count) {
        return LongStream.generate(budgets).limit(count).toArray();
    }

    @Test
    void luby_isTheLubySequenceScaledByTheUnit() {
        assertThat(take(RestartSchedule.LUBY.budgets(100), 8))
                .containsExactly(100, 100, 200, 100, 100, 200, 400, 100);
    }

    @Test
    void interleaved_runsWhicheverTrackHasSpentLess() {
        // Luby (unit 100) spends 100, 100, 200, 100, 100, 200, 400 ... and the geometric track
        // 100, 150, 225, 337, 506 ... (100 * 1.5^j). Luby goes first and again whenever it has spent
        // no more than the geometric track, so the two stay within one budget of each other.
        assertThat(take(RestartSchedule.INTERLEAVED.budgets(100), 12))
                .containsExactly(100, 100, 100, 150, 200, 225, 100, 337, 100, 200, 400, 506);
    }

    @Test
    void interleaved_geometricBudgetsGrowWithoutBound() {
        // What lets a refutation finish: a long enough run eventually comes round. Each track gets
        // equal work, so the geometric track advances only once per many small Luby restarts.
        assertThat(LongStream.of(take(RestartSchedule.INTERLEAVED.budgets(100), 2000)).max().orElseThrow())
                .isGreaterThan(100_000);
    }

    @Test
    void eachCallStartsAFreshSequence() {
        assertThat(take(RestartSchedule.INTERLEAVED.budgets(100), 5))
                .isEqualTo(take(RestartSchedule.INTERLEAVED.budgets(100), 5));
    }
}
