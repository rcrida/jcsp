package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directly exercises the one branch of {@link BooleanProductChannelRecognizer} that's unreachable
 * through any real XCSP3 file -- see the class's own Javadoc for why.
 */
class BooleanProductChannelRecognizerTest {

    /**
     * {@code eq(3,5)}-shaped: {@code tree.sons[0]} is a bare {@code LONG} leaf, not a compound
     * {@code mul(...)} node. Confirmed unreachable through any real, parseable XCSP3 file -- see
     * {@link BooleanProductChannelRecognizer}'s own comment -- so this needs the same direct
     * construction approach as {@code Xcsp3CallbackHandlerTest}'s own unreachable-branch tests.
     */
    @Test void firstOperandNotXNodeParent_declines() {
        XNodeLeaf<XVarInteger> left = new XNodeLeaf<>(TypeExpr.LONG, 3L);
        XNodeLeaf<XVarInteger> right = new XNodeLeaf<>(TypeExpr.LONG, 5L);
        XNodeParent<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ, left, right);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new BooleanProductChannelRecognizer(handler).recognize(tree)).isEmpty();
    }
}
