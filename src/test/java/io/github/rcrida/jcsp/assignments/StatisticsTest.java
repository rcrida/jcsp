package io.github.rcrida.jcsp.assignments;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsTest {
    @Test
    void initialCountersAreZero() {
        val statistics = new Statistics();
        assertThat(statistics.getNodesExplored().get()).isZero();
        assertThat(statistics.getConstraintChecks().get()).isZero();
    }

    @Test
    void initialCurrentSearchSpaceIsEmpty() {
        val statistics = new Statistics();
        assertThat(statistics.getCurrentSearchSpace()).isEmpty();
    }

    @Test
    void updateCurrentSearchSpace() {
        val statistics = new Statistics();
        statistics.updateCurrentSearchSpace(BigInteger.valueOf(42));
        assertThat(statistics.getCurrentSearchSpace()).contains(BigInteger.valueOf(42));
    }

    @Test
    void updateCurrentSearchSpace_overwritesNotAccumulates() {
        val statistics = new Statistics();
        statistics.updateCurrentSearchSpace(BigInteger.valueOf(100));
        statistics.updateCurrentSearchSpace(BigInteger.valueOf(7));
        assertThat(statistics.getCurrentSearchSpace()).contains(BigInteger.valueOf(7));
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
    void incrementBacktracks() {
        val statistics = new Statistics();
        statistics.incrementBacktracks();
        assertThat(statistics.getBacktracks().get()).isEqualTo(1);
    }

    @Test
    void addRestarts() {
        val statistics = new Statistics();
        statistics.addRestarts(3);
        assertThat(statistics.getRestarts().get()).isEqualTo(3);
    }

    @Test
    void incrementSteps() {
        val statistics = new Statistics();
        statistics.incrementSteps();
        statistics.incrementSteps();
        assertThat(statistics.getSteps().get()).isEqualTo(2);
    }

    @Test
    void incrementNogoodsLearned() {
        val statistics = new Statistics();
        statistics.incrementNogoodsLearned();
        assertThat(statistics.getNogoodsLearned().get()).isEqualTo(1);
    }

    @Test
    void incrementNogoodRejections() {
        val statistics = new Statistics();
        statistics.incrementNogoodRejections();
        statistics.incrementNogoodRejections();
        assertThat(statistics.getNogoodRejections().get()).isEqualTo(2);
    }

    @Test
    void add() {
        val a = new Statistics();
        a.incrementNodesExplored();
        a.incrementBacktracks();
        a.incrementSteps();
        a.incrementNogoodsLearned();
        a.incrementNogoodRejections();
        val b = new Statistics();
        b.incrementConstraintChecks();
        b.incrementConstraintChecks();
        b.addRestarts(2);
        b.updateCurrentSearchSpace(BigInteger.valueOf(99));
        a.add(b);
        assertThat(a.getNodesExplored().get()).isEqualTo(1);
        assertThat(a.getConstraintChecks().get()).isEqualTo(2);
        assertThat(a.getBacktracks().get()).isEqualTo(1);
        assertThat(a.getRestarts().get()).isEqualTo(2);
        assertThat(a.getSteps().get()).isEqualTo(1);
        assertThat(a.getNogoodsLearned().get()).isEqualTo(1);
        assertThat(a.getNogoodRejections().get()).isEqualTo(1);
        assertThat(a.getCurrentSearchSpace()).contains(BigInteger.valueOf(99));
    }

    @Test
    void add_otherHasNoCurrentSearchSpace_leavesExistingValueUnchanged() {
        val a = new Statistics();
        a.updateCurrentSearchSpace(BigInteger.valueOf(5));
        val b = new Statistics();
        a.add(b);
        assertThat(a.getCurrentSearchSpace()).contains(BigInteger.valueOf(5));
    }
}
