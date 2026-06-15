package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntervalDomainTest {

    @Test
    void of_invalid_throwsAssertionError() {
        assertThatThrownBy(() -> IntervalDomain.of(10.0, 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessage("min (10.0) must be less than or equal to max (5.0)");
    }

    @Test
    void contains_valuesWithinRange() {
        var d = IntervalDomain.of(1.0, 5.0);
        assertThat(d.contains(1.0)).isTrue();
        assertThat(d.contains(5.0)).isTrue();
        assertThat(d.contains(3.5)).isTrue();
        assertThat(d.contains(0.9)).isFalse();
        assertThat(d.contains(5.1)).isFalse();
    }

    @Test
    void contains_nonNumber_returnsFalse() {
        var d = IntervalDomain.of(1.0, 5.0);
        assertThat(d.contains("not a number")).isFalse();
    }

    @Test
    void isEmpty_falseForValidRange() {
        assertThat(IntervalDomain.of(1.0, 5.0).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_trueWhenMinExceedsMax() {
        var d = IntervalDomain.of(1.0, 5.0).withBounds(10.0, 0.0);
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void size_nonSingletonReturnsMaxValue() {
        assertThat(IntervalDomain.of(1.0, 5.0).size()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void size_singletonReturnsOne() {
        assertThat(IntervalDomain.of(3.0, 3.0).size()).isEqualTo(1);
    }

    @Test
    void isSingleton_trueWhenMinEqualsMax() {
        assertThat(IntervalDomain.of(3.0, 3.0).isSingleton()).isTrue();
        assertThat(IntervalDomain.of(1.0, 5.0).isSingleton()).isFalse();
    }

    @Test
    void singleValue_presentOnlyForSingleton() {
        assertThat(IntervalDomain.of(3.0, 3.0).singleValue()).contains(3.0);
        assertThat(IntervalDomain.of(1.0, 5.0).singleValue()).isEmpty();
    }

    @Test
    void withBounds_narrowsToIntersection() {
        var d = IntervalDomain.of(1.0, 10.0).withBounds(3.0, 7.0);
        assertThat(d).isInstanceOf(BoundedDomain.class);
        var bd = (BoundedDomain<Double>) d;
        assertThat(bd.getMin()).isEqualTo(3.0);
        assertThat(bd.getMax()).isEqualTo(7.0);
    }

    @Test
    void withBounds_widerBoundsDoNotExpandDomain() {
        var d = (BoundedDomain<Double>) IntervalDomain.of(3.0, 7.0).withBounds(0.0, 10.0);
        assertThat(d.getMin()).isEqualTo(3.0);
        assertThat(d.getMax()).isEqualTo(7.0);
    }

    @Test
    void getMinAndMax() {
        var d = IntervalDomain.of(1.0, 5.0);
        assertThat(d.getMin()).isEqualTo(1.0);
        assertThat(d.getMax()).isEqualTo(5.0);
    }

    @Test
    void testToString() {
        assertThat(IntervalDomain.of(1.0, 5.0).toString()).isEqualTo("[1.0, 5.0]");
    }

    @Test
    void stream_throwsUnsupportedOperationException() {
        var d = IntervalDomain.of(1.0, 5.0);
        assertThatThrownBy(d::stream).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toBuilder_throwsUnsupportedOperationException() {
        var d = IntervalDomain.of(1.0, 5.0);
        assertThatThrownBy(d::toBuilder).isInstanceOf(UnsupportedOperationException.class);
    }
}
