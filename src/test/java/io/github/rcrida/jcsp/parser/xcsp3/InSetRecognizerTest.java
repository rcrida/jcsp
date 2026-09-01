package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directly exercises two branches of {@link InSetRecognizer} that are unreachable through any real
 * XCSP3 file: {@code in}/{@code notin} are fixed-arity (value, set) operators in the grammar, and
 * their second operand is always itself a {@code set(...)} node, so neither a wrong-arity node nor
 * a non-{@code set} second operand can occur via real parsing -- the same direct-construction
 * approach {@link ProductRecognizerTest}/{@link SumOrLinearRecognizerTest} already use for their
 * own unreachable-arity branches.
 */
class InSetRecognizerTest {

    @Test void wrongArity_declines() {
        XNodeLeaf<XVarInteger> x = XNode.specialLeaf("x");
        XNode<XVarInteger> tree = new XNodeParent<>(TypeExpr.IN, x);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new InSetRecognizer(handler).recognize(tree)).isEmpty();
    }

    @Test void secondOperandNotSet_declines() {
        XNodeLeaf<XVarInteger> x = XNode.specialLeaf("x");
        XNodeLeaf<XVarInteger> notASet = XNode.specialLeaf("y");
        XNode<XVarInteger> tree = new XNodeParent<>(TypeExpr.NOTIN, x, notASet);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new InSetRecognizer(handler).recognize(tree)).isEmpty();
    }
}
