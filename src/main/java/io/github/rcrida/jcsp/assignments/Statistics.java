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
 * </ul>
 *
 * <p>There is deliberately no separate "nogood prunes" counter: nogoods are modelled as ordinary
 * {@link io.github.rcrida.jcsp.constraints.nary.NogoodConstraint}s that join the same propagation
 * fixpoint as every other constraint (see {@link io.github.rcrida.jcsp.assignments.NogoodStore}),
 * so a candidate rejected because of a learned nogood is architecturally indistinguishable from
 * one rejected by any other constraint — it's simply counted under {@link #backtracks}.
 */
@Value
public class Statistics {
    AtomicInteger nodesExplored = new AtomicInteger();
    AtomicInteger constraintChecks = new AtomicInteger();
    AtomicInteger backtracks = new AtomicInteger();
    AtomicInteger restarts = new AtomicInteger();
    AtomicInteger steps = new AtomicInteger();
    AtomicInteger nogoodsLearned = new AtomicInteger();

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

    void add(Statistics other) {
        nodesExplored.addAndGet(other.nodesExplored.get());
        constraintChecks.addAndGet(other.constraintChecks.get());
        backtracks.addAndGet(other.backtracks.get());
        restarts.addAndGet(other.restarts.get());
        steps.addAndGet(other.steps.get());
        nogoodsLearned.addAndGet(other.nogoodsLearned.get());
    }
}
