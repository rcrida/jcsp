package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TabulationRecognizer}'s decline paths directly rather than through a parsed
 * instance. Going through {@code <intension>} XML can reach some of them, but only by inflating
 * every variable's domain until the ceiling is cleared, which makes the fixture say more about
 * arithmetic than about the branch under test -- and the whole-instance tuple budget cannot be
 * reached that way at all without materializing half a million tuples. Constructing the recognizer
 * directly allows a one-tuple budget and a hand-built tree instead.
 */
class TabulationRecognizerTest {

    /** {@code and(eq(name, value), ne(name, other))} over one registered variable. */
    private static XNode<XVarInteger> andOverOneVariable(XVarInteger x, int value, int other) {
        XNode<XVarInteger> eq = new XNodeParent<>(TypeExpr.EQ,
                new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.LONG, (long) value));
        XNode<XVarInteger> ne = new XNodeParent<>(TypeExpr.NE,
                new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.LONG, (long) other));
        return new XNodeParent<>(TypeExpr.AND, eq, ne);
    }

    @Test
    void affordableScope_isCompiledIntoATable() {
        XVarInteger x = XcspTestNodes.var("x", 0, 4);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x));
        // x == 1 and x != 3 leaves exactly one support.
        Optional<Constraint> result = recognizer.recognize(andOverOneVariable(x, 1, 3));
        assertThat(result).get().isInstanceOf(NaryTuplesConstraint.class);
        assertThat(((NaryTuplesConstraint) result.orElseThrow()).getTuples()).hasSize(1);
    }

    @Test
    void noSatisfyingTuple_declinesRatherThanBuildingAnEmptyTable() {
        XVarInteger x = XcspTestNodes.var("x", 0, 4);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x));
        // x == 1 and x != 1 is unsatisfiable; an empty table would have an empty variable set and
        // so sit outside the constraint graph entirely, so the decomposition takes it instead.
        assertThat(recognizer.recognize(andOverOneVariable(x, 1, 1))).isEmpty();
    }

    @Test
    void scopeWithNoVariables_declines() {
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith());
        XNode<XVarInteger> constantsOnly = new XNodeParent<>(TypeExpr.AND,
                new XNodeParent<>(TypeExpr.EQ,
                        new XNodeLeaf<>(TypeExpr.LONG, 1L), new XNodeLeaf<>(TypeExpr.LONG, 1L)),
                new XNodeParent<>(TypeExpr.EQ,
                        new XNodeLeaf<>(TypeExpr.LONG, 2L), new XNodeLeaf<>(TypeExpr.LONG, 2L)));
        assertThat(recognizer.recognize(constantsOnly)).isEmpty();
    }

    @Test
    void variableTheHandlerNeverRegistered_declines() {
        XVarInteger unregistered = XcspTestNodes.var("ghost", 0, 4);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith());
        assertThat(recognizer.recognize(andOverOneVariable(unregistered, 1, 3))).isEmpty();
    }

    /** {@code and(ne(name, a), ne(name, b))}: every value but two satisfies it. */
    private static XNode<XVarInteger> allButTwo(XVarInteger x, int a, int b) {
        return new XNodeParent<>(TypeExpr.AND,
                new XNodeParent<>(TypeExpr.NE, new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.LONG, (long) a)),
                new XNodeParent<>(TypeExpr.NE, new XNodeLeaf<>(TypeExpr.VAR, x), new XNodeLeaf<>(TypeExpr.LONG, (long) b)));
    }

    @Test
    void sparseRelationOverAWideDomain_isCompiledIntoATable() {
        // 4096 candidates, but one support: 1 x 4096 bits, far under MAX_INDEX_BITS.
        XVarInteger wide = XcspTestNodes.var("wide", 0, 4095);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide));
        Optional<Constraint> result = recognizer.recognize(andOverOneVariable(wide, 1, 3));
        assertThat(result).get().isInstanceOf(NaryTuplesConstraint.class);
        assertThat(((NaryTuplesConstraint) result.orElseThrow()).getTuples()).hasSize(1);
    }

    @Test
    void denseRelationAboveTheIndexCeiling_declines() {
        // 4094 supports x 4096 values is over MAX_INDEX_BITS.
        XVarInteger wide = XcspTestNodes.var("wide", 0, 4095);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide));
        assertThat(recognizer.recognize(allButTwo(wide, 1, 3))).isEmpty();
    }

    @Test
    void declinedEnumeration_stillSpendsTheBudget() {
        XVarInteger wide = XcspTestNodes.var("wide", 0, 4095);
        XVarInteger x = XcspTestNodes.var("x", 0, 1);
        // The dense scope walks 4096 candidates and is declined; the one left over cannot hold x's two.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide, x), 4097);
        assertThat(recognizer.recognize(allButTwo(wide, 1, 3))).isEmpty();
        assertThat(recognizer.recognize(andOverOneVariable(x, 1, 0))).isEmpty();
    }

    @Test
    void wholeInstanceTupleBudget_stopsTabulatingOnceSpent() {
        XVarInteger x = XcspTestNodes.var("x", 0, 1);
        XVarInteger y = XcspTestNodes.var("y", 0, 1);
        // Budget of three candidate tuples: the first two-value scope fits and spends two, leaving
        // one, which the second two-value scope no longer fits into.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x, y), 3);
        assertThat(recognizer.recognize(andOverOneVariable(x, 1, 0))).isPresent();
        assertThat(recognizer.recognize(andOverOneVariable(y, 1, 0))).isEmpty();
    }
}
