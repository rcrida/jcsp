package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.SquareVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductVariableConstraint;
import io.github.rcrida.jcsp.constraints.unary.SquareConstraint;
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
 * A two-factor <em>self</em>-product ({@code mul(x,x)}, e.g. {@code eq(mul(x,x),y)}) routes to
 * {@link SquareVariableConstraint}/{@link SquareConstraint} instead: {@link
 * ProductVariableConstraint#of}/{@link ProductConstraint#of} both take a {@code Set<Variable<N>>},
 * which cannot represent one variable used twice as a factor (confirmed via a real corpus
 * instance, {@code LowAutocorrelation-015.xml.lzma}: an unguarded {@code Set.of(a, b)} throws
 * {@code IllegalArgumentException: duplicate element} the moment {@code a.equals(b)}) -- a real
 * constraint-side limitation of the general {@code Set}-based factor representation, not fixable
 * by any recognizer-side change, so this dispatches to the dedicated squaring constraints instead
 * of trying to force it through {@code ProductConstraint}'s own machinery. A self-product with
 * three or more factors (e.g. {@code mul(x,x,x)}, a cube) still declines -- {@code Square*}
 * covers exactly the two-factor case confirmed needed; XCSP3's own {@code pow(x,k)} operator is
 * the general mechanism for higher powers, a separate (currently unrecognized) shape.
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
 * Checks {@code mul(...)} at both {@code tree.sons[0]} and {@code tree.sons[1]}: {@code mul}'s
 * variable operands precede a bare variable/constant target in {@code xcsp3-tools}' canonical
 * ordering for an originally-written {@code eq}/{@code le}/{@code lt}, confirmed by the same
 * empirical probe {@link BooleanProductChannelRecognizer} relies on -- but {@code ge}/{@code gt}
 * are always rewritten to {@code le}/{@code lt} with operands <em>swapped</em> (e.g. {@code
 * ge(mul(x,x),4)} arrives here as {@code le(4,mul(x,x))}), which moves the compound side to {@code
 * sons[1]} regardless of its complexity. Confirmed via a direct probe against a real parsed tree
 * ({@code ge(mul(x,x),4)} on domain {@code -5..5} produced {@code LE} with {@code son0=LONG,
 * son1=MUL}), meaning {@link Operator#GEQ} was previously unreachable here whenever the source used
 * {@code ge}/{@code gt} -- the {@code sons[1]}-as-{@code mul} case flips the operator (mirroring
 * {@link GroundRelationRecognizer}'s own dual-order handling) since {@code target <op> mulResult}
 * is the reverse relation of {@code mulResult <op> target}.
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
        if (tree.sons[0] instanceof XNodeParent<XVarInteger> mulNode && mulNode.getType() == TypeExpr.MUL) {
            Optional<Constraint> result = recognizeAgainstMul(mulNode, operator, tree.sons[1]);
            if (result.isPresent()) return result;
        }
        if (tree.sons[1] instanceof XNodeParent<XVarInteger> mulNode && mulNode.getType() == TypeExpr.MUL) {
            return recognizeAgainstMul(mulNode, Xcsp3CallbackHandler.flip(operator), tree.sons[0]);
        }
        return Optional.empty();
    }

    private Optional<Constraint> recognizeAgainstMul(
            XNodeParent<XVarInteger> mulNode, Operator operator, XNode<XVarInteger> targetSide) {
        if (mulNode.sons.length < 2) {
            return Optional.empty();
        }
        if (mulNode.sons.length == 2) {
            Optional<Constraint> square = recognizeSelfProduct(mulNode, operator, targetSide);
            if (square.isPresent()) return square;
        }
        Set<Variable<Integer>> factors = new LinkedHashSet<>();
        for (XNode<XVarInteger> factorNode : mulNode.sons) {
            Optional<Variable<Integer>> factor = handler.asVariable(factorNode);
            if (factor.isEmpty() || !factors.add(factor.get())) {
                return Optional.empty();
            }
        }

        Optional<Variable<Integer>> target = handler.asVariable(targetSide);
        if (target.isPresent()) {
            return Optional.of(ProductVariableConstraint.of(factors, operator, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(targetSide)
                .map(constant -> ProductConstraint.of(factors, operator, constant));
    }

    /**
     * Recognizes {@code mul(x,x)} specifically -- both of {@code mulNode}'s two sons resolve to
     * the very same variable -- routing to {@link SquareVariableConstraint}/{@link SquareConstraint}
     * instead of the general {@code Set}-based factor path, which can't represent a duplicated
     * factor at all. Declines (falling through to the general path above, which will itself decline
     * once it hits the duplicate) for any two-factor {@code mul} that isn't a genuine self-product.
     */
    private Optional<Constraint> recognizeSelfProduct(
            XNodeParent<XVarInteger> mulNode, Operator operator, XNode<XVarInteger> targetSide) {
        Optional<Variable<Integer>> a = handler.asVariable(mulNode.sons[0]);
        if (a.isEmpty()) return Optional.empty();
        Optional<Variable<Integer>> b = handler.asVariable(mulNode.sons[1]);
        if (b.isEmpty() || !a.get().equals(b.get())) return Optional.empty();
        Variable<Integer> operand = a.get();

        Optional<Variable<Integer>> target = handler.asVariable(targetSide);
        if (target.isPresent()) {
            return Optional.of(SquareVariableConstraint.of(operand, operator, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(targetSide)
                .map(constant -> SquareConstraint.of(operand, operator, constant));
    }
}
