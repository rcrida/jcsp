package io.github.rcrida.jcsp.solver.neighbourhood;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class NeighbourhoodScheduleTest {
    private static final List<NeighbourhoodGenerator.Factory> TWO =
            List.of(NeighbourhoodGenerator.Factory.RANDOM, NeighbourhoodGenerator.Factory.COST_WEIGHTED);

    /** Grace 100 nodes, 10% of decisions free, share 1.0 then 0.1 once 50 nodes (or 20% of all) go without a gain. */
    private static NeighbourhoodSchedule schedule(List<NeighbourhoodGenerator.Factory> generators) {
        return new NeighbourhoodSchedule(new LargeNeighbourhoodSearch(100, 0.1, 10, 100, 1.0, 0.1, 50, 0.2,
                DecisionSource.Factory.REPLAY, generators));
    }

    @Test
    void nothingIsDueDuringTheGracePeriod() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        assertThat(schedule.due(99, 0)).isFalse();
        assertThat(schedule.due(100, 0)).isTrue();
    }

    @Test
    void whilePayingARunIsDueUntilItsNodesPassTheShareOfTheUnrestrictedOnes() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        assertThat(schedule.due(100, 100)).isTrue();
        assertThat(schedule.due(100, 101)).isFalse();
    }

    @Test
    void afterPatienceRunsWithoutAGainTheShareFallsToTheProbeShare() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        schedule.record(0, false, true, 60);

        // 60 nodes without a gain exceeds max(50, 20% of 100): only the probe share, a tenth, is left.
        assertThat(schedule.due(100, 10)).isTrue();
        assertThat(schedule.due(100, 11)).isFalse();
    }

    @Test
    void patienceGrowsWithTheWorkDoneSoFar() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        schedule.record(0, false, true, 60);

        // At 1,000 unrestricted nodes 20% of all work is 200, so 60 nodes without a gain is still tolerated.
        assertThat(schedule.due(1_000, 1_000)).isTrue();
    }

    @Test
    void anImprovementRestoresTheFullShare() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        schedule.record(0, false, true, 60);
        assertThat(schedule.due(100, 11)).isFalse();

        schedule.record(0, true, false, 10);

        assertThat(schedule.due(100, 11)).isTrue();
    }

    @Test
    void aSingleGeneratorIsAlwaysChosen() {
        NeighbourhoodSchedule schedule = schedule(List.of(NeighbourhoodGenerator.Factory.RANDOM));
        Random random = new Random(1);

        for (int i = 0; i < 20; i++) assertThat(schedule.generator(random)).isZero();
    }

    @Test
    void equalGeneratorsAreDrawnAboutEquallyOften() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        Random random = new Random(1);
        int[] drawn = new int[2];

        for (int i = 0; i < 1_000; i++) drawn[schedule.generator(random)]++;

        assertThat(drawn[0]).isBetween(400, 600);
        assertThat(drawn[1]).isBetween(400, 600);
    }

    @Test
    void theGeneratorThatKeepsImprovingIsDrawnMoreButTheOtherIsNeverStarved() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        for (int i = 0; i < 30; i++) {
            schedule.record(1, true, false, 100);
            schedule.record(0, false, true, 100);
        }
        Random random = new Random(1);
        int[] drawn = new int[2];

        for (int i = 0; i < 1_000; i++) drawn[schedule.generator(random)]++;

        assertThat(drawn[1]).isGreaterThan(700);
        assertThat(drawn[0]).isBetween(100, 300);
    }

    @Test
    void whenNeitherHasFoundAnythingForAWhileTheyAreDrawnEquallyAgain() {
        NeighbourhoodSchedule schedule = schedule(TWO);
        for (int i = 0; i < 200; i++) {
            schedule.record(0, false, true, 100);
            schedule.record(1, false, true, 100);
        }
        Random random = new Random(1);
        int[] drawn = new int[2];

        for (int i = 0; i < 1_000; i++) drawn[schedule.generator(random)]++;

        assertThat(drawn[0]).isBetween(400, 600);
    }

    @Test
    void freeCountIsAFractionOfTheDecisionsButNeverNoneAndNeverAll() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        assertThat(schedule.freeCount(100)).isEqualTo(10);
        assertThat(schedule.freeCount(2)).isEqualTo(1);
        assertThat(schedule.freeCount(3)).isEqualTo(1);
    }

    @Test
    void aRunThatExhaustsItsNeighbourhoodWithoutGainGrowsTheNextOneUpToAPoint() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        for (int i = 0; i < 100; i++) schedule.record(0, false, false, 100);

        assertThat(schedule.freeCount(100)).isEqualTo(90);
        assertThat(schedule.freeCount(10)).isEqualTo(9);
    }

    @Test
    void aRunThatIsCutOffWithoutGainShrinksTheNextOneDownToAPoint() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        for (int i = 0; i < 100; i++) schedule.record(0, false, true, 100);

        assertThat(schedule.freeCount(100)).isEqualTo(2);
    }

    @Test
    void anImprovementLeavesTheSizeAlone() {
        NeighbourhoodSchedule schedule = schedule(TWO);

        schedule.record(0, true, false, 100);

        assertThat(schedule.freeCount(100)).isEqualTo(10);
    }
}
