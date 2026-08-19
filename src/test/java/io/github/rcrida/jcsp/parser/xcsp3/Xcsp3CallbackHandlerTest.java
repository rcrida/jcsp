package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Condition;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.Types.TypeFlag;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;
import org.xcsp.parser.entries.XVariables.XVarSymbolic;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Directly exercises the branches of {@link Xcsp3CallbackHandler} that are unreachable through any
 * real XCSP3 file -- see {@link Xcsp3CallbackHandler#applySumCondition}'s own comment for why a
 * sum/linear {@code <condition>} can never actually produce a {@link Condition} that's neither
 * {@code ConditionVal} nor {@code ConditionVar}.
 */
class Xcsp3CallbackHandlerTest {

    /** Neither {@code ConditionVal} nor {@code ConditionVar} -- not producible by real XCSP3 parsing. */
    private static final Condition NEITHER_VAL_NOR_VAR = new Condition() {
        @Override public TypeExpr operatorTypeExpr() { return TypeExpr.EQ; }
        @Override public Object rightTerm() { return 5; }
    };

    @Test void applySumCondition_unrecognisedConditionShape_throws() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.applySumCondition(Set.of(x), NEITHER_VAL_NOR_VAR, "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void applyLinearCondition_unrecognisedConditionShape_throws() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.applyLinearCondition(Map.of(x, 2), NEITHER_VAL_NOR_VAR, "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void applyMinimumCondition_unrecognisedConditionShape_throws() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.applyMinimumCondition(Set.of(x), NEITHER_VAL_NOR_VAR, "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void applyMaximumCondition_unrecognisedConditionShape_throws() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.applyMaximumCondition(Set.of(x), NEITHER_VAL_NOR_VAR, "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    /**
     * Real "smart" tuple syntax (expressions in place of literal values, e.g. {@code ge(3)}) is
     * obscure enough that no hand-authored XCSP3 fixture exercises {@link TypeFlag#SMART_TUPLES}
     * specifically -- {@code extensionStarredTuples_throwsUnsupported} in {@code Xcsp3ParserTest}
     * only ever produces {@link TypeFlag#STARRED_TUPLES}, which short-circuits the {@code ||} before
     * {@code SMART_TUPLES} is ever checked.
     */
    @Test void requireNoUnsupportedFlags_smartTuples_throws() {
        assertThatThrownBy(() -> Xcsp3CallbackHandler.requireNoUnsupportedFlags(Set.of(TypeFlag.SMART_TUPLES), "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    /**
     * The unary form of {@code extension} has no other columns for a wildcard to leave
     * unconstrained -- a starred single-variable value list has no real XCSP3 syntax to produce it
     * -- so {@code requireNoUnsupportedFlags}'s {@code STARRED_TUPLES} branch needs the same direct
     * construction as its {@code SMART_TUPLES} sibling above.
     */
    @Test void requireNoUnsupportedFlags_starredTuples_throws() {
        assertThatThrownBy(() -> Xcsp3CallbackHandler.requireNoUnsupportedFlags(Set.of(TypeFlag.STARRED_TUPLES), "c0"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    /**
     * {@code SMART_TUPLES} on the n-ary form of {@code extension} is checked before {@code list} is
     * ever used, so an empty {@code XVarInteger[]} is safe here -- real smart-tuple XML syntax is
     * obscure enough that no hand-authored fixture in {@code Xcsp3ParserTest} exercises it, same as
     * the unary case above.
     */
    @Test void buildCtrExtension_naryForm_smartTuples_throws() {
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.buildCtrExtension(
                "c0", new XVarInteger[0], new int[][] {}, true, Set.of(TypeFlag.SMART_TUPLES)))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    /**
     * {@code TypeExpr.GE}/{@code GT} never reach {@code intensionRelationalOperator} through real
     * parsing -- xcsp3-tools' canonizer always rewrites a top-level {@code ge}/{@code gt} into
     * {@code le}/{@code lt} with swapped operands first, the same reason {@code
     * IntensionExpressionEvaluatorTest#evaluate_greaterOrEqual}/{@code evaluate_greaterThan} need
     * direct construction.
     */
    @Test void intensionRelationalOperator_greaterOrEqual_mapsToGeq() {
        assertThat(Xcsp3CallbackHandler.intensionRelationalOperator(TypeExpr.GE)).isEqualTo(Operator.GEQ);
    }

    @Test void intensionRelationalOperator_greaterThan_mapsToGt() {
        assertThat(Xcsp3CallbackHandler.intensionRelationalOperator(TypeExpr.GT)).isEqualTo(Operator.GT);
    }

    @Test void intensionRelationalOperator_nonRelationalType_returnsNull() {
        assertThat(Xcsp3CallbackHandler.intensionRelationalOperator(TypeExpr.ADD)).isNull();
    }

    /**
     * {@code flip(GEQ)} is reachable only from a root {@code Operator} of {@code GEQ}, which in
     * turn only comes from {@code TypeExpr.GE} -- itself unreachable through real parsing (see
     * {@link #intensionRelationalOperator_greaterOrEqual_mapsToGeq}), so this needs the same direct
     * construction.
     */
    @Test void flip_geqBecomesLeq() {
        assertThat(Xcsp3CallbackHandler.flip(Operator.GEQ)).isEqualTo(Operator.LEQ);
    }

    /**
     * {@code flip(GT)} is reachable only from a root {@code Operator} of {@code GT}, which in turn
     * only comes from {@code TypeExpr.GT} -- itself unreachable through real parsing, same as
     * {@code GE} above.
     */
    @Test void flip_gtBecomesLt() {
        assertThat(Xcsp3CallbackHandler.flip(Operator.GT)).isEqualTo(Operator.LT);
    }

    /**
     * {@code flip(EQ)}/{@code flip(NEQ)} are unreachable for a different reason than GE/GT above:
     * {@code recognizeBinaryRelation} only calls {@code flip} from its leftVar-present branch, but
     * xcsp3-tools' canonizer always reorders eq/ne's own operands to place a compound
     * sub-expression (e.g. {@code add(x,3)}) before a bare variable -- confirmed empirically -- so
     * that branch never fires when the operator is EQ/NEQ; the rightVar-present branch (no flip)
     * handles that shape instead.
     */
    @Test void flip_eqStaysEq() {
        assertThat(Xcsp3CallbackHandler.flip(Operator.EQ)).isEqualTo(Operator.EQ);
    }

    @Test void flip_neqStaysNeq() {
        assertThat(Xcsp3CallbackHandler.flip(Operator.NEQ)).isEqualTo(Operator.NEQ);
    }

    /**
     * {@code eq(A,B)} between two bare symbolic constants (no variable operand at all) can't be
     * reached through real XCSP3 parsing -- {@code xcsp3-tools}' own {@code
     * CtrLoaderInteger.intension} NPEs first on the empty variable scope before {@link
     * Xcsp3CallbackHandler#buildCtrIntension(String, XVarSymbolic[], XNodeParent)} is ever entered,
     * so this needs the same direct-construction approach as the other unreachable branches above.
     */
    /**
     * A relational operator other than {@code EQ}/{@code NE} (e.g. {@code LT}) can't arise from
     * real symbolic parsing -- XCSP3-core symbolic instances only ever use {@code eq}/{@code ne} --
     * so this covers {@code buildCtrIntensionSymbolic}'s own operator-mismatch branch directly.
     */
    @Test void buildCtrIntensionSymbolic_nonEqNeOperator_throwsUnsupported() {
        XNodeLeaf<XVarSymbolic> left = new XNodeLeaf<>(TypeExpr.SYMBOL, "A");
        XNodeLeaf<XVarSymbolic> right = new XNodeLeaf<>(TypeExpr.SYMBOL, "B");
        XNodeParent<XVarSymbolic> tree = new XNodeParent<>(TypeExpr.LT, left, right);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.buildCtrIntension("c0", new XVarSymbolic[0], tree))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void buildCtrIntensionSymbolic_bothOperandsConstants_throwsUnsupported() {
        XNodeLeaf<XVarSymbolic> left = new XNodeLeaf<>(TypeExpr.SYMBOL, "A");
        XNodeLeaf<XVarSymbolic> right = new XNodeLeaf<>(TypeExpr.SYMBOL, "B");
        XNodeParent<XVarSymbolic> tree = new XNodeParent<>(TypeExpr.EQ, left, right);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.buildCtrIntension("c0", new XVarSymbolic[0], tree))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    /**
     * A non-leaf (compound) operand, e.g. a nested {@code not(...)}, can't be produced by real
     * symbolic parsing either -- {@code buildCtrIntensionSymbolic}'s own EQ/NE-only, arity-2 shape
     * check above only ever hands {@code symbolicOperand} the tree's direct children, and no bundled
     * instance nests a compound sub-expression there -- so this needs the same direct construction.
     */
    /**
     * A leaf of some type other than {@code VAR}/{@code SYMBOL} (e.g. a numeric {@code LONG} leaf)
     * can't arise from real symbolic parsing either -- {@code xcsp3-tools} only ever produces
     * {@code VAR}/{@code SYMBOL} leaves for a symbolic-variable {@code intension} tree -- so this
     * covers {@code symbolicOperand}'s own "leaf, but neither kind" fallthrough directly.
     */
    @Test void buildCtrIntensionSymbolic_nonSymbolNonVarLeafOperand_throwsUnsupported() {
        XNodeLeaf<XVarSymbolic> numericLeaf = new XNodeLeaf<>(TypeExpr.LONG, 5L);
        XNodeLeaf<XVarSymbolic> right = new XNodeLeaf<>(TypeExpr.SYMBOL, "B");
        XNodeParent<XVarSymbolic> tree = new XNodeParent<>(TypeExpr.EQ, numericLeaf, right);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.buildCtrIntension("c0", new XVarSymbolic[0], tree))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void buildCtrIntensionSymbolic_nonLeafOperand_throwsUnsupported() {
        XNodeLeaf<XVarSymbolic> innerLeaf = new XNodeLeaf<>(TypeExpr.SYMBOL, "A");
        XNodeParent<XVarSymbolic> nonLeaf = new XNodeParent<>(TypeExpr.NOT, innerLeaf);
        XNodeLeaf<XVarSymbolic> right = new XNodeLeaf<>(TypeExpr.SYMBOL, "B");
        XNodeParent<XVarSymbolic> tree = new XNodeParent<>(TypeExpr.EQ, nonLeaf, right);
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        assertThatThrownBy(() -> handler.buildCtrIntension("c0", new XVarSymbolic[0], tree))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }
}
