package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.assignments.Assignment;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Directly exercises {@link IntensionExpressionEvaluator#evaluate} branches that are unreachable
 * through any real XCSP3 {@code <intension>} tree: the "unsupported leaf type" branch (only {@code
 * VAR}/{@code LONG} leaves ever occur over {@code XVarInteger} scope), the {@code GE}/{@code GT}
 * operator cases (xcsp3-tools' canonizer always rewrites {@code ge}/{@code gt} into {@code
 * le}/{@code lt} with swapped operands before {@code buildCtrIntension} ever sees the tree), and
 * {@code evaluateSetLiteral}'s two rejection branches (a non-{@code set(...)} {@code in}/{@code
 * notin} operand, and a {@code set(...)} containing a non-constant member) -- also alongside basic
 * {@code in}/{@code notin} correctness checks built the same hand-constructed way, even though
 * those specific shapes are also reachable through real parsing (see {@code
 * Xcsp3ParserTest#intensionSetMembership_evaluatesInAndNotin}).
 */
class IntensionExpressionEvaluatorTest {

    @Test void evaluate_unsupportedLeafType_throws() {
        XNodeLeaf<XVarInteger> specialLeaf = XNode.specialLeaf("unsupported");
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(specialLeaf, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void evaluate_greaterOrEqual() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.GE, XNode.longLeaf(5), XNode.longLeaf(3));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(1);
        XNode<XVarInteger> falseTree = XNode.node(TypeExpr.GE, XNode.longLeaf(2), XNode.longLeaf(3));
        assertThat(IntensionExpressionEvaluator.evaluate(falseTree, Assignment.empty(), Map.of())).isEqualTo(0);
    }

    @Test void evaluate_greaterThan() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.GT, XNode.longLeaf(5), XNode.longLeaf(3));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(1);
        XNode<XVarInteger> falseTree = XNode.node(TypeExpr.GT, XNode.longLeaf(3), XNode.longLeaf(3));
        assertThat(IntensionExpressionEvaluator.evaluate(falseTree, Assignment.empty(), Map.of())).isEqualTo(0);
    }

    // ---- sqr / pow / min / max / imp / if -----------------------------------------------------------

    @Test void evaluate_square() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.SQR, XNode.longLeaf(4));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(16);
    }

    @Test void evaluate_power() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.POW, XNode.longLeaf(2), XNode.longLeaf(5));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(32);
    }

    @Test void evaluate_power_zeroExponent_isOne() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.POW, XNode.longLeaf(7), XNode.longLeaf(0));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(1);
    }

    @Test void evaluate_power_negativeExponent_throws() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.POW, XNode.longLeaf(2), XNode.longLeaf(-1));
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void evaluate_min() {
        XNode<XVarInteger> tree = XNode.<XVarInteger>node(TypeExpr.MIN,
                List.of(XNode.longLeaf(5), XNode.longLeaf(2), XNode.longLeaf(8)));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(2);
    }

    @Test void evaluate_max() {
        XNode<XVarInteger> tree = XNode.<XVarInteger>node(TypeExpr.MAX,
                List.of(XNode.longLeaf(5), XNode.longLeaf(2), XNode.longLeaf(8)));
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(8);
    }

    @Test void evaluate_or() {
        // Covers both outcomes of the n-ary anyMatch: all-zero operands (false) and at least one
        // nonzero operand (true) -- previously exercised only via a real or(...) intension tree that
        // fell all the way back to PredicateConstraint (Xcsp3ParserTest's own
        // intensionOrChainedEqualityOperand test), until NaryEqualityRecognizer started resolving
        // that exact shape into a real AtLeastNConstraint instead, leaving this evaluator branch
        // with no remaining real-parsing test -- tested directly here instead.
        XNode<XVarInteger> allFalse = XNode.<XVarInteger>node(TypeExpr.OR,
                List.of(XNode.longLeaf(0), XNode.longLeaf(0)));
        assertThat(IntensionExpressionEvaluator.evaluate(allFalse, Assignment.empty(), Map.of())).isEqualTo(0);
        XNode<XVarInteger> oneTrue = XNode.<XVarInteger>node(TypeExpr.OR,
                List.of(XNode.longLeaf(0), XNode.longLeaf(1)));
        assertThat(IntensionExpressionEvaluator.evaluate(oneTrue, Assignment.empty(), Map.of())).isEqualTo(1);
    }

    @Test void evaluate_implication() {
        // Covers all four (antecedent, consequent) truth combinations, not just the two needed to
        // pick the right overall result -- the antecedent-true/consequent-true case specifically
        // exercises the short-circuited second half of "operands[0] == 0 || operands[1] != 0" that
        // a false antecedent alone never reaches.
        XNode<XVarInteger> falseAntecedent = XNode.node(TypeExpr.IMP, XNode.longLeaf(0), XNode.longLeaf(0));
        assertThat(IntensionExpressionEvaluator.evaluate(falseAntecedent, Assignment.empty(), Map.of())).isEqualTo(1);
        XNode<XVarInteger> trueAntecedentFalseConsequent = XNode.node(TypeExpr.IMP, XNode.longLeaf(1), XNode.longLeaf(0));
        assertThat(IntensionExpressionEvaluator.evaluate(trueAntecedentFalseConsequent, Assignment.empty(), Map.of())).isEqualTo(0);
        XNode<XVarInteger> trueAntecedentTrueConsequent = XNode.node(TypeExpr.IMP, XNode.longLeaf(1), XNode.longLeaf(1));
        assertThat(IntensionExpressionEvaluator.evaluate(trueAntecedentTrueConsequent, Assignment.empty(), Map.of())).isEqualTo(1);
    }

    @Test void evaluate_unsupportedOperator_throws() {
        // XCSP3-core's own integer <intension> grammar is fully covered by this evaluator now (see
        // its own Javadoc); sqrt is a real TypeExpr constant but belongs to XCSP3's continuous-math
        // extension, out of scope for an XVarInteger-typed intension -- constructed directly here
        // since no real parseable XCSP3 file reaches this branch any more.
        XNode<XVarInteger> tree = XNode.node(TypeExpr.SQRT, XNode.longLeaf(4));
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void evaluate_ternaryConditional() {
        XNode<XVarInteger> thenBranch = XNode.<XVarInteger>node(TypeExpr.IF,
                List.of(XNode.longLeaf(1), XNode.longLeaf(10), XNode.longLeaf(20)));
        assertThat(IntensionExpressionEvaluator.evaluate(thenBranch, Assignment.empty(), Map.of())).isEqualTo(10);
        XNode<XVarInteger> elseBranch = XNode.<XVarInteger>node(TypeExpr.IF,
                List.of(XNode.longLeaf(0), XNode.longLeaf(10), XNode.longLeaf(20)));
        assertThat(IntensionExpressionEvaluator.evaluate(elseBranch, Assignment.empty(), Map.of())).isEqualTo(20);
    }

    // ---- in / notin / set(...) literals -------------------------------------------------------------

    @Test void evaluate_in_member_isTrue() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.longLeaf(1), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(1), set);
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(1);
    }

    @Test void evaluate_in_nonMember_isFalse() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.longLeaf(1), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(5), set);
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(0);
    }

    @Test void evaluate_notin_nonMember_isTrue() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.longLeaf(1), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.NOTIN, XNode.longLeaf(5), set);
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(1);
    }

    @Test void evaluate_notin_member_isFalse() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.longLeaf(1), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.NOTIN, XNode.longLeaf(1), set);
        assertThat(IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of())).isEqualTo(0);
    }

    @Test void evaluate_in_nonSetSecondOperand_throws() {
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(1), XNode.longLeaf(2));
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void evaluate_setLiteral_withUnsupportedLeafMember_throws() {
        // A set(...) member still goes through evaluate's own leaf-type check (unchanged) -- only
        // the "must be a constant" restriction was lifted, not leaf-type validation itself. Real
        // variable/expression members are covered end-to-end via
        // Xcsp3ParserTest#intensionSetMembership_withVariableAndExpressionMembers_evaluatesPerAssignment,
        // per this class's own scope (branches unreachable through real parsing only).
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.specialLeaf("unsupported"), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(1), set);
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }
}
