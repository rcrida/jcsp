package io.github.rcrida.jcsp.solver.neighbourhood;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Whether {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver} also searches the neighbourhood of its
 * incumbent, and how.
 * <p>
 * A neighbourhood run takes the best solution so far, fixes most of the decisions that produced it, and
 * searches the few that were left free for a better solution, under the objective's cut and a short
 * cutoff. It finds improvements in a stretch of time that the complete search, which spends its restarts
 * on the whole tree, often does not, and it matters most when the instance is too large to prove: the
 * measured gains are on routing, scheduling and assignment problems that finish with a feasible
 * solution and no proof. It is search order only: every neighbourhood run is sound, and a drained
 * stream is still a proof of optimality, because that is decided by the unrestricted runs, which
 * a neighbourhood run never replaces (ADR-0065).
 * <p>
 * Neighbourhood runs take a share of the search's work, counted in nodes so that a seeded solve
 * repeats. None are made for the first {@link #graceNodes} nodes of the unrestricted runs, so a
 * search that proves quickly is the search it would have been without them. After that they run in
 * proportion {@link #share} to the unrestricted work while they keep finding improvements; once
 * {@code max(}{@link #patienceNodes}{@code , }{@link #patienceFraction} {@code * all work so far)}
 * nodes of them have gone by without one, they drop to {@link #probeShare} until one pays. They keep
 * their own variable selector and LP gate, so the unrestricted runs that carry the proof see the state
 * they would have had without them.
 * <p>
 * The decisions each run frees are chosen by one of the {@link #generators}, drawn in proportion to
 * the improvements each has found per node recently. The number freed starts at
 * {@link #initialFreeFraction} of the decisions and grows when a run exhausts its neighbourhood
 * without an improvement and shrinks when it is cut off.
 *
 * @param graceNodes          unrestricted nodes to search before the first neighbourhood run
 * @param initialFreeFraction fraction of the decisions freed by the first run, strictly between 0 and 1
 * @param repairFailures      failed candidates that end a neighbourhood run
 * @param repairNodes         nodes that end a neighbourhood run, which failures alone may not: a
 *                            connected neighbourhood can go deep for a long time without failing
 * @param share               neighbourhood nodes per unrestricted node while they are paying
 * @param probeShare          the same once they have stopped paying; at most {@code share}
 * @param patienceNodes       neighbourhood nodes without an improvement that are always tolerated
 * @param patienceFraction    fraction of all nodes so far that is tolerated instead, if larger
 * @param decisions           how the decisions of an incumbent, the variables a run may fix or free, are found
 * @param generators          the ways of choosing the decisions to free; none turns the search off
 */
public record LargeNeighbourhoodSearch(long graceNodes, double initialFreeFraction, long repairFailures,
                                       long repairNodes, double share, double probeShare, long patienceNodes,
                                       double patienceFraction,
                                       DecisionSource.@NonNull Factory decisions,
                                       @NonNull List<NeighbourhoodGenerator.Factory> generators) {

    /** No neighbourhood runs: the search is the complete one alone. The default. */
    public static final LargeNeighbourhoodSearch OFF =
            new LargeNeighbourhoodSearch(0, 0.05, 200, 10_000, 1.0, 0.05, 20_000, 0.1,
                    DecisionSource.Factory.REPLAY, List.of());

    /**
     * Neighbourhood runs after 50,000 nodes, drawing between the random and the cost-weighted generator.
     * Each run frees about 5% of the decisions to start with, ends at 200 failures or 10,000 nodes, and
     * takes as many nodes as the unrestricted runs until it has gone 20,000 nodes (or a tenth of the work
     * so far) without an improvement, then 5% of them.
     */
    public static final LargeNeighbourhoodSearch DEFAULT =
            new LargeNeighbourhoodSearch(50_000, 0.05, 200, 10_000, 1.0, 0.05, 20_000, 0.1,
                    DecisionSource.Factory.REPLAY,
                    List.of(NeighbourhoodGenerator.Factory.RANDOM, NeighbourhoodGenerator.Factory.COST_WEIGHTED));

    public LargeNeighbourhoodSearch {
        if (graceNodes < 0) throw new IllegalArgumentException("graceNodes must not be negative, got: " + graceNodes);
        if (!(initialFreeFraction > 0 && initialFreeFraction < 1)) {
            throw new IllegalArgumentException("initialFreeFraction must be strictly between 0 and 1, got: " + initialFreeFraction);
        }
        if (repairFailures <= 0) throw new IllegalArgumentException("repairFailures must be positive, got: " + repairFailures);
        if (repairNodes <= 0) throw new IllegalArgumentException("repairNodes must be positive, got: " + repairNodes);
        if (share <= 0) throw new IllegalArgumentException("share must be positive, got: " + share);
        if (probeShare < 0 || probeShare > share) {
            throw new IllegalArgumentException("probeShare must be between 0 and share, got: " + probeShare);
        }
        if (patienceNodes < 0) throw new IllegalArgumentException("patienceNodes must not be negative, got: " + patienceNodes);
        if (patienceFraction < 0) throw new IllegalArgumentException("patienceFraction must not be negative, got: " + patienceFraction);
        generators = List.copyOf(generators);
    }

    /** Whether any neighbourhood run is made at all. */
    public boolean isEnabled() {
        return !generators.isEmpty();
    }
}
