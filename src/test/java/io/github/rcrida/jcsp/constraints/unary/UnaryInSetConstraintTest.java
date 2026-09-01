package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryInSetConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x_inset");

    static Assignment a(int x) {
        return Assignment.of(Map.of(X, x));
    }

    // --- isSatisfiedBy / checkValue ---

    @Test void isSatisfiedBy_in_memberValue_satisfied() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2, 4), true).isSatisfiedBy(a(2))).isTrue();
    }

    @Test void isSatisfiedBy_in_nonMemberValue_violated() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2, 4), true).isSatisfiedBy(a(3))).isFalse();
    }

    @Test void isSatisfiedBy_notin_memberValue_violated() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2, 4), false).isSatisfiedBy(a(2))).isFalse();
    }

    @Test void isSatisfiedBy_notin_nonMemberValue_satisfied() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2, 4), false).isSatisfiedBy(a(3))).isTrue();
    }

    @Test void unassigned_optimisticallyTrue() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2), true).isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    // --- getRelation ---

    @Test void getRelation_in_includesSetAndKeyword() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2), true).getRelation()).contains(" in ").contains("1").contains("2");
    }

    @Test void getRelation_notin_includesKeyword() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2), false).getRelation()).contains(" notin ");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var built = UnaryInSetConstraint.<Integer>builder().variable(X).values(Set.of(1, 2)).positive(true).build();
        assertThat(UnaryInSetConstraint.of(X, Set.of(1, 2), true)).isEqualTo(built);
    }

    // --- propagate: in ---

    @Test void propagate_in_narrowsToMembersPresentInDomain() {
        var result = UnaryInSetConstraint.of(X, Set.of(1, 2, 10), true)
                .propagate(Map.of(X, IntRangeDomain.of(0, 5))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(1, 2);
    }

    @Test void propagate_in_noChangeWhenDomainAlreadySubsetOfValues() {
        var result = UnaryInSetConstraint.of(X, Set.of(0, 1, 2, 3, 4, 5), true)
                .propagate(Map.of(X, IntRangeDomain.of(0, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_in_infeasibleWhenNoOverlap() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(10, 11), true).propagate(Map.of(X, IntRangeDomain.of(0, 5)))).isEmpty();
    }

    // --- propagate: notin ---

    @Test void propagate_notin_deletesExcludedValues() {
        var result = UnaryInSetConstraint.of(X, Set.of(1, 2), false)
                .propagate(Map.of(X, IntRangeDomain.of(0, 5))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(0, 3, 4, 5);
    }

    @Test void propagate_notin_infeasibleWhenAllValuesExcluded() {
        assertThat(UnaryInSetConstraint.of(X, Set.of(0, 1, 2), false).propagate(Map.of(X, IntRangeDomain.of(0, 2)))).isEmpty();
    }

    @Test void propagate_notin_noChangeWhenNoOverlap() {
        var result = UnaryInSetConstraint.of(X, Set.of(10, 11), false)
                .propagate(Map.of(X, IntRangeDomain.of(0, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_gaplessDomain_citesRangeBounds() {
        var result = UnaryInSetConstraint.of(X, Set.of(10, 11), true).propagateWithReasons(Map.of(X, IntRangeDomain.of(0, 5)));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.reason().isSatisfiedBy(a(0))).isFalse();
        assertThat(result.reason().isSatisfiedBy(a(10))).isTrue();
    }

    @Test void explainInfeasible_gappedDomain_citesValueSet() {
        var gapped = IntRangeDomain.of(0, 10).toBuilder().delete(5).delete(6).delete(7).delete(8).delete(9).build();
        var result = UnaryInSetConstraint.of(X, Set.of(20, 21), true).propagateWithReasons(Map.of(X, gapped));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
    }
}
