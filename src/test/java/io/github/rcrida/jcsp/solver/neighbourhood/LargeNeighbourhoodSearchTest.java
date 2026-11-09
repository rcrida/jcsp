package io.github.rcrida.jcsp.solver.neighbourhood;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LargeNeighbourhoodSearchTest {
    private static final List<NeighbourhoodGenerator.Factory> ONE = List.of(NeighbourhoodGenerator.Factory.RANDOM);

    private static LargeNeighbourhoodSearch with(long grace, double fraction, long failures, long nodes, double share,
                                                 double probe, long patience, double patienceFraction) {
        return new LargeNeighbourhoodSearch(grace, fraction, failures, nodes, share, probe, patience, patienceFraction, ONE);
    }

    @Test
    void offMakesNoNeighbourhoodRuns() {
        assertThat(LargeNeighbourhoodSearch.OFF.isEnabled()).isFalse();
        assertThat(LargeNeighbourhoodSearch.OFF.generators()).isEmpty();
    }

    @Test
    void theDefaultDrawsBetweenTheRandomAndTheCostWeightedGenerators() {
        LargeNeighbourhoodSearch standard = LargeNeighbourhoodSearch.DEFAULT;

        assertThat(standard.isEnabled()).isTrue();
        assertThat(standard.generators())
                .containsExactly(NeighbourhoodGenerator.Factory.RANDOM, NeighbourhoodGenerator.Factory.COST_WEIGHTED);
        assertThat(standard.graceNodes()).isEqualTo(50_000);
        assertThat(standard.initialFreeFraction()).isEqualTo(0.05);
        assertThat(standard.repairFailures()).isEqualTo(200);
        assertThat(standard.repairNodes()).isEqualTo(10_000);
        assertThat(standard.share()).isEqualTo(1.0);
        assertThat(standard.probeShare()).isEqualTo(0.05);
        assertThat(standard.patienceNodes()).isEqualTo(20_000);
        assertThat(standard.patienceFraction()).isEqualTo(0.1);
    }

    @Test
    void anyGeneratorEnablesIt() {
        assertThat(with(0, 0.5, 1, 1, 1.0, 0.0, 0, 0).isEnabled()).isTrue();
    }

    @Test
    void theGeneratorListIsCopied() {
        List<NeighbourhoodGenerator.Factory> mutable = new ArrayList<>(ONE);
        LargeNeighbourhoodSearch search = new LargeNeighbourhoodSearch(0, 0.5, 1, 1, 1.0, 0.0, 0, 0, mutable);

        mutable.add(NeighbourhoodGenerator.Factory.COST_WEIGHTED);

        assertThat(search.generators()).hasSize(1);
        assertThatThrownBy(() -> search.generators().add(NeighbourhoodGenerator.Factory.COST_WEIGHTED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsANegativeGrace() {
        assertThatThrownBy(() -> with(-1, 0.5, 1, 1, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("graceNodes");
    }

    @Test
    void rejectsAFreeFractionOutsideTheOpenUnitInterval() {
        assertThatThrownBy(() -> with(0, 0.0, 1, 1, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("initialFreeFraction");
        assertThatThrownBy(() -> with(0, 1.0, 1, 1, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("initialFreeFraction");
        assertThatThrownBy(() -> with(0, Double.NaN, 1, 1, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("initialFreeFraction");
    }

    @Test
    void rejectsACutoffThatCannotBeReached() {
        assertThatThrownBy(() -> with(0, 0.5, 0, 1, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repairFailures");
        assertThatThrownBy(() -> with(0, 0.5, 1, 0, 1.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repairNodes");
    }

    @Test
    void rejectsSharesThatMakeNoSense() {
        assertThatThrownBy(() -> with(0, 0.5, 1, 1, 0.0, 0.0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("share must");
        assertThatThrownBy(() -> with(0, 0.5, 1, 1, 1.0, -0.1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("probeShare");
        assertThatThrownBy(() -> with(0, 0.5, 1, 1, 1.0, 1.5, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("probeShare");
    }

    @Test
    void rejectsNegativePatience() {
        assertThatThrownBy(() -> with(0, 0.5, 1, 1, 1.0, 0.0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("patienceNodes");
        assertThatThrownBy(() -> with(0, 0.5, 1, 1, 1.0, 0.0, 0, -0.1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("patienceFraction");
    }
}
