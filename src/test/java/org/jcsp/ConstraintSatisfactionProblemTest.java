package org.jcsp;

import lombok.val;
import org.jcsp.constraints.binary.BinaryOffsetConstraint;
import org.jcsp.constraints.binary.Operator;
import org.jcsp.domains.Domain;
import org.jcsp.solver.AustraliaMapColouringTest;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jcsp.solver.AustraliaMapColouringTest.NSW;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.Q;
import static org.jcsp.solver.AustraliaMapColouringTest.SA;
import static org.jcsp.solver.AustraliaMapColouringTest.T;
import static org.jcsp.solver.AustraliaMapColouringTest.V;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;
import static org.jcsp.solver.AustraliaMapColouringTest.DOMAIN;

@ExtendWith(MockitoExtension.class)
public class ConstraintSatisfactionProblemTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    @Mock
    Domain domain;

    @Test
    void validateConstraints() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
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
        assertThat(csp.decomposeSubproblems()).isEqualTo(expected);
        assertThat(csp.decomposeSubproblems()).isEqualTo(expected);
    }

    @Test
    void isCyclic() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        Variable c = VARIABLE_FACTORY.create("C");
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
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .variableDomain(b, domain)
                .build();
        assertThat(csp.isFullyConnected()).isFalse();
        assertThat(csp.isTree()).isFalse();
    }

    @Test
    void isTree_singleVariable() {
        Variable a = VARIABLE_FACTORY.create("A");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .build();
        assertThat(csp.isTree()).isTrue();
    }

    @Test
    void isTree_singleConstraint() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        Variable c = VARIABLE_FACTORY.create("C");
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
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain)
                .variableDomain(b, domain)
                .notEqualsConstraint(a, b)
                .constraint(BinaryOffsetConstraint.builder().left(a).right(b).offset(0).operator(Operator.NEQ).build())
                .build();
        assertThat(csp.isTree()).isTrue();
    }

}
