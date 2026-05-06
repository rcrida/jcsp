package io.github.rcrida.jcsp.assignments;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsTest {
    @Test
    void initialCountersAreZero() {
        val statistics = new Statistics();
        assertThat(statistics.getNodesExplored().get()).isZero();
        assertThat(statistics.getConstraintChecks().get()).isZero();
    }

    @Test
    void incrementNodesExplored() {
        val statistics = new Statistics();
        statistics.incrementNodesExplored();
        assertThat(statistics.getNodesExplored().get()).isEqualTo(1);
    }

    @Test
    void incrementConstraintChecks() {
        val statistics = new Statistics();
        statistics.incrementConstraintChecks();
        assertThat(statistics.getConstraintChecks().get()).isEqualTo(1);
    }

    @Test
    void add() {
        val a = new Statistics();
        a.incrementNodesExplored();
        val b = new Statistics();
        b.incrementConstraintChecks();
        b.incrementConstraintChecks();
        a.add(b);
        assertThat(a.getNodesExplored().get()).isEqualTo(1);
        assertThat(a.getConstraintChecks().get()).isEqualTo(2);
    }
}
