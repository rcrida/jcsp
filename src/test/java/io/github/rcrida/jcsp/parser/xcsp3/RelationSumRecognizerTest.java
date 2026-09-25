package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RelationSumRecognizer}'s decline paths directly. Each turns on the node's own
 * shape or on {@code dispatch} failing to resolve an {@code add} term, and {@link
 * TabulationRecognizer} now claims the small-scope {@code add}-of-relations shapes its
 * parser-level tests used to arrive through.
 */
class RelationSumRecognizerTest {

    private static final Function<XNode<XVarInteger>, Optional<Constraint>> RECOGNIZES_NOTHING =
            node -> Optional.empty();

    private static XNode<XVarInteger> leaf(XVarInteger v) {
        return new XNodeLeaf<>(TypeExpr.VAR, v);
    }

    private static XNode<XVarInteger> constant(long value) {
        return new XNodeLeaf<>(TypeExpr.LONG, value);
    }

    @Test
    void nonRelationalNode_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        var recognizer = new RelationSumRecognizer(XcspTestNodes.handlerWith(x), RECOGNIZES_NOTHING);
        // add(...) is arithmetic, not a relation, so there is no operator to build a sum against.
        assertThat(recognizer.recognize(new XNodeParent<>(TypeExpr.ADD, leaf(x), constant(1)))).isEmpty();
    }

    @Test
    void relationWithThreeOperands_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        var recognizer = new RelationSumRecognizer(XcspTestNodes.handlerWith(x), RECOGNIZES_NOTHING);
        XNode<XVarInteger> threeSons = new XNodeParent<>(TypeExpr.EQ, List.of(leaf(x), leaf(x), leaf(x)));
        assertThat(recognizer.recognize(threeSons)).isEmpty();
    }

    @Test
    void neitherOperandIsAnAdd_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        var recognizer = new RelationSumRecognizer(XcspTestNodes.handlerWith(x), RECOGNIZES_NOTHING);
        assertThat(recognizer.recognize(new XNodeParent<>(TypeExpr.EQ, leaf(x), constant(1)))).isEmpty();
    }

    @Test
    void addTermThatIsNotARecognizableRelation_declines() {
        XVarInteger x = XcspTestNodes.var("x", 0, 2);
        XVarInteger y = XcspTestNodes.var("y", 0, 2);
        var recognizer = new RelationSumRecognizer(XcspTestNodes.handlerWith(x, y), RECOGNIZES_NOTHING);
        // eq(add(x,y),1): the add side is present, but its terms must each resolve to a relation to
        // be weighted as a boolean indicator, and this dispatch resolves none of them.
        XNode<XVarInteger> add = new XNodeParent<>(TypeExpr.ADD, leaf(x), leaf(y));
        assertThat(recognizer.recognize(new XNodeParent<>(TypeExpr.EQ, add, constant(1)))).isEmpty();
    }
}
