package io.github.rcrida.jcsp.solver;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RestartRandomizationTest {

    /** The first few draws of the {@code restartIndex}-th restart's {@link Random}, as a comparable value. */
    private static List<Integer> stream(RestartRandomization randomization, int restarts) {
        val draws = new ArrayList<Integer>();
        for (int k = 1; k <= restarts; k++) {
            Random random = randomization.randomFor(k);
            draws.add(random == null ? null : random.nextInt(1_000_000));
        }
        return draws;
    }

    @Test
    void sameSeed_sameSearch_drawsTheSameStream() {
        assertThat(stream(RestartRandomization.seeded(42L).forSearch(1), 5))
                .isEqualTo(stream(RestartRandomization.seeded(42L).forSearch(1), 5));
    }

    @Test
    void differentSearches_drawDifferentStreams() {
        // Cross-search diversity: the probes have to explore differently from each other and from the
        // first-solution search, or they are correlated retries of one descent.
        val first = stream(RestartRandomization.seeded(42L).forSearch(1), 5);
        val second = stream(RestartRandomization.seeded(42L).forSearch(2), 5);
        val base = stream(RestartRandomization.seeded(42L), 5);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(base);
    }

    @Test
    void aLaterSearchIsUnaffectedByHowMuchEarlierSearchesDrew() {
        // The whole point of forSearch. The driver is stateful, so without a search identity what a
        // search gets depends on how many draws preceded it -- which made two searches in one solve
        // incomparable to each other and to the same search run alone (ADR-0015).
        val undisturbed = RestartRandomization.seeded(42L);
        val disturbed = RestartRandomization.seeded(42L);
        stream(disturbed, 37);

        assertThat(stream(disturbed.forSearch(3), 5))
                .isEqualTo(stream(undisturbed.forSearch(3), 5));
    }

    @Test
    void searchZero_isTheConfiguredRandomizationItself() {
        // So a solve that runs one search -- every satisfaction solve, and branch-and-bound's own
        // first-solution search -- behaves exactly as it did before forSearch existed.
        val seeded = RestartRandomization.seeded(42L);

        assertThat(seeded.forSearch(0)).isSameAs(seeded);
    }

    @Test
    void none_staysInertForEverySearch() {
        assertThat(RestartRandomization.NONE.forSearch(4).randomFor(1)).isNull();
    }

    @Test
    void aCallerSuppliedImplementation_inheritsTheSharedStreamDefault() {
        // The default returns this, so adding forSearch did not change what an existing lambda does.
        RestartRandomization fixed = restartIndex -> new Random(restartIndex);

        assertThat(fixed.forSearch(9)).isSameAs(fixed);
    }
}
