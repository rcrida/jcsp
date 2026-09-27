package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ProductPropagation}'s interval algebra and coverage sweep directly. The signed
 * cases are what the two product constraints used to give up on entirely, and the caps and
 * straddles-zero guards are cheaper to pin here than by constructing a solve that reaches them.
 */
class ProductPropagationTest {

    private static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- range -------------------------------------------------------------------------------

    @Test void range_allPositive_matchesProductOfMinsAndMaxes() {
        assertThat(ProductPropagation.range(new double[]{2, 3}, new double[]{4, 5}))
                .containsExactly(6.0, 20.0);
    }

    @Test void range_spanningZero_takesTheExtremeCorners() {
        // [-4,4] * [-4,4] reaches -16..16, which neither product-of-mins (16) nor
        // product-of-maxes (16) alone would ever report.
        assertThat(ProductPropagation.range(new double[]{-4, -4}, new double[]{4, 4}))
                .containsExactly(-16.0, 16.0);
    }

    @Test void range_bothNegative_productIsPositive() {
        // [-4,-2] * [-3,-1] spans 2..12; the naive shortcut would read [12, 2], inverted.
        assertThat(ProductPropagation.range(new double[]{-4, -3}, new double[]{-2, -1}))
                .containsExactly(2.0, 12.0);
    }

    @Test void range_oneNegativeOnePositive_isEntirelyNegative() {
        assertThat(ProductPropagation.range(new double[]{-4, 1}, new double[]{-2, 3}))
                .containsExactly(-12.0, -2.0);
    }

    @Test void range_noFactors_isTheMultiplicativeIdentity() {
        assertThat(ProductPropagation.range(new double[]{}, new double[]{})).containsExactly(1.0, 1.0);
    }

    @Test void rangeExcluding_skipsTheNamedFactor() {
        // Skipping index 0 leaves [1,3] * [2,2] = [2,6].
        assertThat(ProductPropagation.rangeExcluding(new double[]{5, 1, 2}, new double[]{9, 3, 2}, 0))
                .containsExactly(2.0, 6.0);
    }

    // --- straddlesZero -----------------------------------------------------------------------

    @Test void straddlesZero_rangeAcrossZero_isTrue() {
        assertThat(ProductPropagation.straddlesZero(new double[]{-1, 1})).isTrue();
    }

    @Test void straddlesZero_touchingZeroFromAbove_isTrue() {
        assertThat(ProductPropagation.straddlesZero(new double[]{0, 5})).isTrue();
    }

    @Test void straddlesZero_touchingZeroFromBelow_isTrue() {
        assertThat(ProductPropagation.straddlesZero(new double[]{-5, 0})).isTrue();
    }

    @Test void straddlesZero_strictlyPositive_isFalse() {
        assertThat(ProductPropagation.straddlesZero(new double[]{2, 5})).isFalse();
    }

    @Test void straddlesZero_strictlyNegative_isFalse() {
        assertThat(ProductPropagation.straddlesZero(new double[]{-5, -2})).isFalse();
    }

    // --- divide ------------------------------------------------------------------------------

    @Test void divide_positiveDenominator_ordersTheQuotientsCorrectly() {
        assertThat(ProductPropagation.divide(6, 12, 2, 3)).containsExactly(2.0, 6.0);
    }

    @Test void divide_negativeDenominator_flipsTheOrdering() {
        assertThat(ProductPropagation.divide(6, 12, -3, -2)).containsExactly(-6.0, -2.0);
    }

    // --- eqCoverageEligible ------------------------------------------------------------------

    @Test void eqCoverageEligible_smallDiscreteDomains_isTrue() {
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        assertThat(ProductPropagation.eqCoverageEligible(List.of(x, y),
                domains(x, IntRangeDomain.of(-1, 1), y, IntRangeDomain.of(-1, 1)))).isTrue();
    }

    @Test void eqCoverageEligible_continuousDomain_isFalse() {
        Variable<Double> x = F.create("cx");
        assertThat(ProductPropagation.eqCoverageEligible(List.of(x),
                domains(x, IntervalDomain.of(0.0, 5.0)))).isFalse();
    }

