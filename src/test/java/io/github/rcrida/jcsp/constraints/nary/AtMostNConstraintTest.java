package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AtMostNConstraintTest {
    @Mock Variable<Boolean> v1;
    @Mock Variable<Boolean> v2;
    @Mock Variable<Boolean> v3;
    @Mock Variable<Boolean> v4;

    AtMostNConstraint constraint2;
    AtMostNConstraint constraint3;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        constraint2 = AtMostNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(2).build();
        constraint3 = AtMostNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(3).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByCountWithinBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, false, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAtBound() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountExceedsBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, true)))).isFalse();
    }

    @Test
    void isSatisfiedByAllFalse() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, false, v2, false, v3, false, v4, false)))).isTrue();
    }

    @Test
    void notBinaryDecomposable() {
        assertThat(constraint2).isNotInstanceOf(BinaryDecomposable.class);
    }

    @Test
    void getRelation() {
        assertThat(constraint2.getRelation()).isEqualTo("AtMost2");
        assertThat(constraint3.getRelation()).isEqualTo("AtMost3");
    }

    @Test
    void testToString() {
        assertThat(constraint2.toString()).isEqualTo("<(v1, v2, v3, v4), AtMost2>");
    }

    // --- propagate() ---

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Domain<Boolean> BOTH  = BooleanDomain.INSTANCE;
    static final Domain<Boolean> TRUE  = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.FALSE).build();
    static final Domain<Boolean> FALSE = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.TRUE).build();

    @Test
    void propagate_quotaReached_forcesPossiblyTrueToFalse() {
        // atMost(1): a={true}, b={true,false}, c={false} → definite=1==n → force b to false
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b, c)).n(1).build();
        var result = constraint.propagate(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(FALSE);
    }

    @Test
    void propagate_infeasible_definiteExceedsN() {
        // atMost(1): a={true}, b={true} → definite=2 > n=1 → infeasible
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b)).n(1).build();
        assertThat(constraint.propagate(Map.of(a, TRUE, b, TRUE))).isEmpty();
    }

    @Test
    void propagate_noChange_definiteBelow() {
        // atMost(2): a={true}, b={true,false}, c={true,false} → definite=1 < n=2 → no pruning
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b, c)).n(2).build();
        var result = constraint.propagate(Map.of(a, TRUE, b, BOTH, c, BOTH));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b, c)).n(2).build();
        var result = constraint.propagateWithReasons(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void propagateWithReasons_infeasible_attributesForcedTrueVariables() {
        // atMost(1): a={true}, b={true}, c={false} → definite=2 > n=1 → infeasible;
        // a and b are forced true and jointly explain why the count exceeds n.
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b, c)).n(1).build();
        var result = constraint.propagateWithReasons(Map.of(a, TRUE, b, TRUE, c, FALSE));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(a, true), Map.entry(b, true));
    }

    @Test
    void propagateWithReasons_infeasible_allForcedTrue_attributesBoth() {
        // atMost(0): a={true}, b={true} → definite=2 > n=0 → infeasible; both are forced true.
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var constraint = AtMostNConstraint.builder().variables(Set.of(a, b)).n(0).build();
        var result = constraint.propagateWithReasons(Map.of(a, TRUE, b, TRUE));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(a, true), Map.entry(b, true));
    }
}
