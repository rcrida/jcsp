package io.github.rcrida.jcsp.constraints.nary;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Energetic reasoning for {@link CumulativeConstraint} and {@link CumulativeVariableConstraint},
 * which reduce to the same per-task bounds and so share one implementation.
 * <p>
 * Both classes already run timetabling, which acts only where a task has a <em>compulsory part</em>
 * ({@code lst < est + duration}) -- a task whose start window is wider than its own duration
 * contributes nothing at all. That is not a corner case: on {@code StripPacking-C1P1}, a 20x20
 * strip packed exactly by 16 rectangles, every rectangle has {@code x in [0, 20 - w]}, so a
 * compulsory part would need {@code w > 10} and no rectangle has one. Timetabling is structurally
 * inert there at any capacity, which is why the instance's redundant projections could fire
 * thousands of times and still leave the search no better off.
 * <p>
 * Energetic reasoning needs no compulsory part. For a window {@code [t1, t2)} it asks how much work
 * each task must do <em>inside</em> that window however it is placed ({@link #minimumWork}), and
 * compares the total against the window's capacity. Exceeding it is a direct infeasibility; falling
 * just short bounds where a single task can be, which is the half that tightens domains. Its grip
 * is tightest exactly where timetabling has none -- a resource with little slack and tasks free to
 * move -- and on a zero-slack instance, where total energy equals capacity times horizon, every
 * window's balance is tight.
 * <p>
 * This is the bound-tightening half {@code energyOverload} deliberately left alone, whose Javadoc
 * declined to port disjunctive edge-finding's rule on the grounds that its proof assumes no two
 * tasks can coexist. That objection is real and still stands for that rule; the energetic argument
 * is a different one, derived in {@link #energetic} below, and never assumes tasks cannot overlap.
 * <p>
 * {@link Tasks#minEnergies} exists because a task with <em>variable</em> size has more energy than
 * its bounds admit separately. A rectangle that is 2x12 or 12x2 has an area of 24 either way, but
 * its width and height domains both reach down to 2, so duration-minimum times resource-minimum
 * reports 4. Across {@code StripPacking-C1P1} that understates the load as 220 of 400 -- a strip
 * packed to the last cell looks 45% empty, and no energetic deduction is available anywhere. The
 * floor is supplied by whoever knows the sizes are linked (the XCSP3 parser reads it off the
 * rotation table) and is only applied to a task the window provably contains, where all of its
 * energy is spent inside by definition.
 */
final class CumulativePropagation {

    private CumulativePropagation() {
    }

    /**
     * The per-task bounds both cumulative constraints reduce to -- {@link CumulativeConstraint}
     * from its fixed durations/resources, {@link CumulativeVariableConstraint} from its
     * duration/resource variables' current domain minima.
     */
    record Tasks(double @NonNull [] est, double @NonNull [] lst,
                 double @NonNull [] durations, double @NonNull [] resources,
                 double @NonNull [] maxDurations, double @NonNull [] minEnergies,
                 double limit, double horizon) {

        int size() {
            return est.length;
        }

        /**
         * The furthest point task {@code i} can reach. {@code lst + maxDuration} is the bound from
         * its own two domains, and {@link #horizon} is any tighter one the caller knows jointly --
         * a rotatable rectangle starts as late as 18 only when it is 2 wide, so its own domains
         * report a reach of 30 on a strip that ends at 20.
         */
        double maxEnd(int i) {
            return Math.min(lst[i] + maxDurations[i], horizon);
        }
    }

    /**
     * Tightened bounds, or -- when {@link #overloaded} is non-null -- the tasks whose combined
     * minimum work exceeded some window's capacity, for the caller to cite as the conflict.
     */
    record Energetic(@Nullable Set<Integer> overloaded, double @NonNull [] est, double @NonNull [] lst) {

        boolean feasible() {
            return overloaded == null;
        }
    }

    /**
     * The work task {@code i} must perform inside {@code [t1, t2)} under <em>any</em> placement
     * allowed by its current bounds: its height times the smallest overlap achievable, which is the
     * lesser of shifting it as far left as possible (overlap {@code ect - t1}) and as far right as
     * possible (overlap {@code t2 - lst}), capped by both the window and the task's own duration.
     * Zero when the task can avoid the window entirely.
     */
    static double minimumWork(double est, double lst, double duration, double resource,
                              double t1, double t2) {
        double overlap = Math.min(Math.min(t2 - t1, duration),
                Math.min(est + duration - t1, t2 - lst));
        return overlap <= 0.0 ? 0.0 : resource * overlap;
    }

    /** The work task {@code i} does inside {@code [t1, t2)} when it starts exactly at {@code s}. */
    private static double workAt(double s, double duration, double resource, double t1, double t2) {
        double overlap = Math.min(s + duration, t2) - Math.max(s, t1);
        return overlap <= 0.0 ? 0.0 : resource * overlap;
    }

    /**
     * One energetic-reasoning pass over every relevant window, returning the tightened bounds.
     * <p>
     * For a window {@code [t1, t2)} of capacity {@code limit * (t2 - t1)}, every task {@code k}
     * consumes at least {@link #minimumWork} of it, so the energy left for any one task {@code i}
     * is {@code rest = capacity - sum over k != i of minimumWork(k)}. Two consequences, both used:
     * <ul>
     *   <li>If the total minimum work exceeds the capacity, no schedule exists -- reported as
     *       {@link Energetic#overloaded}.</li>
     *   <li>{@code i}'s own work in the window, as a function of its start {@code s}, rises while
     *       {@code s} moves into the window and falls once it is leaving, with no second peak. So
     *       the starts that consume more than {@code rest} form one contiguous stretch, and finding
     *       {@code i}'s earliest start already over that budget places the whole of {@code i}'s
     *       remaining domain to the right of it. Everything from {@code t2 - rest/resource} onward
     *       overlaps by at most {@code rest/resource}, so that is the new earliest start; the
     *       mirror argument on {@code i}'s latest start gives {@code t1 + rest/resource} as the new
     *       latest completion.</li>
     * </ul>
     * Unimodality is what makes the adjustment sound, and it is worth being explicit about: a start
     * far enough left that the task finishes before {@code t1} also consumes nothing, so "consumes
     * too much" alone would not justify pushing {@code i} right. It justifies it here only because
     * {@code i} cannot reach that left region -- its own {@code est} is already inside the
     * over-budget stretch.
     * <p>
     * Windows are taken from every {@code est}/{@code lst} as a left edge and every
     * {@code ect}/{@code lct} as a right edge, deduplicated -- the standard relevant-interval set,
     * since a window's balance can only change where some task's bound sits. Bounds tightened
     * earlier in the sweep are used by later windows; the minimum works are not recomputed within a
     * window after one is tightened, which only ever leaves {@code rest} too generous and so is
     * sound.
     */
    static @NonNull Energetic energetic(@NonNull Tasks tasks) {
        int n = tasks.size();
        double[] est = tasks.est().clone();
        double[] lst = tasks.lst().clone();
        double[] durations = tasks.durations();
        double[] resources = tasks.resources();
        double limit = tasks.limit();

        double[] leftEdges = new double[2 * n];
        double[] rightEdges = new double[2 * n];
        for (int i = 0; i < n; i++) {
            leftEdges[i] = est[i];
            leftEdges[n + i] = lst[i];
            rightEdges[i] = est[i] + durations[i];
            rightEdges[n + i] = lst[i] + durations[i];
        }
        leftEdges = distinctSorted(leftEdges);
        rightEdges = distinctSorted(rightEdges);

        double[] work = new double[n];
        for (double t1 : leftEdges) {
            for (double t2 : rightEdges) {
                if (t2 <= t1) continue;
                double capacity = limit * (t2 - t1);

                double total = 0.0;
                for (int k = 0; k < n; k++) {
                    work[k] = minimumWork(est[k], lst[k], durations[k], resources[k], t1, t2);
                    if (est[k] >= t1 && tasks.maxEnd(k) <= t2) {
                        // k cannot leave the window under any placement, so all of its energy is
                        // spent inside -- which is more than duration-minimum times
                        // resource-minimum whenever the two cannot take their minima together.
                        work[k] = Math.max(work[k], tasks.minEnergies()[k]);
                    }
                    total += work[k];
                }
                if (total > capacity) return new Energetic(contributors(work), est, lst);

                for (int i = 0; i < n; i++) {
                    if (resources[i] <= 0.0) continue;   // nothing to bound, and nothing to divide by
                    double rest = capacity - (total - work[i]);
                    if (workAt(est[i], durations[i], resources[i], t1, t2) > rest) {
                        est[i] = Math.max(est[i], t2 - rest / resources[i]);
                    }
                    if (workAt(lst[i], durations[i], resources[i], t1, t2) > rest) {
                        lst[i] = Math.min(lst[i], t1 + rest / resources[i] - durations[i]);
                    }
                    // Bounds that cross need no check of their own: they narrow the start domain to
                    // nothing, which is how the caller already learns the constraint is infeasible.
                }
            }
        }
        return new Energetic(null, est, lst);
    }

    /** The tasks that actually consumed any of the window, which are the ones worth citing. */
    private static Set<Integer> contributors(double[] work) {
        Set<Integer> contributors = new LinkedHashSet<>();
        for (int k = 0; k < work.length; k++) {
            if (work[k] > 0.0) contributors.add(k);
        }
        return contributors;
    }

    /** {@code values} sorted with duplicates dropped -- tasks routinely share a bound. */
    private static double[] distinctSorted(double[] values) {
        Arrays.sort(values);
        int kept = 0;
        for (int i = 0; i < values.length; i++) {
            if (i == 0 || values[i] != values[i - 1]) values[kept++] = values[i];
        }
        return Arrays.copyOf(values, kept);
    }
}
