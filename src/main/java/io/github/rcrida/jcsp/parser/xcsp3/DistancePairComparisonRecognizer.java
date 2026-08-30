package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

/**
 * Recognizes {@code A op B} where both {@code A} and {@code B} are a two-variable {@code
 * dist(...)} node -- e.g. {@code ne(dist(a,b), dist(c,d))} -- and routes it onto {@link
 * BinaryComparatorConstraint} over two fresh {@code |x - y|} auxiliaries (see {@link
 * Xcsp3CallbackHandler#distanceAuxiliary}) instead of the generic {@code PredicateConstraint}.
 * Unlike every other registered recognizer, this one needs to add two <em>unconditional</em>
 * auxiliary-linking constraints directly against the handler's builder first (the same
 * "definitional, never itself the loaded constraint" treatment {@link IffRecognizer}'s own
 * indicators get, via {@link Xcsp3CallbackHandler#distanceAuxiliary} itself) -- only the derived
 * {@code auxLeft <op> auxRight} relation is what this method returns, so reifying the whole thing
 * still reifies only the comparison, not the (always true) auxiliary definitions.
 * <p>
 * By far the most common {@code dist}-based shape in the bundled XCSP3 competition corpus (a
 * single instance, a Costas-array-style "every pairwise distance is distinct" encoding,
 * contributes hundreds of {@code ne(dist(...),dist(...))} clauses alone) -- previously falling all
 * the way to the generic, unpropagated {@code PredicateConstraint}. Recognition failure on either
 * side (one side isn't a two-variable {@code dist(...)}, or its own operands aren't plain
 * variables) is always safe, just less propagated, falling through to {@link
 * DistanceOfPairRecognizer}/the generic fallback unchanged.
 */
final class DistancePairComparisonRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    DistancePairComparisonRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    private record DistancePairOperand(Variable<Integer> a, Variable<Integer> b) {}

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(node.getType());
        if (operator == null || node.sons.length != 2) return Optional.empty();
        Optional<DistancePairOperand> left = asDistancePairOperand(node.sons[0]);
        if (left.isEmpty()) return Optional.empty();
        Optional<DistancePairOperand> right = asDistancePairOperand(node.sons[1]);
        if (right.isEmpty()) return Optional.empty();

        Variable<Integer> auxLeft = handler.distanceAuxiliary(left.get().a(), left.get().b());
        Variable<Integer> auxRight = handler.distanceAuxiliary(right.get().a(), right.get().b());
        return Optional.of(BinaryComparatorConstraint.of(auxLeft, operator, auxRight));
    }

    private Optional<DistancePairOperand> asDistancePairOperand(XNode<XVarInteger> node) {
        if (!(node instanceof XNodeParent<XVarInteger> distNode)
                || distNode.getType() != TypeExpr.DIST || distNode.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> a = handler.resolveVariable(distNode.sons[0]);
        if (a.isEmpty()) return Optional.empty();
        Optional<Variable<Integer>> b = handler.resolveVariable(distNode.sons[1]);
        if (b.isEmpty()) return Optional.empty();
        return Optional.of(new DistancePairOperand(a.get(), b.get()));
    }
}
