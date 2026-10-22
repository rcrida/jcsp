package io.github.rcrida.jcsp.solver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestartNogoodsTest {

    @Test
    void noneRecordsNothing() {
        assertThat(RestartNogoods.NONE.isEnabled()).isFalse();
        assertThat(RestartNogoods.NONE.isAdaptive()).isFalse();
    }

    @Test
    void theAdaptivePresetRecordsShortNogoodsAndJudgesThem() {
        assertThat(RestartNogoods.ADAPTIVE.isEnabled()).isTrue();
        assertThat(RestartNogoods.ADAPTIVE.isAdaptive()).isTrue();
        assertThat(RestartNogoods.ADAPTIVE.maxArity()).isEqualTo(8);
        assertThat(RestartNogoods.ADAPTIVE.window()).isEqualTo(10_000);
        assertThat(RestartNogoods.ADAPTIVE.minHitRate()).isEqualTo(0.01);
    }

    @Test
    void alwaysRecordsForTheWholeSearch() {
        var always = RestartNogoods.always(5);

        assertThat(always.isEnabled()).isTrue();
        assertThat(always.isAdaptive()).isFalse();
        assertThat(always.maxArity()).isEqualTo(5);
    }

    @Test
    void adaptiveCarriesItsWindowAndThreshold() {
        var adaptive = RestartNogoods.adaptive(3, 100, 0.5);

        assertThat(adaptive.isAdaptive()).isTrue();
        assertThat(adaptive.maxArity()).isEqualTo(3);
        assertThat(adaptive.window()).isEqualTo(100);
        assertThat(adaptive.minHitRate()).isEqualTo(0.5);
    }

    @Test
    void aNonPositiveWindowANegativeArityOrANegativeRateIsRejected() {
        assertThatThrownBy(() -> new RestartNogoods(-1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RestartNogoods(1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RestartNogoods(1, 1, -0.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
