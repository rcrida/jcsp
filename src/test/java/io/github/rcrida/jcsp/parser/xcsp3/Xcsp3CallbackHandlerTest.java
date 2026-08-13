package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.xcsp.common.Condition;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.Types.TypeFlag;

import java.util.Map;
import java.util.Set;

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
}
