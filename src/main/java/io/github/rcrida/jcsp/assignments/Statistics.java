package io.github.rcrida.jcsp.assignments;

import lombok.Value;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Search statistics accumulated during a solve. Counters are thread-safe ({@link AtomicInteger})
 * and shared across all {@link Assignment} objects derived from the same root via
 * {@link Assignment#withValue}, so a single instance reflects the full cost of a search.
 *
 * <ul>
 *   <li>{@code nodesExplored} — variable assignments attempted (incremented by {@link Assignment#withValue})</li>
 *   <li>{@code constraintChecks} — constraint evaluations performed (incremented by {@link Assignment#isConsistent})</li>
 *   <li>{@code backtracks} — times tree search reversed a value assignment due to inconsistency or domain wipeout</li>
 *   <li>{@code restarts} — completed Luby restarts before a solution was found (backtracking search only)</li>
 *   <li>{@code steps} — local search moves taken to reach the solution (local search solvers only)</li>
 *   <li>{@code nogoodsLearned} — nogoods recorded after a domain-wipeout during search (backtracking search only)</li>
 *   <li>{@code nogoodPrunes} — candidate assignments skipped because a learned nogood subsumed them (backtracking search only)</li>
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
    AtomicInteger nogoodPrunes = new AtomicInteger();

    void incrementNodesExplored() {
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

    public void incrementNogoodPrunes() {
        nogoodPrunes.incrementAndGet();
    }

    void add(Statistics other) {
        nodesExplored.addAndGet(other.nodesExplored.get());
        constraintChecks.addAndGet(other.constraintChecks.get());
        backtracks.addAndGet(other.backtracks.get());
        restarts.addAndGet(other.restarts.get());
        steps.addAndGet(other.steps.get());
        nogoodsLearned.addAndGet(other.nogoodsLearned.get());
        nogoodPrunes.addAndGet(other.nogoodPrunes.get());
    }
}
