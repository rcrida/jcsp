package io.github.rcrida.jcsp.assignments;

import lombok.Value;

import java.util.concurrent.atomic.AtomicInteger;

@Value
public class Statistics {
    AtomicInteger nodesExplored = new AtomicInteger();
    AtomicInteger constraintChecks = new AtomicInteger();

    void incrementNodesExplored() {
        nodesExplored.incrementAndGet();
    }

    void incrementConstraintChecks() {
        constraintChecks.incrementAndGet();
    }

    void add(Statistics other) {
        nodesExplored.addAndGet(other.nodesExplored.get());
        constraintChecks.addAndGet(other.constraintChecks.get());
    }
}
