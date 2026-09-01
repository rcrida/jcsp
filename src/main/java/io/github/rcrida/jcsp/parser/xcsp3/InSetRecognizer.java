package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryInSetConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Recognizes {@code in(v, set(c1,...,cn))}/{@code notin(v, set(c1,...,cn))} where every {@code
 * set(...)} member is a bare constant -- e.g. {@code in(x[0], set(1,2))} -- routing onto {@link
 * UnaryInSetConstraint} instead of the generic {@code PredicateConstraint}. Declines whenever any
 * member isn't a constant (e.g. a variable or a nested expression like {@code sub(x,1)}, both
 * legal XCSP3 {@code set(...)} member shapes {@link IntensionExpressionEvaluator} still evaluates
 * per-assignment): such a set's membership genuinely depends on other variables' current values
 * too, so the whole relation isn't unary at all once that happens -- the generic evaluator already
 * covers it correctly, just without propagation.
 * <p>
 * The constant-only check on the {@code set(...)} side is done before resolving the variable side
 * (via {@link Xcsp3CallbackHandler#resolveVariable}, not the narrower {@code asVariable}, so a
 * compound operand like {@code div}/{@code mod}/{@code add} works here too) specifically so a
 * decline never needs to materialize an auxiliary first -- the same ordering {@link
 * GroundRelationRecognizer} uses for its own constant-side-first check.
 * <p>
 * Confirmed via the bundled XCSP3 competition corpus (a fallback-histogram scan): {@code
 * in(var,set(...))} against a literal constant set was the largest remaining {@code
 * PredicateConstraint} bucket after {@code div}/{@code mod} of {@code add}, three occurrences.
 */
final class InSetRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    InSetRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        boolean positive;
        if (tree.getType() == TypeExpr.IN) {
            positive = true;
        } else if (tree.getType() == TypeExpr.NOTIN) {
            positive = false;
        } else {
            return Optional.empty();
        }
        if (tree.sons.length != 2 || tree.sons[1].getType() != TypeExpr.SET) {
            return Optional.empty();
        }

        Set<Integer> values = new LinkedHashSet<>();
        for (XNode<XVarInteger> member : tree.sons[1].sons) {
            Optional<Integer> constant = Xcsp3CallbackHandler.asConstant(member);
            if (constant.isEmpty()) return Optional.empty();
            values.add(constant.get());
        }

        return handler.resolveVariable(tree.sons[0])
                .map(variable -> UnaryInSetConstraint.of(variable, values, positive));
    }
}
