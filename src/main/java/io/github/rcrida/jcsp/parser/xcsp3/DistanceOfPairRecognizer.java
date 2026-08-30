package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.nary.AbsoluteDifferenceVariableConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

/**
 * Recognizes {@code A op B} where one side is a two-variable {@code dist(a,b)} node and the other
 * a plain variable or constant -- e.g. {@code eq(dist(a,b), c)} -- and routes it onto {@link
 * AbsoluteDifferenceVariableConstraint} (variable target) or {@link AbsoluteDifferenceConstraint}
 * (constant target) instead of the generic {@code PredicateConstraint}. Unlike {@link
 * DistancePairComparisonRecognizer}, this is a plain leaf-relation recognizer for the target side
 * (no auxiliary needed there -- the comparison's own right-hand side already <em>is</em> the
 * target/bound), though {@code dist}'s own two operands ({@code a}/{@code b}) are resolved via
 * {@link Xcsp3CallbackHandler#resolveVariable}, which may itself materialize a {@code div}/{@code
 * mod} auxiliary. Same operand-order restriction as every other {@code dist}/{@code mul}/{@code
 * add} recognizer in this package: {@code xcsp3-tools}' canonizer always places the compound
 * {@code dist(...)} node first against a plain variable/constant target (confirmed via the same
 * corpus scan that motivated this class), so a defensive reverse-order check would be permanently
 * dead code. Called on both a top-level node and a non-top-level one (e.g. from {@link
 * AndRecognizer}/{@link OrRecognizer}'s own recursive descent into an {@code and}/{@code or}
 * child), hence the plain {@link XNode} parameter type rather than {@link XNodeParent}.
 */
final class DistanceOfPairRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    DistanceOfPairRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
        if (operator == null || tree.sons.length != 2) return Optional.empty();
        if (!(tree.sons[0] instanceof XNodeParent<XVarInteger> distNode)
                || distNode.getType() != TypeExpr.DIST || distNode.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> a = handler.resolveVariable(distNode.sons[0]);
        if (a.isEmpty()) return Optional.empty();
        Optional<Variable<Integer>> b = handler.resolveVariable(distNode.sons[1]);
        if (b.isEmpty()) return Optional.empty();

        Optional<Variable<Integer>> target = handler.asVariable(tree.sons[1]);
        if (target.isPresent()) {
            return Optional.of(AbsoluteDifferenceVariableConstraint.of(a.get(), b.get(), operator, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(tree.sons[1])
                .map(constant -> AbsoluteDifferenceConstraint.of(a.get(), b.get(), operator, constant));
    }
}
