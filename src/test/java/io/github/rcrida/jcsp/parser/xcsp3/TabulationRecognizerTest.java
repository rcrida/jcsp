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
 * arithmetic than about the branch under test -- and the whole-instance budgets cannot be reached
 * that way at all without walking millions of candidates. Constructing the recognizer directly
 * allows a budget of a few tuples and a hand-built tree instead.
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
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide, x), 4097, 100);
        assertThat(recognizer.recognize(allButTwo(wide, 1, 3))).isEmpty();
        assertThat(recognizer.recognize(andOverOneVariable(x, 1, 0))).isEmpty();
    }

    @Test
    void wholeInstanceCandidateBudget_stopsTabulatingOnceSpent() {
        XVarInteger x = XcspTestNodes.var("x", 0, 1);
        XVarInteger y = XcspTestNodes.var("y", 0, 1);
        // Budget of three candidate tuples: the first two-value scope fits and spends two, leaving
        // one, which the second two-value scope, a different relation, no longer fits into.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x, y), 3, 100);
        assertThat(recognizer.recognize(andOverOneVariable(x, 1, 0))).isPresent();
        assertThat(recognizer.recognize(andOverOneVariable(y, 0, 1))).isEmpty();
    }

    /** {@code eq(left, right)}. */
    private static XNode<XVarInteger> equal(XVarInteger left, XVarInteger right) {
        return relation(TypeExpr.EQ, left, right);
    }

    /** {@code operator(left, right)}. */
    private static XNode<XVarInteger> relation(TypeExpr operator, XVarInteger left, XVarInteger right) {
        return new XNodeParent<>(operator,
                new XNodeLeaf<>(TypeExpr.VAR, left), new XNodeLeaf<>(TypeExpr.VAR, right));
    }

    @Test
    void denseRelationWhoseSampleIsOverTheIndexCeiling_isDeclinedWithoutAWalk() {
        XVarInteger x = XcspTestNodes.var("x", 0, 4095);
        XVarInteger y = XcspTestNodes.var("y", 0, 4095);
        XVarInteger z = XcspTestNodes.var("z", 0, 19_999);
        // Room to walk x != y's 16.8M candidates and 10,000 more. Walked, it would leave too little
        // for z's 20,000; declined from its sample, it leaves plenty.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x, y, z), 4096L * 4096 + 10_000, 100_000);
        assertThat(recognizer.recognize(relation(TypeExpr.NE, x, y))).isEmpty();
        // z's one support is too few sample hits to judge it by, so it is walked and kept.
        assertThat(recognizer.recognize(andOverOneVariable(z, 1, 3))).isPresent();
    }

    @Test
    void relationWhoseSampleFitsTheRoom_isWalkedAndKept() {
        XVarInteger x = XcspTestNodes.var("x", 0, 199);
        XVarInteger y = XcspTestNodes.var("y", 0, 199);
        // x <= y keeps 20,100 of 40,000 candidates: plenty of sample hits, and within the index ceiling.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x, y));
        Optional<Constraint> result = recognizer.recognize(relation(TypeExpr.LE, x, y));
        assertThat(result).get().isInstanceOf(NaryTuplesConstraint.class);
        assertThat(((NaryTuplesConstraint) result.orElseThrow()).getTuples()).hasSize(20_100);
    }

    @Test
    void relationWhoseSampleIsOverTheKeptBudget_isDeclinedWithoutAWalk() {
        XVarInteger x = XcspTestNodes.var("x", 0, 199);
        XVarInteger y = XcspTestNodes.var("y", 0, 199);
        XVarInteger z = XcspTestNodes.var("z", 0, 19_999);
        // The same x <= y against a kept budget of 1,000. Walked, its 40,000 candidates would leave
        // too little for z's 20,000; declined from its sample, they leave plenty.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(x, y, z), 40_000 + 4096 + 10_000, 1_000);
        assertThat(recognizer.recognize(relation(TypeExpr.LE, x, y))).isEmpty();
        assertThat(recognizer.recognize(andOverOneVariable(z, 1, 3))).isPresent();
    }

    @Test
    void repeatedRelation_isWalkedOnceAndRenamedOntoEachScope() {
        XVarInteger a = XcspTestNodes.var("a", 0, 9);
        XVarInteger b = XcspTestNodes.var("b", 0, 9);
        XVarInteger c = XcspTestNodes.var("c", 0, 9);
        XVarInteger d = XcspTestNodes.var("d", 0, 9);
        Xcsp3CallbackHandler handler = XcspTestNodes.handlerWith(a, b, c, d);
        // Room to walk 100 candidates once: the second scope fits only by reusing the first's walk.
        var recognizer = new TabulationRecognizer(handler, 100, 100);

        Optional<Constraint> first = recognizer.recognize(equal(a, b));
        Optional<Constraint> second = recognizer.recognize(equal(c, d));

        assertThat(first).get().isInstanceOf(NaryTuplesConstraint.class);
        assertThat(second).get().isInstanceOf(NaryTuplesConstraint.class);
        var renamed = (NaryTuplesConstraint) second.orElseThrow();
        assertThat(renamed.getVariables()).containsExactlyInAnyOrder(
                handler.variableForName("c"), handler.variableForName("d"));
        assertThat(renamed.getTuples()).hasSize(10).allSatisfy(tuple -> assertThat(
                tuple.getValue(handler.variableForName("c"))).isEqualTo(tuple.getValue(handler.variableForName("d"))));
    }

    @Test
    void sameShapeOverDifferentDomains_isWalkedAgain() {
        XVarInteger a = XcspTestNodes.var("a", 0, 9);
        XVarInteger b = XcspTestNodes.var("b", 0, 9);
        XVarInteger c = XcspTestNodes.var("c", 0, 4);
        XVarInteger d = XcspTestNodes.var("d", 0, 4);
        // 100 candidates for the first walk leave 10, short of the second relation's 25.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(a, b, c, d), 110, 100);
        assertThat(recognizer.recognize(equal(a, b))).isPresent();
        assertThat(recognizer.recognize(equal(c, d))).isEmpty();
    }

    @Test
    void repeatedRelationOverTheIndexCeiling_isDeclinedAgainWithoutAWalk() {
        XVarInteger wide = XcspTestNodes.var("wide", 0, 4095);
        // Room for two walks of 4096: if the repeated dense relation were walked again, nothing
        // would be left for the sparse one after it.
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide), 2 * 4096, 100_000);
        assertThat(recognizer.recognize(allButTwo(wide, 1, 3))).isEmpty();
        assertThat(recognizer.recognize(allButTwo(wide, 1, 3))).isEmpty();
        assertThat(recognizer.recognize(andOverOneVariable(wide, 1, 3))).isPresent();
    }

    @Test
    void sparseRelation_spendsTheKeptBudgetOnlyOnItsSupports() {
        // 4096 candidates and one support each: a candidate count would spend a 2-tuple kept budget
        // on the first table alone, but two single-support tables fit it and a third does not.
        XVarInteger wide = XcspTestNodes.var("wide", 0, 4095);
        var recognizer = new TabulationRecognizer(XcspTestNodes.handlerWith(wide), 100_000, 2);
        assertThat(recognizer.recognize(andOverOneVariable(wide, 1, 3))).isPresent();
        assertThat(recognizer.recognize(andOverOneVariable(wide, 2, 3))).isPresent();
        assertThat(recognizer.recognize(andOverOneVariable(wide, 4, 3))).isEmpty();
    }
}
