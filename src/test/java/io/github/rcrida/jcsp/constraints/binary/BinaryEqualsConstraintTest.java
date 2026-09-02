package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryEqualsConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable<Integer> left = VARIABLE_FACTORY.create("left");
    Variable<Integer> right = VARIABLE_FACTORY.create("right");
    BinaryEqualsConstraint<Integer> constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryEqualsConstraint.<Integer>builder()
                .left(left)
                .right(right)
                .build();
    }

    @Test
    void isSatisfied_true() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 0)))).isTrue();
    }

    @Test
    void isSatisfied_false() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 1)))).isFalse();
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
        assertThat(constraint.toString()).isEqualTo("<(left, right), left == right>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryEqualsConstraint.of(left, right)).isEqualTo(constraint);
    }

    // --- propagate() ---

    @Test
    void propagate_bothSidesNarrowedToIntersection() {
        // x1∈{1,4}, x2∈{2,4}: a gapped pair bounds-only reasoning would under-prune (bounds
        // intersection [max(1,2), min(4,4)] = [2,4] narrows x1 to {4} but leaves x2 at {2,4},
        // retaining 2 even though it now has no support) -- a real value-level intersection
        // narrows both to {4} directly.
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 4),
                right, DiscreteDomain.of(2, 4));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(left)).isEqualTo(DiscreteDomain.of(4));
        assertThat(result.get().get(right)).isEqualTo(DiscreteDomain.of(4));
    }

    @Test
    void propagate_onlyOneSideNarrows() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2, 3),
                right, DiscreteDomain.of(2, 3));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(left)).isEqualTo(DiscreteDomain.of(2, 3));
        assertThat(result.get()).doesNotContainKey(right);
    }

    @Test
    void propagate_intersectionAlreadyEqual_returnsEmptyMap() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2),
                right, DiscreteDomain.of(1, 2));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_disjointDomains_infeasible() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2),
                right, DiscreteDomain.of(3, 4));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 4),
                right, DiscreteDomain.of(2, 4));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
    }

    @Test
    void propagateWithReasons_infeasible_citesBothSidesExactValueSets() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                left, DiscreteDomain.of(1, 2),
                right, DiscreteDomain.of(3, 4));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                left, Set.of(1, 2), right, Set.of(3, 4))));
    }
}
