package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ChannelRecognizer} directly rather than through a parsed instance, because
 * {@link TabulationRecognizer} now claims the small-scope {@code eq}/{@code ne}/{@code iff} shapes
 * its parser-level tests used to reach, and the branches below turn on what {@code dispatch}
 * returns -- which an XML fixture controls only indirectly, if at all.
 */
class ChannelRecognizerTest {

    /** Dispatch that recognizes nothing, so every operand falls through to the bare-variable path. */
    private static final java.util.function.Function<XNode<XVarInteger>, Optional<Constraint>> RECOGNIZES_NOTHING =
            node -> Optional.empty();

    private static XNode<XVarInteger> leaf(XVarInteger v) {
        return new XNodeLeaf<>(TypeExpr.VAR, v);
    }

    @Test
    void relationWithThreeOperands_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        var recognizer = new ChannelRecognizer(XcspTestNodes.handlerWith(x), RECOGNIZES_NOTHING);
        XNode<XVarInteger> threeSons = new XNodeParent<>(TypeExpr.EQ, List.of(leaf(x), leaf(x), leaf(x)));
        assertThat(recognizer.recognize(threeSons)).isEmpty();
    }

    @Test
    void leftOperandNeitherRelationNorVariable_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        var recognizer = new ChannelRecognizer(XcspTestNodes.handlerWith(x), RECOGNIZES_NOTHING);
        // add(x, 1) is not a relation this dispatch recognizes, and asVariable cannot reduce it to a
        // bare variable either, so the left operand resolves to nothing.
        XNode<XVarInteger> compound = new XNodeParent<>(TypeExpr.ADD, leaf(x), new XNodeLeaf<>(TypeExpr.LONG, 1L));
        assertThat(recognizer.recognize(new XNodeParent<>(TypeExpr.EQ, compound, leaf(x)))).isEmpty();
    }

    @Test
    void bareVariableOperands_channelThroughBooleanIndicators() {
        XVarInteger x = XcspTestNodes.var("x", 0, 1);
        XVarInteger y = XcspTestNodes.var("y", 0, 1);
        var recognizer = new ChannelRecognizer(XcspTestNodes.handlerWith(x, y), RECOGNIZES_NOTHING);
        // Both operands are bare variables, so each resolves via the BareVariable arm and is bridged
        // into a Variable<Boolean> by booleanIndicatorFor rather than being reified as a relation.
        Optional<Constraint> result = recognizer.recognize(new XNodeParent<>(TypeExpr.EQ, leaf(x), leaf(y)));
        assertThat(result).get().isInstanceOf(BinaryComparatorConstraint.class);
    }
}
