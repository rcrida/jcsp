package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.MaxVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinVariableConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Recognizes {@code eq(min(a,b,...),t)} and {@code eq(max(a,b,...),t)} over plain variables as a
 * {@link MinVariableConstraint}/{@link MaxVariableConstraint} with target {@code t}. A repeated
 * operand is kept once, which leaves the extremum unchanged. Declines when an operand is not a
 * plain variable, when fewer than two distinct operands remain, or when {@code t} is itself an
 * operand.
 */
final class ExtremumRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    ExtremumRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        if (tree.getType() != TypeExpr.EQ || tree.sons.length != 2) return Optional.empty();
        if (!(tree.sons[0] instanceof XNodeParent<XVarInteger> extremum)
                || (extremum.getType() != TypeExpr.MIN && extremum.getType() != TypeExpr.MAX)) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> target = handler.asVariable(tree.sons[1]);
        if (target.isEmpty()) return Optional.empty();
        Set<Variable<Integer>> operands = new LinkedHashSet<>();
        for (XNode<XVarInteger> son : extremum.sons) {
            Optional<Variable<Integer>> operand = handler.asVariable(son);
            if (operand.isEmpty()) return Optional.empty();
            operands.add(operand.get());
        }
        if (operands.size() < 2 || operands.contains(target.get())) return Optional.empty();
        return Optional.of(extremum.getType() == TypeExpr.MIN
                ? MinVariableConstraint.of(operands, Operator.EQ, target.get())
                : MaxVariableConstraint.of(operands, Operator.EQ, target.get()));
    }
}
