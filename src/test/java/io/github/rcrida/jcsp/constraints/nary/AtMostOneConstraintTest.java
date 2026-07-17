package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AtMostOneConstraintTest {
    @Mock Variable<Boolean> variable1;
    @Mock Variable<Boolean> variable2;
    @Mock Variable<Boolean> variable3;

    AtMostOneConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = AtMostOneConstraint.builder().variables(Set.of(variable1, variable2, variable3)).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingleTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true)))).isTrue();
    }

    @Test
    void isSatisfiedByAllFalse() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, false, variable2, false, variable3, false)))).isTrue();
    }

    @Test
    void isSatisfiedByExactlyOneTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, false, variable3, false)))).isTrue();
    }

    @Test
    void isSatisfiedByTwoTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, true, variable3, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, true, variable3, true)))).isFalse();
    }

    @Test
    void getAsBinaryConstraintsProducesPairs() {
        assertThat(constraint.getAsBinaryConstraints()).hasSize(3);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(variable1, variable2, variable3), AtMostOne>");
    }

    // --- propagate() ---

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Domain<Boolean> BOTH = BooleanDomain.INSTANCE;
    static final Domain<Boolean> TRUE = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.FALSE).build();
    static final Domain<Boolean> FALSE = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.TRUE).build();

    @Test
    void propagate_oneDefiniteTrue_forcesPossiblyTrueToFalse() {
        // a={true}, b={true,false}, c={false} -- one definite true -- force b to false
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var atMostOne = AtMostOneConstraint.builder().variables(Set.of(a, b, c)).build();
        var result = atMostOne.propagate(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(FALSE);
    }

    @Test
    void propagate_infeasible_twoDefiniteTrue() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var atMostOne = AtMostOneConstraint.builder().variables(Set.of(a, b)).build();
        assertThat(atMostOne.propagate(Map.of(a, TRUE, b, TRUE))).isEmpty();
    }

    @Test
    void propagate_noChange_zeroDefiniteTrue() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var atMostOne = AtMostOneConstraint.builder().variables(Set.of(a, b, c)).build();
        var result = atMostOne.propagate(Map.of(a, BOTH, b, BOTH, c, FALSE));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var atMostOne = AtMostOneConstraint.builder().variables(Set.of(a, b, c)).build();
        var result = atMostOne.propagateWithReasons(Map.of(a, TRUE, b, BOTH, c, FALSE));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_infeasible_attributesForcedTrueVariables() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var atMostOne = AtMostOneConstraint.builder().variables(Set.of(a, b, c)).build();
        var result = atMostOne.propagateWithReasons(Map.of(a, TRUE, b, TRUE, c, FALSE));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(a, true, b, true)));
    }
}
