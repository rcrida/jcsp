package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import static io.github.rcrida.jcsp.parser.xcsp3.XcspTestNodes.handlerWith;
import static io.github.rcrida.jcsp.parser.xcsp3.XcspTestNodes.var;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ExtremumRecognizer}'s guards on hand-built trees: earlier recognizers claim
 * these shapes before a parsed file can reach it.
 */
class ExtremumRecognizerTest {

    @Test void firstOperandIsALeaf_declines() {
        XVarInteger x = var("x", 0, 3), y = var("y", 0, 3);
        Xcsp3CallbackHandler handler = handlerWith(x, y);
        XNodeParent<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ,
                new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.VAR, y));
        assertThat(new ExtremumRecognizer(handler).recognize(tree)).isEmpty();
    }

    @Test void targetIsAConstant_declines() {
        XVarInteger x = var("x", 0, 3), y = var("y", 0, 3);
        Xcsp3CallbackHandler handler = handlerWith(x, y);
        XNodeParent<XVarInteger> min = new XNodeParent<>(TypeExpr.MIN,
                new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.VAR, y));
        XNodeParent<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ, min, new XNodeLeaf<>(TypeExpr.LONG, 2L));
        assertThat(new ExtremumRecognizer(handler).recognize(tree)).isEmpty();
    }
}
