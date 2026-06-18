package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryLogicConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> A = F.create("a");
    static final Variable<Boolean> B = F.create("b");

    static BinaryLogicConstraint c(LogicOperator op) { return BinaryLogicConstraint.of(A, op, B); }
    static Assignment a(boolean a, boolean b) { return Assignment.of(Map.of(A, a, B, b)); }

    // AND: true only when both true
    @Test void and_tt() { assertThat(c(LogicOperator.AND).isSatisfiedBy(a(true,  true))).isTrue(); }
    @Test void and_tf() { assertThat(c(LogicOperator.AND).isSatisfiedBy(a(true,  false))).isFalse(); }
    @Test void and_ft() { assertThat(c(LogicOperator.AND).isSatisfiedBy(a(false, true))).isFalse(); }
    @Test void and_ff() { assertThat(c(LogicOperator.AND).isSatisfiedBy(a(false, false))).isFalse(); }

    // OR: false only when both false
    @Test void or_tt()  { assertThat(c(LogicOperator.OR).isSatisfiedBy(a(true,  true))).isTrue(); }
    @Test void or_tf()  { assertThat(c(LogicOperator.OR).isSatisfiedBy(a(true,  false))).isTrue(); }
    @Test void or_ft()  { assertThat(c(LogicOperator.OR).isSatisfiedBy(a(false, true))).isTrue(); }
    @Test void or_ff()  { assertThat(c(LogicOperator.OR).isSatisfiedBy(a(false, false))).isFalse(); }

    // XOR: true only when exactly one is true
    @Test void xor_tt() { assertThat(c(LogicOperator.XOR).isSatisfiedBy(a(true,  true))).isFalse(); }
    @Test void xor_tf() { assertThat(c(LogicOperator.XOR).isSatisfiedBy(a(true,  false))).isTrue(); }
    @Test void xor_ft() { assertThat(c(LogicOperator.XOR).isSatisfiedBy(a(false, true))).isTrue(); }
    @Test void xor_ff() { assertThat(c(LogicOperator.XOR).isSatisfiedBy(a(false, false))).isFalse(); }

    // NAND: false only when both true
    @Test void nand_tt() { assertThat(c(LogicOperator.NAND).isSatisfiedBy(a(true,  true))).isFalse(); }
    @Test void nand_tf() { assertThat(c(LogicOperator.NAND).isSatisfiedBy(a(true,  false))).isTrue(); }
    @Test void nand_ff() { assertThat(c(LogicOperator.NAND).isSatisfiedBy(a(false, false))).isTrue(); }

    // NOR: true only when both false
    @Test void nor_ff()  { assertThat(c(LogicOperator.NOR).isSatisfiedBy(a(false, false))).isTrue(); }
    @Test void nor_ft()  { assertThat(c(LogicOperator.NOR).isSatisfiedBy(a(false, true))).isFalse(); }
    @Test void nor_tf()  { assertThat(c(LogicOperator.NOR).isSatisfiedBy(a(true,  false))).isFalse(); }

    // XNOR: true when both equal
    @Test void xnor_tt() { assertThat(c(LogicOperator.XNOR).isSatisfiedBy(a(true,  true))).isTrue(); }
    @Test void xnor_tf() { assertThat(c(LogicOperator.XNOR).isSatisfiedBy(a(true,  false))).isFalse(); }

    @Test
    void partialAssignment_optimisticallyTrue() {
        assertThat(c(LogicOperator.AND).isSatisfiedBy(Assignment.of(Map.of(A, true)))).isTrue();
        assertThat(c(LogicOperator.OR).isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(c(LogicOperator.OR).toString()).isEqualTo("<(a, b), a || b>");
        assertThat(c(LogicOperator.XOR).toString()).isEqualTo("<(a, b), a ^ b>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryLogicConstraint.of(A, LogicOperator.OR, B))
                .isEqualTo(BinaryLogicConstraint.of(A, LogicOperator.OR, B));
    }

    @Test
    void solver_orAndXor_solutionCount() {
        // (a || b) AND (b ^ c): 3 solutions — (F,T,F), (T,F,T), (T,T,F)
        Variable<Boolean> C = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(A, BooleanDomain.INSTANCE)
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(C, BooleanDomain.INSTANCE)
                .logicConstraint(A, LogicOperator.OR,  B)
                .logicConstraint(B, LogicOperator.XOR, C)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(3);
    }
}
