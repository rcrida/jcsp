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
 * mod} auxiliary. The {@code dist(...)} node may be either operand: {@code xcsp3-tools}' canonizer
 * rewrites {@code gt}/{@code ge} as a {@code le} with the operands swapped (e.g. {@code
 * gt(dist(a,b),5)} arrives as {@code le(6,dist(a,b))}), so a second-operand {@code dist} is matched
 * with the operator {@linkplain Xcsp3CallbackHandler#flip flipped}. Called on both a top-level node and a non-top-level one (e.g. from {@link
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
        int distIndex = isDistanceOfPair(tree.sons[0]) ? 0 : isDistanceOfPair(tree.sons[1]) ? 1 : -1;
        if (distIndex < 0) return Optional.empty();
        XNode<XVarInteger> distNode = tree.sons[distIndex];
        XNode<XVarInteger> targetNode = tree.sons[1 - distIndex];
        Operator relation = distIndex == 0 ? operator : Xcsp3CallbackHandler.flip(operator);
        Optional<Variable<Integer>> a = handler.resolveVariable(distNode.sons[0]);
        if (a.isEmpty()) return Optional.empty();
        Optional<Variable<Integer>> b = handler.resolveVariable(distNode.sons[1]);
        if (b.isEmpty()) return Optional.empty();

        Optional<Variable<Integer>> target = handler.asVariable(targetNode);
        if (target.isPresent()) {
            return Optional.of(AbsoluteDifferenceVariableConstraint.of(a.get(), b.get(), relation, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(targetNode)
                .map(constant -> AbsoluteDifferenceConstraint.of(a.get(), b.get(), relation, constant));
    }

    private static boolean isDistanceOfPair(XNode<XVarInteger> node) {
        return node.getType() == TypeExpr.DIST && node.sons.length == 2;
    }
}
