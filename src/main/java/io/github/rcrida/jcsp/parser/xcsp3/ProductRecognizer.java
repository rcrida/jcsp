package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductVariableConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The general-domain generalization of {@link BooleanProductChannelRecognizer}'s own {@code
 * eq(mul(a,b), X)} shape: recognizes {@code A op B} where one operand is a {@code mul(...)} of two
 * or more <em>distinct</em> variables and the other is a plain variable or constant, for {@code
 * EQ}/{@code LEQ}/{@code GEQ} -- the same operator set {@link ProductConstraint}/{@link
 * ProductVariableConstraint} themselves only narrow for (see their own Javadoc); recognizing
 * {@code LT}/{@code GT}/{@code NEQ} here would gain nothing over falling through to the generic
 * fallback, since their own {@code propagate} is a no-op for those regardless. Routes to {@link
 * ProductVariableConstraint} (variable target) or {@link ProductConstraint} (constant target)
 * instead of the generic {@link PredicateConstraint}.
 * <p>
 * {@code mul}'s arity is not restricted to two: {@link ProductConstraint}/{@link
 * ProductVariableConstraint} already accept any {@code Set<Variable<N>>} of factors regardless of
 * count, so a {@code mul(a,b,c,...)} with three or more distinct operands is just as recognizable
 * as the two-operand case -- the two-operand restriction an earlier version of this class had
 * (named {@code ProductOfPairRecognizer}) was purely a recognizer-side limitation, not a
 * constraint-side one, and was lifted once a three-plus-factor {@code mul} was confirmed to be
 * legal XCSP3 syntax with nothing in the target constraint classes standing in the way.
 * <p>
 * Still declines a <em>self</em>-product (the same variable appearing twice or more among the
 * factors, e.g. {@code eq(mul(x,x),y)}) -- unlike the arity restriction above, this one is a real
 * constraint-side limitation, not just a recognizer-side one: {@link ProductVariableConstraint#of}/
 * {@link ProductConstraint#of} both take a {@code Set<Variable<N>>}, which cannot represent one
 * variable used twice as a factor no matter how this recognizer resolves the tree (confirmed via a
 * real corpus instance, {@code LowAutocorrelation-015.xml.lzma}: an unguarded {@code Set.of(a, b)}
 * throws {@code IllegalArgumentException: duplicate element} the moment {@code a.equals(b)}).
 * Fixing this would need either a genuinely different constraint representation (e.g. a
 * multiset/list of factors) or a dedicated squaring constraint, not a recognizer change -- out of
 * scope here.
 * <p>
 * Registered after {@link BooleanProductChannelRecognizer} in {@link
 * Xcsp3CallbackHandler#recognizeConstraint}'s chain, so a boolean-domain {@code EQ} pair still
 * takes that recognizer's tighter {@link io.github.rcrida.jcsp.constraints.nary.MinVariableConstraint}
 * identity first. This class's own propagation is a strictly weaker no-op whenever a factor's
 * domain minimum isn't strictly positive -- every {@code {0,1}} domain included, per {@link
 * ProductConstraint}'s own restriction -- so falling through here for a boolean pair {@link
 * BooleanProductChannelRecognizer} already declined (a non-{@code EQ} operator) is still always
 * safe, just less propagated, never incorrect.
 * <p>
 * Only checks {@code mul(...)} as {@code tree.sons[0]}, not the reverse: confirmed by the same
 * empirical probe {@link BooleanProductChannelRecognizer} relies on that {@code mul}'s variable
 * operands always precede a bare variable/constant target in {@code xcsp3-tools}' canonical
 * ordering.
 */
final class ProductRecognizer implements ConstraintRecognizer {

    private static final Set<Operator> PRODUCT_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    private final Xcsp3CallbackHandler handler;

    ProductRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
        if (!PRODUCT_OPERATORS.contains(operator) || tree.sons.length != 2) {
            return Optional.empty();
        }
        if (!(tree.sons[0] instanceof XNodeParent<XVarInteger> mulNode) || mulNode.getType() != TypeExpr.MUL
                || mulNode.sons.length < 2) {
            return Optional.empty();
        }
        Set<Variable<Integer>> factors = new LinkedHashSet<>();
        for (XNode<XVarInteger> factorNode : mulNode.sons) {
            Optional<Variable<Integer>> factor = handler.asVariable(factorNode);
            if (factor.isEmpty() || !factors.add(factor.get())) {
                return Optional.empty();
            }
        }

        Optional<Variable<Integer>> target = handler.asVariable(tree.sons[1]);
        if (target.isPresent()) {
            return Optional.of(ProductVariableConstraint.of(factors, operator, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(tree.sons[1])
                .map(constant -> ProductConstraint.of(factors, operator, constant));
    }
}
