package io.github.rcrida.jcsp.assignments;

import lombok.Value;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Search statistics accumulated during a solve. Counters are thread-safe ({@link AtomicInteger})
 * and shared across all {@link Assignment} objects derived from the same root via
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
 * </ul>
 */
@Value
public class Statistics {
    AtomicInteger nodesExplored = new AtomicInteger();
    AtomicInteger constraintChecks = new AtomicInteger();
    AtomicInteger backtracks = new AtomicInteger();
    AtomicInteger restarts = new AtomicInteger();
    AtomicInteger steps = new AtomicInteger();
    AtomicInteger nogoodsLearned = new AtomicInteger();
    AtomicInteger nogoodRejections = new AtomicInteger();

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
