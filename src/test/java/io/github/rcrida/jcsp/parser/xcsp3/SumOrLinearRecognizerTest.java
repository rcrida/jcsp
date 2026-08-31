package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directly exercises the two branches of {@link SumOrLinearRecognizer#addTerm} that are
 * unreachable through any real XCSP3 file: {@code neg}/{@code sub} are strictly unary/binary
 * operators respectively in the XCSP3 grammar (unlike {@code add}/{@code mul}, which are n-ary),
 * so a {@code neg} node with anything but one son, or a {@code sub} node with anything but two,
 * can't occur via real parsing -- the same direct-construction approach {@link
 * BooleanProductChannelRecognizerTest}/{@link ProductRecognizerTest} already use for their own
 * unreachable-arity branches.
 */
class SumOrLinearRecognizerTest {

    @Test void negTermWithTwoOperands_declines() {
        XNodeLeaf<XVarInteger> a = XNode.specialLeaf("a");
        XNodeLeaf<XVarInteger> b = XNode.specialLeaf("b");
        XNodeParent<XVarInteger> negWithTwoSons = new XNodeParent<>(TypeExpr.NEG, a, b);
        XNodeParent<XVarInteger> addNode = new XNodeParent<>(TypeExpr.ADD, negWithTwoSons);
        XNodeLeaf<XVarInteger> target = XNode.specialLeaf("t");
        XNode<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ, addNode, target);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new SumOrLinearRecognizer(handler).recognize(tree)).isEmpty();
    }

    @Test void subTermWithThreeOperands_declines() {
        XNodeLeaf<XVarInteger> a = XNode.specialLeaf("a");
        XNodeLeaf<XVarInteger> b = XNode.specialLeaf("b");
        XNodeLeaf<XVarInteger> c = XNode.specialLeaf("c");
        XNodeParent<XVarInteger> subWithThreeSons = new XNodeParent<>(TypeExpr.SUB, List.of(a, b, c));
        XNodeParent<XVarInteger> addNode = new XNodeParent<>(TypeExpr.ADD, subWithThreeSons);
        XNodeLeaf<XVarInteger> target = XNode.specialLeaf("t");
        XNode<XVarInteger> tree = new XNodeParent<>(TypeExpr.EQ, addNode, target);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThat(new SumOrLinearRecognizer(handler).recognize(tree)).isEmpty();
    }
}
