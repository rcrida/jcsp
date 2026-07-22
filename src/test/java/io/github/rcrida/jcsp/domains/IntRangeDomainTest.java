package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntRangeDomainTest {
    @Test
    void constructInvalid() {
        assertThatThrownBy(() -> IntRangeDomain.of(10, 5))
            .isInstanceOf(AssertionError.class)
            .hasMessage("minInclusive (10) must be less than or equal to maxInclusive (5)");
    }

    @Test
    void contains() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5)).isTrue();
        assertThat(domain.contains(10)).isTrue();
        assertThat(domain.contains(4)).isFalse();
        assertThat(domain.contains(11)).isFalse();
    }

    @Test
    void containsNull() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(null)).isFalse();
    }

    @Test
    void containsInvalid() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.contains(5.5)).isFalse();
        assertThat(domain.contains("5")).isFalse();
    }

    @Test
    void stream() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.stream()).containsOnly(5, 6, 7, 8, 9, 10);
    }

    @Test
    void getMinAndMax_of() {
        IntRangeDomain domain = IntRangeDomain.of(5, 10);
        assertThat(domain.getMin()).isEqualTo(5);
        assertThat(domain.getMax()).isEqualTo(10);
    }

    @Test
    void testToString() {
        assertThat(IntRangeDomain.of(5, 10).toString()).isEqualTo("IntRangeDomain[5..10]");
    }

    @Test
    void constructor_mismatchedMin_throwsAssertionError() {
        // of() can never produce this (it always passes its own minInclusive/maxInclusive
        // straight through), but the canonical constructor is necessarily public -- a record's
        // canonical constructor can't be more restrictive than the record itself -- so the
        // min/max-matches-values invariant is enforced by assertion instead of access control.
        assertThatThrownBy(() -> new IntRangeDomain(Set.of(1, 2, 3), 100, 200))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_mismatchedMax_throwsAssertionError() {
        // Distinct from the mismatched-min case above: min==1 matches, so the short-circuiting
        // && only reaches (and fails) the max comparison here, not the min one.
        assertThatThrownBy(() -> new IntRangeDomain(Set.of(1, 2, 3), 1, 200))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_emptyValues_skipsMinMaxCheck() {
        // Collections.min/max would throw NoSuchElementException on an empty collection; the
        // assert's isEmpty() short-circuit avoids ever evaluating them in that case.
        var domain = new IntRangeDomain(Set.of(), 0, 0);
        assertThat(domain.isEmpty()).isTrue();
    }
}
