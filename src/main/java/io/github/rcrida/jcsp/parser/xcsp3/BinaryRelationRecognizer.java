package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

/**
 * The bare-variable-pair and {@code var op (var+const)} cases are tried first since neither needs
 * an auxiliary variable ({@link BinaryOffsetConstraint} represents {@code var+const} directly).
 * Only once neither applies does this fall back to the more general {@link
 * Xcsp3CallbackHandler#resolveVariable} on both sides -- e.g. {@code eq(div(x,6), div(y,6))},
 * {@code eq(div(x,6), y)}. That general fallback deliberately declines whenever either side is a
 * bare constant (checked via {@link Xcsp3CallbackHandler#asConstant}): {@link
 * Xcsp3CallbackHandler#resolveVariable} would happily resolve a bare constant too (via its own
 * constant-variable case), which would otherwise let this method needlessly beat {@link
 * GroundRelationRecognizer} to a ground-relation shape (e.g. {@code eq(x,5)}), producing a
 * wasteful extra auxiliary variable where {@code UnaryComparatorConstraint} already handles it
 * directly.
 */
final class BinaryRelationRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    BinaryRelationRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(node.getType());
        if (operator == null || node.sons.length != 2) return Optional.empty();
        XNode<XVarInteger> left = node.sons[0];
        XNode<XVarInteger> right = node.sons[1];

        Optional<Variable<Integer>> leftVar = handler.asVariable(left);
        Optional<Variable<Integer>> rightVar = handler.asVariable(right);
        if (leftVar.isPresent() && rightVar.isPresent()) {
            return Optional.of(BinaryComparatorConstraint.of(leftVar.get(), operator, rightVar.get()));
        }
        // "var <op> var+k" rearranges to "var+k <flip(op)> var" to match BinaryOffsetConstraint's
        // fixed "left + offset <op> right" shape, which only ever applies the offset to the left side.
        if (leftVar.isPresent()) {
            Optional<Constraint> offset = handler.asVariablePlusConstant(right)
                    .<Constraint>map(vk -> BinaryOffsetConstraint.of(
                            vk.variable(), vk.offset(), Xcsp3CallbackHandler.flip(operator), leftVar.get()));
            if (offset.isPresent()) return offset;
        }
        if (rightVar.isPresent()) {
            Optional<Constraint> offset = handler.asVariablePlusConstant(left)
                    .<Constraint>map(vk -> BinaryOffsetConstraint.of(vk.variable(), vk.offset(), operator, rightVar.get()));
            if (offset.isPresent()) return offset;
        }
        if (Xcsp3CallbackHandler.asConstant(left).isPresent() || Xcsp3CallbackHandler.asConstant(right).isPresent()) {
            return Optional.empty();
        }
        return handler.resolveVariable(left).flatMap(l -> handler.resolveVariable(right)
                .map(r -> BinaryComparatorConstraint.of(l, operator, r)));
    }
}
