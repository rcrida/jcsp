package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static io.github.rcrida.jcsp.constraints.nary.AtMostNConstraintTest.BOTH;
import static io.github.rcrida.jcsp.constraints.nary.AtMostNConstraintTest.FALSE;
import static io.github.rcrida.jcsp.constraints.nary.AtMostNConstraintTest.TRUE;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AtLeastNConstraintTest {
    @Mock Variable<Boolean> v1;
    @Mock Variable<Boolean> v2;
    @Mock Variable<Boolean> v3;
    @Mock Variable<Boolean> v4;

    AtLeastNConstraint constraint2;
    AtLeastNConstraint constraint3;

    @BeforeEach
    void setUp() {
        constraint2 = AtLeastNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(2).build();
        constraint3 = AtLeastNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(3).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByPartialBelowBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAtBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, false, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAboveBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, true)))).isTrue();
    }

    @Test
    void isSatisfiedByCountBelowBoundWhenComplete() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, false, v3, false, v4, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllFalseWhenComplete() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, false, v2, false, v3, false, v4, false)))).isFalse();
    }

    @Test
    void notBinaryDecomposable() {
        assertThat(constraint2).isNotInstanceOf(BinaryDecomposable.class);
    }

    @Test
    void getRelation() {
        assertThat(constraint2.getRelation()).isEqualTo("AtLeast2");
        assertThat(constraint3.getRelation()).isEqualTo("AtLeast3");
    }

    @Test
    void testToString() {
        assertThat(constraint2.toString()).isEqualTo("<(v1, v2, v3, v4), AtLeast2>");
    }

    // --- propagate() ---

    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void propagate_maxCountEqualsN_forcesPossiblyTrueToTrue() {
        // atLeast(2): a={true}, b={true,false}, c={false} → definite=1, possible=[b], maxCount=2==n → force b to true
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b, c)).n(2).build();
        var result = constraint.propagate(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(TRUE);
    }

    @Test
    void propagate_infeasible_maxCountBelowN() {
        // atLeast(3): a={true}, b={false} → maxCount=1 < n=3 → infeasible
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b)).n(3).build();
        assertThat(constraint.propagate(Map.of(a, TRUE, b, FALSE))).isEmpty();
    }

    @Test
    void propagate_noChange_maxCountAboveN() {
        // atLeast(1): a={true,false}, b={true,false}, c={true,false} → maxCount=3 > n=1 → no pruning
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b, c)).n(1).build();
        var result = constraint.propagate(Map.of(a, BOTH, b, BOTH, c, BOTH));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b, c)).n(2).build();
        var result = constraint.propagateWithReasons(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void propagateWithReasons_infeasible_attributesForcedFalseVariables() {
        // atLeast(3): a={true}, b={false}, c={false} → maxCount=1 < n=3 → infeasible;
        // b and c are forced false and jointly explain why the count falls short.
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b, c)).n(3).build();
        var result = constraint.propagateWithReasons(Map.of(a, TRUE, b, FALSE, c, FALSE));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(b, false), Map.entry(c, false));
    }

    @Test
    void propagateWithReasons_infeasible_allForcedFalse_attributesBoth() {
        // atLeast(1): a={false}, b={false} → maxCount=0 < n=1 → infeasible;
        // no variable was ever possibly-true, so both are forced false and both are blamed.
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b)).n(1).build();
        var result = constraint.propagateWithReasons(Map.of(a, FALSE, b, FALSE));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(a, false), Map.entry(b, false));
    }

    @Test
    void propagateWithReasons_infeasible_nExceedsVariableCount_returnsEmptyReason() {
        // atLeast(3) over only two variables, both still open → maxCount=2 < n=3 → infeasible,
        // but no variable is forced false, so nothing can be blamed; caller falls back to the
        // full assignment.
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var constraint = AtLeastNConstraint.builder().variables(Set.of(a, b)).n(3).build();
        var result = constraint.propagateWithReasons(Map.of(a, BOTH, b, BOTH));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }
}
