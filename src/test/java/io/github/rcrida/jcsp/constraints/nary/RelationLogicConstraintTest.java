package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationLogicConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final DiscreteDomain<Integer> ZERO_ONE = IntRangeDomain.of(0, 1);
    static final Domain<Integer> ZERO_ONLY = ZERO_ONE.toBuilder().delete(1).build();
    static final Domain<Integer> ONE_ONLY = ZERO_ONE.toBuilder().delete(0).build();
    static final DiscreteDomain<Integer> ONE_TO_THREE = IntRangeDomain.of(1, 3);

    // --- construction ---

    @Test
    void valueLiteral_nonEqNeqOperator_throws() {
        Variable<Integer> a = F.create("a");
        assertThatThrownBy(() -> new RelationLogicConstraint.ValueLiteral(a, Operator.LT, 1))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void variableLiteral_nonEqNeqOperator_throws() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        assertThatThrownBy(() -> new RelationLogicConstraint.VariableLiteral(a, Operator.LT, b))
                .isInstanceOf(AssertionError.class);
    }

    // --- Literal.negate() ---

    @Test
    void valueLiteral_negate_flipsOperator() {
        Variable<Integer> a = F.create("a");
        var literal = new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1);
        assertThat(literal.negate()).isEqualTo(new RelationLogicConstraint.ValueLiteral(a, Operator.NEQ, 1));
        assertThat(literal.negate().negate()).isEqualTo(literal);
    }

    @Test
    void variableLiteral_negate_flipsOperator() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var literal = new RelationLogicConstraint.VariableLiteral(a, Operator.EQ, b);
        assertThat(literal.negate()).isEqualTo(new RelationLogicConstraint.VariableLiteral(a, Operator.NEQ, b));
        assertThat(literal.negate().negate()).isEqualTo(literal);
    }

    // --- isSatisfiedBy() ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_valueLiterals_or_bothFalse_isFalse() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0, b, 1)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_variableLiterals_evaluatesLeftVsRight() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c"), d = F.create("d");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.VariableLiteral(a, Operator.NEQ, b), LogicOperator.AND,
                new RelationLogicConstraint.VariableLiteral(c, Operator.EQ, d));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 2, c, 3, d, 3)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 1, c, 3, d, 3)))).isFalse();
    }

    @Test
    void getVariables_unionOfBothLiterals() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.NEQ, c));
        assertThat(constraint.getVariables()).containsExactlyInAnyOrder(a, b, c);
    }

    // --- getRelation() / toString() ---

    @Test
    void getRelation_valueLiterals() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.NEQ, 0));
        assertThat(constraint.getRelation()).isEqualTo("a == 1 || b != 0");
    }

    @Test
    void getRelation_variableLiterals() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c"), d = F.create("d");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.VariableLiteral(a, Operator.EQ, b), LogicOperator.XOR,
                new RelationLogicConstraint.VariableLiteral(c, Operator.NEQ, d));
        assertThat(constraint.getRelation()).isEqualTo("a == b ^ c != d");
    }

    // --- classify(): ValueLiteral NEQ branch (both outcomes) ---

    @Test
    void classify_valueLiteralNeq_domainDoesNotContainValue_satisfied() {
        // b's literal is b != 5; ONE_TO_THREE doesn't contain 5 at all -> guaranteed satisfied.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.NEQ, 5));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, ONE_TO_THREE));
        assertThat(result).contains(Map.of());
    }

    @Test
    void classify_valueLiteralNeq_domainSingletonAtValue_falsified() {
        // b's literal is b != 2; b's domain is singleton {2} -> guaranteed falsified.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.NEQ, 2));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, IntRangeDomain.of(2, 2)));
        assertThat(result).contains(Map.of());
    }

    // --- propagate(): both literals decided ---

    @Test
    void propagate_bothDecided_operatorTrue_noOp() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, ZERO_ONLY));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_bothDecided_operatorFalse_infeasible() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, b, ONE_ONLY))).isEmpty();
    }

    // --- propagate(): exactly one decided, both candidates unconstrain the other ---

    @Test
    void propagate_or_leftTrue_rightUndetermined_noOp() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, ZERO_ONE));
        assertThat(result).contains(Map.of());
    }

    // --- propagate(): exactly one decided, forces the other (ValueLiteral EQ/NEQ) ---

    @Test
    void propagate_or_leftFalse_forcesRightValueLiteralEq() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, ZERO_ONE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ZERO_ONLY);
    }

    @Test
    void propagate_forcesValueLiteralNeq_deletesOneValue() {
        // right = a != 2 (NEQ literal); OR with left already false -> right must hold -> delete 2 from b's domain.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.NEQ, 2));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ONE_TO_THREE.toBuilder().delete(2).build());
    }

    // --- propagate(): forcing a VariableLiteral (EQ narrows to intersection, NEQ partial) ---

    @Test
    void propagate_forcesVariableLiteralEq_narrowsBothToIntersection() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.EQ, c));
        Domain<Integer> bDomain = IntRangeDomain.of(1, 3);
        Domain<Integer> cDomain = IntRangeDomain.of(2, 4);
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, bDomain, c, cDomain));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b, c);
        assertThat(result.get().get(b)).isEqualTo(IntRangeDomain.of(2, 3));
        assertThat(result.get().get(c)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void propagate_forcesVariableLiteralNeq_leftSingletonDeletesFromRight() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.NEQ, c));
        Domain<Integer> bDomain = IntRangeDomain.of(2, 2);
        Domain<Integer> cDomain = IntRangeDomain.of(1, 3);
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, bDomain, c, cDomain));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(c);
        assertThat(result.get().get(c)).isEqualTo(ONE_TO_THREE.toBuilder().delete(2).build());
    }

    @Test
    void propagate_forcesVariableLiteralNeq_rightSingletonDeletesFromLeft() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.NEQ, c));
        Domain<Integer> bDomain = IntRangeDomain.of(1, 3);
        Domain<Integer> cDomain = IntRangeDomain.of(2, 2);
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, bDomain, c, cDomain));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ONE_TO_THREE.toBuilder().delete(2).build());
    }

    // --- classify(): VariableLiteral against a non-DiscreteDomain side degrades to undetermined ---

    @Test
    void classify_variableLiteral_leftNonDiscreteDomain_degradesToUndetermined() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.EQ, c));
        var result = constraint.propagate(Map.of(
                a, ZERO_ONE, b, io.github.rcrida.jcsp.domains.IntervalDomain.of(0, 5), c, ZERO_ONE));
        assertThat(result).contains(Map.of());
    }

    @Test
    void classify_variableLiteral_rightNonDiscreteDomain_degradesToUndetermined() {
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(b, Operator.EQ, c));
        var result = constraint.propagate(Map.of(
                a, ZERO_ONE, b, ZERO_ONE, c, io.github.rcrida.jcsp.domains.IntervalDomain.of(0, 5)));
        assertThat(result).contains(Map.of());
    }

    // --- propagate(): forcing the negated literal (satisfiesFalse branch) ---

    @Test
    void propagate_xor_leftTrue_forcesRightFalse_viaNegatedLiteral() {
        // XOR(true, right) is true only when right is false -> forces b != 0 (negation of b == 0).
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.XOR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, ZERO_ONE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ONE_ONLY);
    }

    // --- propagate(): one decided, neither candidate works -> infeasible ---

    @Test
    void propagate_and_leftFalse_infeasibleRegardlessOfRight() {
        // AND(false, right) is never true -> infeasible even though right's domain is fully open.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.AND,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, b, ZERO_ONE))).isEmpty();
    }

    // --- propagate(): right decided, left undetermined (symmetric branch) ---

    @Test
    void propagate_rightDecided_forcesLeft() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ZERO_ONE, b, ONE_ONLY));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(a);
        assertThat(result.get().get(a)).isEqualTo(ONE_ONLY);
    }

    // --- propagate(): neither decided ---

    @Test
    void propagate_neitherDecided_noOp() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, ZERO_ONE, b, ZERO_ONE));
        assertThat(result).contains(Map.of());
    }

    // --- propagate(): non-DiscreteDomain literal side degrades to undetermined ---

    @Test
    void classify_nonDiscreteDomain_degradesToUndetermined() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.propagate(Map.of(a, io.github.rcrida.jcsp.domains.IntervalDomain.of(0, 5), b, ZERO_ONE));
        assertThat(result).contains(Map.of());
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_allSingleton_producesGroundNogood() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.explainInfeasible(Map.of(a, ZERO_ONLY, b, ONE_ONLY));
        assertThat(result).contains(GroundNogoodConstraint.of(Map.of(a, 0, b, 1)));
    }

    @Test
    void explainInfeasible_nonSingletonDomain_producesValueSetNogood() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        Domain<Integer> aDomain = ONE_TO_THREE.toBuilder().delete(1).build(); // {2,3}
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(b, Operator.EQ, 0));
        var result = constraint.explainInfeasible(Map.of(a, aDomain, b, ONE_ONLY));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(ValueSetNogoodConstraint.class);
    }
}
