package io.github.rcrida.jcsp.assignments;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Search statistics accumulated during a solve. Counters are thread-safe ({@link AtomicLong} --
 * switched from {@code AtomicInteger} after a real run overflowed it) and shared across all
 * {@link Assignment} objects derived from the same root via
 * {@link Assignment#withValue}, so a single instance reflects the full cost of a search.
 * <p>
 * Every accessor returns a plain {@code long}, not the backing {@link AtomicLong}: the mutable
 * counter is an implementation detail, and returning it handed callers the ability to {@code set}
 * or {@code decrement} a counter this class means to be append-only. Keeping the representation
 * private is also what lets it change without touching this class's published surface again.
 * <p>
 * {@link AtomicLong} deliberately, despite {@link #constraintChecks} being written tens of millions
 * of times per solve. {@link java.util.concurrent.atomic.LongAdder} is no cheaper here (an
 * uncontended {@code add} is a compare-and-swap loop against {@link AtomicLong#incrementAndGet}'s
 * single fetch-and-add, its striping needs contention the backtracking chain never creates, and
 * {@code sum} would slow {@link #getNodesExplored}, read once per search node by {@code
 * SolverLimits#checkStop}). Batching the count per {@link Assignment#isConsistentAmong} call --
 * a 70x reduction in atomic operations -- was implemented and measured as neutral on every workload
 * tried, including the paths that genuinely share one instance across threads, and reverted; a
 * {@code try}/{@code finally} variant of it was ~2% <em>worse</em>. This counter has never appeared
 * in a JFR profile: the operation count is large, the cost is not.
 *
 * <ul>
 *   <li>{@link #nodesExplored} — variable assignments attempted (incremented by {@link Assignment#withValue}),
 *       plus, since {@link #incrementNodesExplored} is public, {@link
 *       io.github.rcrida.jcsp.solver.SetBranchingSolver}'s own branch steps (which never build an
 *       {@link Assignment}), so this reflects the whole solve's search effort, not just the
 *       terminal solver's part</li>
 *   <li>{@link #constraintChecks} — constraint evaluations performed (incremented by {@link Assignment#isConsistent})</li>
 *   <li>{@link #backtracks} — times tree search reversed a value assignment due to inconsistency or domain wipeout</li>
 *   <li>{@link #restarts} — completed Luby restarts (backtracking search only), recorded as each one
 *       happens rather than batched up only once a solution is found, so it reflects restarts
 *       completed so far even when a solve ends via a limit, cancellation, or genuine UNSAT</li>
 *   <li>{@link #steps} — local search moves taken to reach the solution (local search solvers only)</li>
 *   <li>{@link #nogoodsLearned} — nogoods recorded after a domain-wipeout during search (backtracking search only)</li>
 *   <li>{@link #nogoodRejections} — times a learned {@link io.github.rcrida.jcsp.constraints.nary.NogoodConstraint}
 *       specifically (not some other constraint) was the one that caused a rejection, counted at
 *       both of its two detection sites: a direct {@code isSatisfiedBy} violation ({@link
 *       Assignment#isConsistentAmong}) and a propagation-detected domain wipeout ({@link
 *       io.github.rcrida.jcsp.solver.FixpointPropagation#applyFixpointWithReason}'s {@code
 *       NogoodFixpointConsistency} entry). A purely additive, observational counter — it doesn't
 *       change how a nogood-caused rejection is treated by search itself (still folded into
 *       {@link #backtracks} exactly like any other constraint's rejection, per {@link
 *       io.github.rcrida.jcsp.assignments.NogoodStore}'s own design), only how much of that total
 *       is attributable to CDCL specifically.</li>
 *   <li>{@link #rootSearchSpace} — the whole problem's search space once the chain's one-time
 *       preprocessing fixpoint converged, recorded before any search node is explored, so it is
 *       stable across runs of the same instance and answers "how much did propagation shrink this
 *       problem". Written by {@link io.github.rcrida.jcsp.solver.PropagationFixpointSolver}, with
 *       {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch} recording its own root as a fallback
 *       for a directly-constructed solver that never ran the chain's preprocessing.</li>
 *   <li>{@link #remainingSearchSpace} — {@link #rootSearchSpace} scaled by the unexplored fraction
 *       of the depth-first descent in progress when an incomplete solve stopped. Recorded by both
 *       terminal solvers: {@code DomWdegLubySearch} accumulates directly in its recursion, and
 *       {@code BranchAndBoundSolver}, whose search is a lazy {@link java.util.stream.Stream},
 *       attaches the same accounting to each child sub-stream's {@link
 *       java.util.stream.Stream#onClose} — {@code flatMap} consumes a mapped stream fully and then
 *       closes it, so that fires exactly when a child subtree is finished with. On the satisfaction
 *       chain it is scoped to the restart in progress, since restarts re-descend from the root and
 *       keep no record of what earlier ones refuted, so their explored fractions overlap and cannot
 *       be summed.</li>
 * </ul>
 */
@Value
public class Statistics {
    @Getter(AccessLevel.NONE) AtomicLong nodesExplored = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong constraintChecks = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong backtracks = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong restarts = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong steps = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong nogoodsLearned = new AtomicLong();
    @Getter(AccessLevel.NONE) AtomicLong nogoodRejections = new AtomicLong();
    @Getter(AccessLevel.NONE) @ToString.Exclude AtomicReference<BigInteger> rootSearchSpace = new AtomicReference<>();
    @Getter(AccessLevel.NONE) @ToString.Exclude AtomicReference<BigInteger> remainingSearchSpace = new AtomicReference<>();

    public long getNodesExplored() {
        return nodesExplored.get();
    }

    public long getConstraintChecks() {
        return constraintChecks.get();
    }

    public long getBacktracks() {
        return backtracks.get();
    }

    public long getRestarts() {
        return restarts.get();
    }

    public long getSteps() {
        return steps.get();
    }

    public long getNogoodsLearned() {
        return nogoodsLearned.get();
    }

    public long getNogoodRejections() {
        return nogoodRejections.get();
    }

    public void incrementNodesExplored() {
        nodesExplored.incrementAndGet();
    }

    void incrementConstraintChecks() {
        constraintChecks.incrementAndGet();
    }

    public void incrementBacktracks() {
        backtracks.incrementAndGet();
    }

    public void addRestarts(int n) {
        restarts.addAndGet(n);
    }

    public void incrementSteps() {
        steps.incrementAndGet();
    }

    public void incrementNogoodsLearned() {
        nogoodsLearned.incrementAndGet();
    }

    public void incrementNogoodRejections() {
        nogoodRejections.incrementAndGet();
    }

    /**
     * Search space of the whole problem once the chain's one-time preprocessing fixpoint has
     * converged, before any search node is explored — {@link Optional#empty()} if preprocessing
     * never completed (it proved infeasibility, or was cancelled).
     * <p>
     * It does not depend on where a solve happened to be interrupted, so it is stable across runs
     * of the same instance and is the honest answer to "how much did propagation shrink this
     * problem".
     */
    public Optional<BigInteger> getRootSearchSpace() {
        return Optional.ofNullable(rootSearchSpace.get());
    }

    /** Records {@link #getRootSearchSpace}; first write wins, so a decomposed subproblem solved
     *  later cannot overwrite the whole problem's figure. */
    public void updateRootSearchSpace(BigInteger searchSpace) {
        rootSearchSpace.compareAndSet(null, searchSpace);
    }

    /**
     * Estimated search space still unexplored by the restart in progress when an incomplete solve
     * stopped, or {@link Optional#empty()} when no such estimate was recorded.
     * <p>
     * Scoped to the current restart, not the whole solve: a restart abandons its subtree and
     * re-descends from the root, and with nogood learning off (the default — see
     * {@code docs/adr/0030-nogood-learning-is-not-cdcl.md}) nothing records which regions earlier
     * restarts refuted, so the explored fractions of separate restarts overlap and cannot be summed.
     * Read it as "how far into this descent the search had got", not as total work remaining.
     */
    public Optional<BigInteger> getRemainingSearchSpace() {
        return Optional.ofNullable(remainingSearchSpace.get());
    }

    /**
     * Records {@link #getRemainingSearchSpace}; first write wins. Cancellation is signalled by an
     * empty {@code Stream} step rather than an exception, so the enclosing lazy stream keeps
     * re-checking every remaining sibling as the recursion unwinds, and each re-check would record
     * again, from deep (narrow, meaningful) states back toward shallow ones: a plain overwrite would
     * let the last, least useful one clobber the state at the moment of cancellation. Compare-and-set
     * keeps the first, deepest snapshot, and is thread-safe for {@link
     * io.github.rcrida.jcsp.solver.IndependentSubproblemSolver} subproblems sharing one instance.
     */
    public void updateRemainingSearchSpace(BigInteger searchSpace) {
        remainingSearchSpace.compareAndSet(null, searchSpace);
    }

    /**
     * Clears {@link #getRemainingSearchSpace}, so that the next write wins rather than being
     * discarded.
     * <p>
     * For a solve with an earlier phase that records snapshots of a search this figure is not
     * documented to describe: {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver}'s incumbent
     * seeder runs its own feasibility searches against this same instance, and under first-write-wins
     * a cancelled probe's subtree would stand in for the branch-and-bound search's own. Deliberately
     * does not touch {@link #getRootSearchSpace}, which is the whole problem's post-preprocessing
     * size and the same figure whatever phase recorded it.
     */
    public void clearSearchSpaceSnapshots() {
        remainingSearchSpace.set(null);
    }

    void add(Statistics other) {
        nodesExplored.addAndGet(other.nodesExplored.get());
        constraintChecks.addAndGet(other.constraintChecks.get());
        backtracks.addAndGet(other.backtracks.get());
        restarts.addAndGet(other.restarts.get());
        steps.addAndGet(other.steps.get());
        nogoodsLearned.addAndGet(other.nogoodsLearned.get());
        nogoodRejections.addAndGet(other.nogoodRejections.get());
    }
}