    @Test void eqCoverageEligible_combinationsOverTheCap_isFalse() {
        // 40 * 40 * 40 = 64,000 combinations, past MAX_COVERAGE_COMBINATIONS.
        Variable<Integer> x = F.create("bx");
        Variable<Integer> y = F.create("by");
        Variable<Integer> z = F.create("bz");
        assertThat(ProductPropagation.eqCoverageEligible(List.of(x, y, z),
                domains(x, IntRangeDomain.of(0, 39), y, IntRangeDomain.of(0, 39), z, IntRangeDomain.of(0, 39))))
                .isFalse();
    }

    // --- eqCoverage --------------------------------------------------------------------------

    @Test void eqCoverage_signProduct_keepsOnlyTheSupportedValues() {
        // x * y == 1 over {-1,1} keeps every value of both factors (1*1 and -1*-1 both reach 1),
        // and reports 1 as the only reachable product.
        Variable<Integer> x = F.create("sx");
        Variable<Integer> y = F.create("sy");
        var coverage = ProductPropagation.eqCoverage(List.of(x, y),
                domains(x, IntRangeDomain.of(-1, 1), y, IntRangeDomain.of(-1, 1)),
                product -> product == 1.0);
        assertThat(coverage).isNotNull();
        assertThat(coverage.reachableProducts()).containsExactly(1.0);
        assertThat(coverage.supportedFactorValues().get(0)).containsExactlyInAnyOrder(-1, 1);
    }

    @Test void eqCoverage_targetForcesOneCombination_prunesTheOthers() {
        // x * y == 6 over {1,2,3} supports only (2,3) and (3,2), so 1 loses support on both sides.
        Variable<Integer> x = F.create("px");
        Variable<Integer> y = F.create("py");
        var coverage = ProductPropagation.eqCoverage(List.of(x, y),
                domains(x, IntRangeDomain.of(1, 3), y, IntRangeDomain.of(1, 3)),
                product -> product == 6.0);
        assertThat(coverage).isNotNull();
        assertThat(coverage.supportedFactorValues().get(0)).containsExactlyInAnyOrder(2, 3);
        assertThat(coverage.supportedFactorValues().get(1)).containsExactlyInAnyOrder(2, 3);
    }

    @Test void eqCoverage_noCombinationReachesTheTarget_isNull() {
        Variable<Integer> x = F.create("nx");
        Variable<Integer> y = F.create("ny");
        assertThat(ProductPropagation.eqCoverage(List.of(x, y),
                domains(x, IntRangeDomain.of(1, 2), y, IntRangeDomain.of(1, 2)),
                product -> product == 7.0)).isNull();
    }

    // --- retain ------------------------------------------------------------------------------

    @Test void retain_nothingRemoved_isNullSoTheCallerPublishesNothing() {
        DiscreteDomain<Integer> domain = IntRangeDomain.of(1, 3);
        assertThat(ProductPropagation.retain(domain, Set.of(1, 2, 3))).isNull();
    }

    @Test void retain_someRemoved_returnsTheNarrowedDomain() {
        DiscreteDomain<Integer> domain = IntRangeDomain.of(1, 3);
        Domain<Integer> narrowed = ProductPropagation.retain(domain, Set.of(2));
        assertThat(narrowed).isNotNull();
        assertThat(narrowed.contains(2)).isTrue();
        assertThat(narrowed.contains(1)).isFalse();
        assertThat(narrowed.contains(3)).isFalse();
    }

    @Test void putIfNarrowed_nullDomain_leavesTheMapAlone() {
        Variable<Integer> x = F.create("qx");
        Map<Variable<?>, Domain<?>> updated = ProductPropagation.updates();
        ProductPropagation.putIfNarrowed(updated, x, null);
        assertThat(updated).isEmpty();
    }

    private static Map<Variable<?>, Domain<?>> domains(Object... pairs) {
        Map<Variable<?>, Domain<?>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Variable<?>) pairs[i], (Domain<?>) pairs[i + 1]);
        }
        return map;
    }
}
