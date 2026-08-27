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

class RelationLogicConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final DiscreteDomain<Integer> ZERO_ONE = IntRangeDomain.of(0, 1);
    static final Domain<Integer> ZERO_ONLY = ZERO_ONE.toBuilder().delete(1).build();
    static final Domain<Integer> ONE_ONLY = ZERO_ONE.toBuilder().delete(0).build();
    static final DiscreteDomain<Integer> ONE_TO_THREE = IntRangeDomain.of(1, 3);

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

    // --- Literal.negate() for ordering operators (delegates to Operator#reversed) ---

    @Test
    void valueLiteral_negate_orderingOperator_delegatesToOperatorReversed() {
        Variable<Integer> a = F.create("a");
        var literal = new RelationLogicConstraint.ValueLiteral(a, Operator.LT, 5);
        assertThat(literal.negate()).isEqualTo(new RelationLogicConstraint.ValueLiteral(a, Operator.GEQ, 5));
        assertThat(literal.negate().negate()).isEqualTo(literal);
    }

    @Test
    void variableLiteral_negate_orderingOperator_delegatesToOperatorReversed() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var literal = new RelationLogicConstraint.VariableLiteral(a, Operator.GT, b);
        assertThat(literal.negate()).isEqualTo(new RelationLogicConstraint.VariableLiteral(a, Operator.LEQ, b));
        assertThat(literal.negate().negate()).isEqualTo(literal);
    }

    // --- Literal.holds() for ordering operators ---

    @Test
    void valueLiteral_holds_orderingOperator() {
        Variable<Integer> a = F.create("a");
        var lt = new RelationLogicConstraint.ValueLiteral(a, Operator.LT, 5);
        assertThat(lt.holds(Assignment.of(Map.of(a, 3)))).isTrue();
        assertThat(lt.holds(Assignment.of(Map.of(a, 5)))).isFalse();
    }

    @Test
    void variableLiteral_holds_orderingOperator() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var geq = new RelationLogicConstraint.VariableLiteral(a, Operator.GEQ, b);
        assertThat(geq.holds(Assignment.of(Map.of(a, 3, b, 3)))).isTrue();
        assertThat(geq.holds(Assignment.of(Map.of(a, 2, b, 3)))).isFalse();
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

    // --- classify()/force(): ValueLiteral ordering operators -------------------------------------
    // Each test pairs the ordering literal under test (as OR's right side) with a control left
    // literal (a == 1 against a's domain {0}, always FALSIFIED), so propagate()'s OR-driven
    // reasoning reveals classify's outcome for the right side: Map.of() for SATISFIED (both
    // decided, OR already true), Optional.empty() for FALSIFIED (both decided, OR false), and a
    // real forced/narrowed domain for UNDETERMINED (left decided forces right to hold).

    @Test
    void classify_valueLiteralLt_allSatisfy_noOp() {
        // x in {1,2,3}, x < 4: every value satisfies -> SATISFIED.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.LT, 4));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE))).contains(Map.of());
    }

    @Test
    void classify_valueLiteralLt_noneSatisfy_infeasible() {
        // x in {1,2,3}, x < 1: no value satisfies -> FALSIFIED, and OR(false,false) is infeasible.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.LT, 1));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE))).isEmpty();
    }

    @Test
    void force_valueLiteralLt_undetermined_narrowsToSatisfyingValues() {
        // x in {1,2,3}, x < 2: mixed -> UNDETERMINED, forced true -> keep only {1}.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.LT, 2));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x);
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(1, 1));
    }

    @Test
    void classify_valueLiteralLeq_boundaryAllSatisfy_noOp() {
        // x in {1,2,3}, x <= 3: every value satisfies (boundary case, distinct from LT) -> SATISFIED.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.LEQ, 3));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE))).contains(Map.of());
    }

    @Test
    void force_valueLiteralLeq_undetermined_narrowsToSatisfyingValues() {
        // x in {1,2,3}, x <= 2: mixed -> forced true -> keep {1,2}, delete 3.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.LEQ, 2));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(1, 2));
    }

    @Test
    void classify_valueLiteralGt_noneSatisfy_infeasible() {
        // x in {1,2,3}, x > 3: no value satisfies -> FALSIFIED.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.GT, 3));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE))).isEmpty();
    }

    @Test
    void force_valueLiteralGt_undetermined_narrowsToSatisfyingValues() {
        // x in {1,2,3}, x > 2: mixed -> forced true -> keep {3}.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.GT, 2));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(3, 3));
    }

    @Test
    void classify_valueLiteralGeq_boundaryAllSatisfy_noOp() {
        // x in {1,2,3}, x >= 1: every value satisfies (boundary case, distinct from GT) -> SATISFIED.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.GEQ, 1));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE))).contains(Map.of());
    }

    @Test
    void force_valueLiteralGeq_undetermined_narrowsToSatisfyingValues() {
        // x in {1,2,3}, x >= 2: mixed -> forced true -> keep {2,3}, delete 1.
        Variable<Integer> a = F.create("a"), x = F.create("x");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.ValueLiteral(x, Operator.GEQ, 2));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, x, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get().get(x)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    // --- classify()/force(): VariableLiteral ordering operators -----------------------------------

    @Test
    void classify_variableLiteralLt_allSatisfy_noOp() {
        // p in {1,2}, q in {5,7}: pMax(2) < qMin(5) -> every pairing satisfies -> SATISFIED.
        Variable<Integer> a = F.create("a"), p = F.create("p"), q = F.create("q");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(p, Operator.LT, q));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, p, IntRangeDomain.of(1, 2), q, IntRangeDomain.of(5, 7)));
        assertThat(result).contains(Map.of());
    }

    @Test
    void classify_variableLiteralLt_noneSatisfy_infeasible() {
        // p in {5,7}, q in {1,3}: pMin(5) >= qMax(3) -> no pairing satisfies -> FALSIFIED.
        Variable<Integer> a = F.create("a"), p = F.create("p"), q = F.create("q");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(p, Operator.LT, q));
        assertThat(constraint.propagate(Map.of(a, ZERO_ONLY, p, IntRangeDomain.of(5, 7), q, IntRangeDomain.of(1, 3))))
                .isEmpty();
    }

    @Test
    void force_variableLiteralLt_undetermined_narrowsBothSides() {
        // p in {1..9}, q in {0..8}: mixed -> forced p < q. p keeps l with l < qMax(8) -> loses {8,9}.
        // q keeps r with pMin(1) < r -> loses {0,1} (1 itself has no support: nothing in p is < 1).
        Variable<Integer> a = F.create("a"), p = F.create("p"), q = F.create("q");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(p, Operator.LT, q));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, p, IntRangeDomain.of(1, 9), q, IntRangeDomain.of(0, 8)));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(p, q);
        assertThat(result.get().get(p)).isEqualTo(IntRangeDomain.of(1, 7));
        assertThat(result.get().get(q)).isEqualTo(IntRangeDomain.of(0, 8).toBuilder().delete(0).delete(1).build());
    }

    @Test
    void classify_variableLiteralGt_allSatisfy_noOp() {
        // p in {5,7}, q in {1,3}: pMin(5) > qMax(3) -> every pairing satisfies -> SATISFIED.
        Variable<Integer> a = F.create("a"), p = F.create("p"), q = F.create("q");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(p, Operator.GT, q));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, p, IntRangeDomain.of(5, 7), q, IntRangeDomain.of(1, 3)));
        assertThat(result).contains(Map.of());
    }

    @Test
    void force_variableLiteralGt_undetermined_narrowsBothSides() {
        // p in {0..8}, q in {1..9}: mixed -> forced p > q. p keeps l with l > qMin(1) -> loses {0,1}.
        // q keeps r with pMax(8) > r -> loses {8,9} (8 itself has no support: nothing in p is > 8).
        Variable<Integer> a = F.create("a"), p = F.create("p"), q = F.create("q");
        var constraint = RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(a, Operator.EQ, 1), LogicOperator.OR,
                new RelationLogicConstraint.VariableLiteral(p, Operator.GT, q));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, p, IntRangeDomain.of(0, 8), q, IntRangeDomain.of(1, 9)));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(p, q);
        assertThat(result.get().get(p)).isEqualTo(IntRangeDomain.of(2, 8));
        assertThat(result.get().get(q)).isEqualTo(IntRangeDomain.of(1, 9).toBuilder().delete(8).delete(9).build());
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
