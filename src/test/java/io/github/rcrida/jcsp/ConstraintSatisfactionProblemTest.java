package io.github.rcrida.jcsp;

import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.AustraliaMapColouringTest;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.NSW;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.NT;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.Q;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.SA;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.T;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.V;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.WA;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.DOMAIN;

@ExtendWith(MockitoExtension.class)
public class ConstraintSatisfactionProblemTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    @Mock
    Domain<Integer> domain;

    @Test
    void validateConstraints() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        assertThatThrownBy(() -> ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .notEqualsConstraint(a, b)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Constraints reference unknown variables [B]");
    }

    @Test
    void getNeightbours() {
        val csp = AustraliaMapColouringTest.problem();
        val expected = Map.of(
                WA, Set.of(NT, SA),
                NT, Set.of(WA, SA, Q),
                SA, Set.of(WA, NT, Q, NSW, V),
                Q, Set.of(NT, SA, NSW),
                NSW, Set.of(SA, Q, V),
                V, Set.of(SA, NSW),
                T, Set.of()
        );
        assertThat(csp.getNeighbours()).isEqualTo(expected);
    }

    @Test
    void decomposeSubproblems() {
        val csp = AustraliaMapColouringTest.problem();
        val expected = Set.of(
                ConstraintSatisfactionProblem.builder()
                        .variableDomain(WA, DOMAIN)
                        .variableDomain(NT, DOMAIN)
                        .variableDomain(Q, DOMAIN)
                        .variableDomain(NSW, DOMAIN)
                        .variableDomain(V, DOMAIN)
                        .variableDomain(SA, DOMAIN)
                        .notEqualsConstraint(SA, WA)
                        .notEqualsConstraint(SA, NT)
                        .notEqualsConstraint(SA, Q)
                        .notEqualsConstraint(SA, NSW)
                        .notEqualsConstraint(SA, V)
                        .notEqualsConstraint(WA, NT)
                        .notEqualsConstraint(NT, Q)
                        .notEqualsConstraint(Q, NSW)
                        .notEqualsConstraint(NSW, V)
                        .build(),
                ConstraintSatisfactionProblem.builder()
                        .variableDomain(T, DOMAIN)
                        .build()
        );
        assertThat(csp.decomposeSubproblems()).hasValue(expected);
        assertThat(csp.decomposeSubproblems()).hasValue(expected);
    }

    @Test
    void isCyclic() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .variableDomain(b, domain)
                .variableDomain(c, domain)
                .allDiffConstraint(Set.of(a, b, c))
                .build();
        assertThat(csp.isCyclic()).isTrue();
        assertThat(csp.isTree()).isFalse();
    }

    @Test
    void isFullyConnected() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .variableDomain(b, domain)
                .build();
        assertThat(csp.isFullyConnected()).isFalse();
        assertThat(csp.isTree()).isFalse();
    }

    @Test
    void isTree_singleVariable() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .build();
        assertThat(csp.isTree()).isTrue();
    }

    @Test
    void isTree_singleConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .variableDomain(b, domain)
                .variableDomain(c, domain)
                .notEqualsConstraint(a, b)
                .notEqualsConstraint(a, c)
                .build();
        assertThat(csp.isTree()).isTrue();
    }

    @Test
    void valid_multipleConstraintsBetweenVariables() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 100))
                .variableDomain(b, IntRangeDomain.of(0, 100))
                .notEqualsConstraint(a, b)
                .constraint(BinaryOffsetConstraint.<Integer>builder().left(a).right(b).offset(0).operator(Operator.NEQ).build())
                .build();
        assertThat(csp.isTree()).isTrue();
    }

    @Test
    void exactlyOneConstraint_singleVariable_emitsUnaryValueConstraint() {
        Variable<Boolean> a = VARIABLE_FACTORY.create("A");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
        assertThat(csp.getConstraints().iterator().next())
                .isInstanceOf(UnaryValueConstraint.class);
    }

    @Test
    void builder_notEqualsChainConstraint_asserts() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        assertThatThrownBy(() -> ConstraintSatisfactionProblem.builder().notEqualsChainConstraint(List.of(a)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void builder_predicateConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 100))
                .predicateConstraint(a, (Integer v) -> v > 3)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_sumConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 10))
                .variableDomain(b, IntRangeDomain.of(0, 10))
                .sumConstraint(Set.of(a, b), Operator.EQ, 5)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_countConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 3))
                .variableDomain(b, IntRangeDomain.of(0, 3))
                .countConstraint(Set.of(a, b), 1, Operator.EQ, 1)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_linearConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 5))
                .variableDomain(b, IntRangeDomain.of(0, 5))
                .linearConstraint(Map.of(a, 2, b, 3), Operator.EQ, 12)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_cumulativeConstraint() {
        Variable<Integer> s1 = VARIABLE_FACTORY.create("s1");
        Variable<Integer> s2 = VARIABLE_FACTORY.create("s2");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s1, IntRangeDomain.of(0, 5))
                .variableDomain(s2, IntRangeDomain.of(0, 5))
                .cumulativeConstraint(List.of(s1, s2), List.of(2, 2), List.of(1, 1), 1)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_lexConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        Variable<Integer> d = VARIABLE_FACTORY.create("D");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 3)).variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 3)).variableDomain(d, IntRangeDomain.of(1, 3))
                .lexConstraint(List.of(a, b), Operator.LEQ, List.of(c, d))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_globalCardinalityConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 3))
                .variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 3))
                .globalCardinalityConstraint(Set.of(a, b, c), Map.of(1, 2, 2, 1))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_logicConstraint() {
        Variable<Boolean> a = VARIABLE_FACTORY.create("A");
        Variable<Boolean> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, io.github.rcrida.jcsp.domains.BooleanDomain.INSTANCE)
                .variableDomain(b, io.github.rcrida.jcsp.domains.BooleanDomain.INSTANCE)
                .logicConstraint(a, io.github.rcrida.jcsp.constraints.LogicOperator.OR, b)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_binaryComparatorConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 5))
                .variableDomain(b, IntRangeDomain.of(1, 5))
                .comparatorConstraint(a, Operator.LEQ, b)
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_increasingConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 5))
                .variableDomain(b, IntRangeDomain.of(1, 5))
                .variableDomain(c, IntRangeDomain.of(1, 5))
                .increasingConstraint(List.of(a, b, c))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_decreasingConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 5))
                .variableDomain(b, IntRangeDomain.of(1, 5))
                .variableDomain(c, IntRangeDomain.of(1, 5))
                .decreasingConstraint(List.of(a, b, c))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_elementConstraint() {
        Variable<Integer> idx = VARIABLE_FACTORY.create("idx");
        Variable<Integer> res = VARIABLE_FACTORY.create("res");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(idx, IntRangeDomain.of(1, 3))
                .variableDomain(res, IntRangeDomain.of(10, 30))
                .elementConstraint(idx, res, List.of(10, 20, 30))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }

    @Test
    void builder_tuplesConstraint() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 3))
                .variableDomain(b, IntRangeDomain.of(0, 3))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(a, 1, b, 2)),
                        Assignment.of(Map.of(a, 2, b, 1))))
                .build();
        assertThat(csp.getConstraints()).hasSize(1);
    }
}
