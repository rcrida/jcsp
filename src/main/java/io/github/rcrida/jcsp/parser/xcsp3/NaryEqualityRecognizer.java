package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.nary.AndConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Recognizes {@code eq(a1, a2, ..., an)} with three or more operands -- XCSP3's {@code eq} is a
 * generalized n-ary "all equal" (see {@link IntensionExpressionEvaluator}'s own {@code allEqual}
 * fallback evaluation), not just the binary relation {@link GroundRelationRecognizer}/{@link
 * BinaryRelationRecognizer} already handle for the two-operand case. Each operand is resolved via
 * {@link Xcsp3CallbackHandler#resolveVariable} -- a bare variable, a bare constant (wrapped in a
 * memoized singleton-domain auxiliary the same way every other {@code resolveVariable} caller
 * gets one for free), or a compound expression like {@code add}/{@code div}/{@code mod} -- then
 * chained into {@code n-1} consecutive pairwise {@link BinaryComparatorConstraint}s wrapped in one
 * {@link AndConstraint}, the same "consecutive pairwise chain" decomposition {@code lex} uses for
 * more than two lists and {@link io.github.rcrida.jcsp.constraints.nary.OrderedConstraint}'s own
 * binary decomposition uses generally -- sound because equality is transitive, so a consecutive
 * chain implies every pair is equal, not just adjacent ones.
 * <p>
 * Declining any one operand's resolution declines the whole node, always safe, just less
 * propagated, per this package's shared recognizer contract. Explicitly restricted to three or
 * more operands (not two) so the common two-operand case keeps routing through {@link
 * GroundRelationRecognizer}/{@link BinaryRelationRecognizer}'s own tighter, single-constraint
 * results (a direct {@link
 * io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint}/{@link
 * BinaryComparatorConstraint} rather than a one-conjunct {@link AndConstraint}) -- this class is
 * never reached for a two-operand {@code eq} anyway, since {@code recognizeConstraint} tries those
 * two recognizers first and stops at the first success, but the explicit arity guard keeps this
 * class's own purpose (the genuinely n-ary case) unambiguous.
 * <p>
 * Added for XCSP3's {@code or(...)} literal shape confirmed via a corpus fallback-histogram scan:
 * {@code or(eq(add(x[0],1),x[299]), eq(x[0],x[299],299))} -- the first disjunct already resolves
 * via {@link BinaryRelationRecognizer}'s own {@code var+const} handling, but the second, a genuine
 * three-operand {@code eq}, had no recognizer at all before this one, so {@link OrRecognizer}'s own
 * n-ary fallback (which dispatches each disjunct through the full recognizer chain) declined the
 * whole node on that one unresolvable disjunct.
 */
final class NaryEqualityRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    NaryEqualityRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        if (tree.getType() != TypeExpr.EQ || tree.sons.length < 3) {
            return Optional.empty();
        }
        List<Variable<Integer>> operands = new ArrayList<>(tree.sons.length);
        for (XNode<XVarInteger> son : tree.sons) {
            Optional<Variable<Integer>> resolved = handler.resolveVariable(son);
            if (resolved.isEmpty()) return Optional.empty();
            operands.add(resolved.get());
        }
        Set<Constraint> conjuncts = new LinkedHashSet<>();
        for (int i = 0; i < operands.size() - 1; i++) {
            conjuncts.add(BinaryComparatorConstraint.of(operands.get(i), Operator.EQ, operands.get(i + 1)));
        }
        return Optional.of(AndConstraint.of(conjuncts));
    }
}
