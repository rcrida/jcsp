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
 *   <li>{@link #currentSearchSpace} — unlike every field above, a single overwritten slot, not an
 *       accumulator: {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem#getSearchSpace()}
 *       of whatever live, propagation-narrowed problem search was working on at the exact point a
 *       {@link io.github.rcrida.jcsp.solver.Cancellation} was detected (node/time limit or an
 *       external stop signal) and search actually stopped because of it. {@code null} until that
 *       happens — a completed solve (found a solution, proved UNSAT, or genuinely exhausted the
 *       search) never touches it, since {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem#getSearchSpace()}
 *       on the original, undecomposed problem already answers "how big was this problem" for that
 *       case. Lets a caller see how much smaller the search space had actually become by the time
 *       an incomplete solve gave up, as opposed to the always-unchanged size the original
 *       (immutable) problem itself reports. Excluded from {@code toString()} ({@code
 *       @ToString.Exclude}) since {@code Xcsp3ProblemRunner} (and any other caller) already has
 *       its own dedicated, clearer place to report it (a separate {@code c search-space-after:}
 *       line) rather than folding a potentially very large number into the same line as the other
 *       seven counters.</li>
 * </ul>
 */
@Value
public class Statistics {
    AtomicLong nodesExplored = new AtomicLong();
    AtomicLong constraintChecks = new AtomicLong();
    AtomicLong backtracks = new AtomicLong();
    AtomicLong restarts = new AtomicLong();
    AtomicLong steps = new AtomicLong();
    AtomicLong nogoodsLearned = new AtomicLong();
    AtomicLong nogoodRejections = new AtomicLong();
    @Getter(AccessLevel.NONE) @ToString.Exclude AtomicReference<BigInteger> currentSearchSpace = new AtomicReference<>();

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
     * {@link Optional#empty()} until {@link #updateCurrentSearchSpace} is called at least once —
     * see that method's own Javadoc, and {@link #currentSearchSpace}'s class-level bullet, for when
     * that happens. A hand-written accessor (the field itself suppresses {@code @Value}'s usual
     * generated getter via {@code @Getter(AccessLevel.NONE)}) rather than exposing the backing
     * {@link AtomicReference} directly the way every other field's generated getter does — this one
     * field is nullable-by-design rather than always-meaningful, so {@link Optional} is the honest
     * shape for callers.
     */
    public Optional<BigInteger> getCurrentSearchSpace() {
        return Optional.ofNullable(currentSearchSpace.get());
    }

    /**
     * Overwrites {@link #currentSearchSpace} with {@code searchSpace} — unlike every other mutator
     * on this class, not additive. Called exactly once per solve, at whichever cancellation-check
     * site first observes {@link io.github.rcrida.jcsp.solver.Cancellation#isCancelled()} and stops
     * search because of it.
     */
    public void updateCurrentSearchSpace(BigInteger searchSpace) {
        currentSearchSpace.set(searchSpace);
    }

    void add(Statistics other) {
        nodesExplored.addAndGet(other.nodesExplored.get());
        constraintChecks.addAndGet(other.constraintChecks.get());
        backtracks.addAndGet(other.backtracks.get());
        restarts.addAndGet(other.restarts.get());
        steps.addAndGet(other.steps.get());
        nogoodsLearned.addAndGet(other.nogoodsLearned.get());
        nogoodRejections.addAndGet(other.nogoodRejections.get());
        // Not additive like the seven counters above -- summing two search-space sizes is
        // meaningless. Last-known-non-null-wins instead.
        if (other.currentSearchSpace.get() != null) {
            currentSearchSpace.set(other.currentSearchSpace.get());
        }
    }
}
