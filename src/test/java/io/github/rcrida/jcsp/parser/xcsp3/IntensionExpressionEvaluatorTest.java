package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.assignments.Assignment;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.parser.entries.XVariables.XVarInteger;

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

    @Test void evaluate_setLiteral_withNonLongLeafMember_throws() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.specialLeaf("unsupported"), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(1), set);
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void evaluate_setLiteral_withNonLeafMember_throws() {
        XNode<XVarInteger> set = XNode.node(TypeExpr.SET, XNode.node(TypeExpr.ADD, XNode.longLeaf(1), XNode.longLeaf(1)), XNode.longLeaf(2));
        XNode<XVarInteger> tree = XNode.node(TypeExpr.IN, XNode.longLeaf(1), set);
        assertThatThrownBy(() -> IntensionExpressionEvaluator.evaluate(tree, Assignment.empty(), Map.of()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }
}
