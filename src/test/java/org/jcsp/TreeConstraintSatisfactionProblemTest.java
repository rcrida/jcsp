package org.jcsp;

import lombok.val;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.constraints.binary.BinaryOffsetConstraint;
import org.jcsp.constraints.binary.Operator;
import org.jcsp.constraints.nary.AllDiffConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.solver.AustraliaMapColouringTest;
import org.jcsp.solver.CryptarithmeticTest;
import org.jcsp.solver.NQueensTest;
import org.jcsp.solver.SudokuTest;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TreeConstraintSatisfactionProblemTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    @Mock
    Domain domain;

    @Test
    void invalid_empty() {
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(Map.of(), Set.of()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must not be empty");
    }

    @Test
    void invalid_cycles() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        Variable c = VARIABLE_FACTORY.create("C");
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(
                Map.of(a, domain, b, domain, c, domain),
                Set.of(AllDiffConstraint.builder().variable(a).variable(b).variable(c).build())))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must not contain cycles");
    }

    @Test
    void invalid_disconnected() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(
                Map.of(a, domain, b, domain),
                Set.of()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must be fully connected");
    }

    @Test
    void valid_single() {
        Variable a = VARIABLE_FACTORY.create("A");
        new TreeConstraintSatisfactionProblem(Map.of(a, domain), Set.of());
    }

    @Test
    void valid() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        Variable c = VARIABLE_FACTORY.create("C");
        new TreeConstraintSatisfactionProblem(
                Map.of(a, domain, b, domain, c, domain),
                Set.of(BinaryNotEqualsConstraint.builder().left(a).right(b).build(), BinaryNotEqualsConstraint.builder().left(a).right(c).build()));
    }

    @Test
    void valid_multiplePaths() {
        Variable a = VARIABLE_FACTORY.create("A");
        Variable b = VARIABLE_FACTORY.create("B");
        new TreeConstraintSatisfactionProblem(
                Map.of(a, domain, b, domain),
                Set.of(
                        BinaryNotEqualsConstraint.builder().left(a).right(b).build(),
                        BinaryOffsetConstraint.builder().left(a).right(b).offset(0).operator(Operator.NEQ).build()));
    }

    @Test
    void australianMapColouring() {
        val csp = AustraliaMapColouringTest.problem();
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(csp.getVariableDomains(), csp.getConstraints()))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Tree must");
    }

    @Test
    void cryptarithmetic() {
        val csp = CryptarithmeticTest.twoPlusTwoEqualsFour();
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(csp.getVariableDomains(), csp.getConstraints()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must not contain cycles");
    }

    @Test
    void nQueens() {
        val csp = NQueensTest.nQueens();
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(csp.getVariableDomains(), csp.getConstraints()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must not contain cycles");
    }

    @Test
    void soduko() {
        val csp = SudokuTest.sudoku();
        assertThatThrownBy(() -> new TreeConstraintSatisfactionProblem(csp.getVariableDomains(), csp.getConstraints()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Tree must not contain cycles");
    }
}
