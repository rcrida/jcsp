package io.github.rcrida.jcsp.solver.neighbourhood;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Random;

/**
 * When {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver} makes a neighbourhood run, which generator
 * it uses and how many decisions it frees: the bookkeeping of a {@link LargeNeighbourhoodSearch}, kept
 * apart from the search itself because it is arithmetic on counters and nothing else. One instance
 * belongs to one solve.
 * <p>
 * This is the solver's mechanism, not an extension point: public only because the solver is in another
 * package. Everything is counted in nodes, never in time, so a seeded solve makes the same runs wherever
 * it is run.
 */
public final class NeighbourhoodSchedule {
    /** The least share of the draws a generator keeps, whatever it has found lately, so it can recover. */
    private static final double MIN_SHARE_OF_DRAWS = 0.15;
    private static final double SMOOTHING = 0.3;
    private static final double SMALLEST_FRACTION = 0.02;
    private static final double LARGEST_FRACTION = 0.9;

    private final LargeNeighbourhoodSearch config;
    private final double[] rate;
    private double freeFraction;
    private long nodesSinceGain;

    public NeighbourhoodSchedule(@NonNull LargeNeighbourhoodSearch config) {
        this.config = config;
        this.rate = new double[config.generators().size()];
        Arrays.fill(rate, 1.0);
        this.freeFraction = config.initialFreeFraction();
    }

    /**
     * Whether a neighbourhood run is due, given the nodes the unrestricted runs and the neighbourhood runs
     * have each spent so far.
     */
    public boolean due(long unrestrictedNodes, long neighbourhoodNodes) {
        if (unrestrictedNodes < config.graceNodes()) return false;
        long total = unrestrictedNodes + neighbourhoodNodes;
        boolean paying = nodesSinceGain <= Math.max(config.patienceNodes(), config.patienceFraction() * total);
        double share = paying ? config.share() : config.probeShare();
        return neighbourhoodNodes <= share * unrestrictedNodes;
    }

    /** The index of the generator for the next run, drawn in proportion to how much each has found lately. */
    public int generator(@NonNull Random random) {
        double total = 0;
        for (double r : rate) total += r;
        double[] weight = new double[rate.length];
        double weights = 0;
        for (int i = 0; i < rate.length; i++) {
            weight[i] = total < 1e-9 ? 1.0 : Math.max(rate[i], MIN_SHARE_OF_DRAWS * total);
            weights += weight[i];
        }
        double target = random.nextDouble() * weights;
        for (int i = 0; i < weight.length - 1; i++) {
            target -= weight[i];
            if (target < 0) return i;
        }
        return weight.length - 1;
    }

    /** How many of {@code decisions} (at least two) the next run frees: always one or more, and never all. */
    public int freeCount(int decisions) {
        return Math.max(1, Math.min(decisions - 1, (int) Math.round(freeFraction * decisions)));
    }

    /**
     * Records how a run came out: whether it improved the incumbent, whether it ended at its cutoff
     * rather than by exhausting its neighbourhood, and the nodes it cost to set up and search.
     */
    public void record(int generator, boolean improved, boolean cutoff, long nodes) {
        double found = improved ? 1000.0 / Math.max(nodes, 50) : 0.0;
        rate[generator] = (1 - SMOOTHING) * rate[generator] + SMOOTHING * found;
        nodesSinceGain = improved ? 0 : nodesSinceGain + nodes;
        if (!improved) {
            freeFraction = cutoff
                    ? Math.max(SMALLEST_FRACTION, freeFraction * 0.85)
                    : Math.min(LARGEST_FRACTION, freeFraction * 1.25 + 0.01);
        }
    }
}
