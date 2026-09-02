package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryNotEqualsConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable<Integer> left = VARIABLE_FACTORY.create("left");
    Variable<Integer> right = VARIABLE_FACTORY.create("right");
    BinaryNotEqualsConstraint<Integer> constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryNotEqualsConstraint.<Integer>builder()
                .left(left)
                .right(right)
                .build();
    }

    @Test
    void isSatisfied_true() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 1)))).isTrue();
    }

    @Test
    void isSatisfied_false() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 0)))).isFalse();
    }

    @Test
    void isSatisfied_unknowns() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(right, 1)))).isTrue();
    }

    @Test
    void getNeighbour() {
        assertThat(constraint.getNeighbour(left)).isEqualTo(right);
        assertThat(constraint.getNeighbour(right)).isEqualTo(left);
    }

    @Test
    void getNeighbour_incorrect() {
        assertThatThrownBy(() -> constraint.getNeighbour(VARIABLE_FACTORY.create("another")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(left, right), left != right>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryNotEqualsConstraint.of(left, right)).isEqualTo(constraint);
    }

    // --- propagate() ---

    @Test
    void propagate_leftSingleton_deletesFromRightWhenPresent() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1),
                right, DiscreteDomain.of(1, 2, 3));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(right)).isEqualTo(DiscreteDomain.of(2, 3));
        assertThat(result.get()).doesNotContainKey(left);
    }

    @Test
    void propagate_leftSingleton_valueAbsentFromRight_returnsEmptyMap() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1),
                right, DiscreteDomain.of(2, 3));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_rightSingleton_deletesFromLeftWhenPresent() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2, 3),
                right, DiscreteDomain.of(2));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(left)).isEqualTo(DiscreteDomain.of(1, 3));
        assertThat(result.get()).doesNotContainKey(right);
    }

    @Test
    void propagate_neitherSingleton_returnsEmptyMap() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2),
                right, DiscreteDomain.of(1, 2));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_bothSingletonDistinctValues_returnsEmptyMap() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1),
                right, DiscreteDomain.of(2));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_bothSingletonSameValue_infeasible() {
        // The classic x != x conflict: left's own deletion attempt on right empties it.
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(5),
                right, DiscreteDomain.of(5));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1),
                right, DiscreteDomain.of(1, 2, 3));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
    }

    @Test
    void propagateWithReasons_infeasible_bothAlreadySingleton_attributesBothViaGroundReason() {
        // Infeasibility for this constraint can only ever arise when both sides are already
        // singleton with the same value -- deleting one value from a domain that still has 2+
        // values can never empty it. So ValueSetNogoodConstraint#fromCurrentState's own
        // allSingletonReason fast path always fires here, never its value-set fallback (unlike
        // BinaryEqualsConstraint, which can wipe a non-singleton side directly).
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(5),
                right, DiscreteDomain.of(5));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(left, 5, right, 5)));
    }
}
