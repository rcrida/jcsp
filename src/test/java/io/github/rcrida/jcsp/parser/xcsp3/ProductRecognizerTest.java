package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directly exercises the one branch of {@link ProductRecognizer} that's unreachable through any
 * real XCSP3 file -- see the class's own Javadoc for the arity generalization this guards.
 */
class ProductRecognizerTest {

    /**
     * {@code eq(mul(x),t)}-shaped: a single-operand {@code mul(...)}. Confirmed unreachable through
     * any real, parseable XCSP3 file -- {@code xcsp3-tools}' own canonizer collapses a one-operand
     * {@code mul} directly into its sole operand ({@code TypeExpr#isIdentityWhenOneOperand()}) well
     * before this recognizer ever sees the tree, so {@code mulNode.sons.length < 2} needs the same
     * direct construction approach {@link BooleanProductChannelRecognizerTest} already uses for its
     * own unreachable-branch coverage.
     */
    @Test void singleOperandMul_declines() {
        XNodeLeaf<XVarInteger> factor = XNode.specialLeaf("x");
        XNodeParent<XVarInteger> mul = new XNodeParent<>(TypeExpr.MUL, factor);
        XNodeLeaf<XVarInteger> target = XNode.specialLeaf("t");
        XNode<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ, mul, target);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new ProductRecognizer(handler).recognize(tree)).isEmpty();
    }
}
