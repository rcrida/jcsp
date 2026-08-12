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
 * VAR}/{@code LONG} leaves ever occur over {@code XVarInteger} scope), and the {@code GE}/{@code
 * GT} operator cases (xcsp3-tools' canonizer always rewrites {@code ge}/{@code gt} into {@code
 * le}/{@code lt} with swapped operands before {@code buildCtrIntension} ever sees the tree).
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
}
