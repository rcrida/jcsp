package io.github.rcrida.jcsp.constraints.nary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link CumulativePropagation}'s energetic reasoning directly. Every case here is one
 * the timetabling pass in both cumulative constraints cannot reach -- no task has a compulsory part
 * in any of them -- which is the whole reason this pass exists.
 */
class CumulativePropagationTest {

    /** A single-task-per-entry builder, to keep each scenario readable as a table of tasks. */
    private static CumulativePropagation.Tasks tasks(double limit, double[]... rows) {
        int n = rows.length;
        double[] est = new double[n];
        double[] lst = new double[n];
        double[] durations = new double[n];
        double[] resources = new double[n];
        double[] maxDurations = new double[n];
        double[] minEnergies = new double[n];
        for (int i = 0; i < n; i++) {
            est[i] = rows[i][0];
            lst[i] = rows[i][1];
            durations[i] = rows[i][2];
            resources[i] = rows[i][3];
            maxDurations[i] = rows[i].length > 4 ? rows[i][4] : rows[i][2];
            minEnergies[i] = rows[i].length > 5 ? rows[i][5] : rows[i][2] * rows[i][3];
        }
        return new CumulativePropagation.Tasks(est, lst, durations, resources, maxDurations, minEnergies,
                limit, Double.POSITIVE_INFINITY);
    }

    // --- minimumWork ---------------------------------------------------------------------------

    @Test void minimumWork_taskCanAvoidTheWindow_isZero() {
        // [0,10] start window, duration 2: the task fits entirely before [8,10).
        assertThat(CumulativePropagation.minimumWork(0, 10, 2, 5, 8, 10)).isZero();
    }

    @Test void minimumWork_taskCannotAvoidTheWindow_isTheForcedOverlap() {
        // est 0, lst 1, duration 3 -> the task covers [1,3) whatever it does; height 5 -> 10.
        assertThat(CumulativePropagation.minimumWork(0, 1, 3, 5, 1, 3)).isEqualTo(10.0);
    }

    @Test void minimumWork_windowNarrowerThanTheTask_isCappedByTheWindow() {
        assertThat(CumulativePropagation.minimumWork(0, 0, 10, 2, 3, 5)).isEqualTo(4.0);
    }

    // --- overload ------------------------------------------------------------------------------

    @Test void energetic_windowOverloaded_reportsTheContributingTasks() {
        // Two unit-height tasks of duration 2 must both fall inside [0,3) on a capacity-1
        // resource: 4 units of work into 3 units of room. The third task is free to sit well
        // clear of that window, so it is not among the tasks the conflict is attributed to.
        var result = CumulativePropagation.energetic(tasks(1,
                new double[]{0, 1, 2, 1},
                new double[]{0, 1, 2, 1},
                new double[]{20, 40, 2, 1}));
        assertThat(result.feasible()).isFalse();
        assertThat(result.overloaded()).containsExactlyInAnyOrder(0, 1);
    }

    @Test void energetic_withinCapacity_isFeasibleAndChangesNothing() {
        var result = CumulativePropagation.energetic(tasks(2,
                new double[]{0, 8, 2, 1},
                new double[]{0, 8, 2, 1}));
        assertThat(result.feasible()).isTrue();
        assertThat(result.est()).containsExactly(0.0, 0.0);
        assertThat(result.lst()).containsExactly(8.0, 8.0);
    }

    // --- bound tightening ----------------------------------------------------------------------

    @Test void energetic_windowFullyClaimed_pushesAnotherTaskPastIt() {
        // Task 0 is pinned to [0,3) and fills the resource. Task 1 has a free start window, and no
        // compulsory part of its own, but cannot overlap [0,3) at all -- so it starts at 3.
        var result = CumulativePropagation.energetic(tasks(1,
                new double[]{0, 0, 3, 1},
                new double[]{0, 5, 2, 1}));
        assertThat(result.feasible()).isTrue();
        assertThat(result.est()[1]).isEqualTo(3.0);
    }

    @Test void energetic_windowFullyClaimed_pullsAnotherTaskBackBeforeIt() {
        // The mirror: task 0 occupies [2,5), so task 1 must finish by 2 and start no later than 0.
        var result = CumulativePropagation.energetic(tasks(1,
                new double[]{2, 2, 3, 1},
                new double[]{0, 3, 2, 1}));
        assertThat(result.feasible()).isTrue();
        assertThat(result.lst()[1]).isEqualTo(0.0);
    }

    @Test void energetic_boundsCross_isInfeasibleAndCitesTheTaskItself() {
        // Task 0 fills [0,4). Task 1 needs 2 units and can only start in [1,2], all of which
        // overlaps -- pushing its earliest start past its latest.
        var result = CumulativePropagation.energetic(tasks(1,
                new double[]{0, 0, 4, 1},
                new double[]{1, 2, 2, 1}));
        assertThat(result.feasible()).isFalse();
        assertThat(result.overloaded()).contains(1);
    }

    @Test void energetic_zeroHeightTask_isLeftAlone() {
        // A task consuming nothing can sit anywhere, and must never be divided by its own height.
        var result = CumulativePropagation.energetic(tasks(1,
                new double[]{0, 0, 3, 1},
                new double[]{0, 5, 2, 0}));
        assertThat(result.feasible()).isTrue();
        assertThat(result.est()[1]).isZero();
        assertThat(result.lst()[1]).isEqualTo(5.0);
    }

    // --- energy floors -------------------------------------------------------------------------

    @Test void energetic_energyFloor_catchesAnOverloadTheDomainMinimaHide() {
        // A 2x12 rectangle that may be rotated has width and height domains both reaching 2, so
        // duration-minimum times resource-minimum reports 4 against its real area of 24. Contained
        // in [0,10) on a capacity-2 resource, 24 does not fit in 20 and 4 does.
        double[] rotatable = {0, 0, 2, 2, 2, 24};
        assertThat(CumulativePropagation.energetic(tasks(2, rotatable)).feasible()).isFalse();

        double[] withoutFloor = {0, 0, 2, 2, 2, 4};
        assertThat(CumulativePropagation.energetic(tasks(2, withoutFloor)).feasible()).isTrue();
    }

    @Test void energetic_energyFloor_ignoredWhenTheTaskCanLeaveTheWindow() {
        // Same floor, but the task's own latest completion runs past every window it could be
        // charged for, so nothing may be assumed about how much of it lands inside.
        double[] escaping = {0, 20, 2, 2, 12, 24};
        assertThat(CumulativePropagation.energetic(tasks(2, escaping)).feasible()).isTrue();
    }

    @Test void tasks_sizeIsTheTaskCount() {
        assertThat(tasks(1, new double[]{0, 0, 1, 1}, new double[]{0, 0, 1, 1}).size()).isEqualTo(2);
    }
}
