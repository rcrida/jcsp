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

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The general-domain generalization of {@link BooleanProductChannelRecognizer}'s own {@code
 * eq(mul(a,b), X)} shape: recognizes {@code A op B} where one operand is a two-variable {@code
 * mul(a,b)} and the other is a plain variable or constant, for {@code EQ}/{@code LEQ}/{@code GEQ}
 * -- the same operator set {@link ProductConstraint}/{@link ProductVariableConstraint} themselves
 * only narrow for (see their own Javadoc); recognizing {@code LT}/{@code GT}/{@code NEQ} here
 * would gain nothing over falling through to the generic fallback, since their own {@code
 * propagate} is a no-op for those regardless. Routes to {@link ProductVariableConstraint}
 * (variable target) or {@link ProductConstraint} (constant target) instead of the generic {@link
 * PredicateConstraint}.
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
 * Same operand-order restriction as {@link BooleanProductChannelRecognizer}, confirmed by the same
 * empirical probe: {@code mul}'s two variable operands always precede a bare variable/constant
 * target in {@code xcsp3-tools}' canonical ordering. Also declines a self-product ({@code a} and
 * {@code b} the same variable, e.g. {@code eq(mul(x,x),y)}) for the same reason {@link
 * BooleanProductChannelRecognizer} does -- {@link ProductVariableConstraint#of}/{@link
 * ProductConstraint#of} both take a {@code Set<Variable<N>>}, which can't represent one variable
 * used twice as a factor; confirmed via a real corpus instance ({@code
 * LowAutocorrelation-015.xml.lzma}) that this shape actually occurs, throwing {@code
 * IllegalArgumentException: duplicate element} from an unguarded {@code Set.of(a, b)} before this
 * check was added.
 */
final class ProductOfPairRecognizer implements ConstraintRecognizer {

    private static final Set<Operator> PRODUCT_OF_PAIR_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    private final Xcsp3CallbackHandler handler;

    ProductOfPairRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
        if (!PRODUCT_OF_PAIR_OPERATORS.contains(operator)) {
            return Optional.empty();
        }
        if (tree.sons.length != 2) {
            return Optional.empty();
        }
        if (!(tree.sons[0] instanceof XNodeParent<XVarInteger> mulNode) || mulNode.getType() != TypeExpr.MUL) {
            return Optional.empty();
        }
        if (mulNode.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> a = handler.asVariable(mulNode.sons[0]);
        if (a.isEmpty()) return Optional.empty();
        Optional<Variable<Integer>> b = handler.asVariable(mulNode.sons[1]);
        if (b.isEmpty()) return Optional.empty();
        if (a.get().equals(b.get())) return Optional.empty();

        Optional<Variable<Integer>> target = handler.asVariable(tree.sons[1]);
        if (target.isPresent()) {
            return Optional.of(ProductVariableConstraint.of(Set.of(a.get(), b.get()), operator, target.get()));
        }
        return Xcsp3CallbackHandler.asConstant(tree.sons[1])
                .map(constant -> ProductConstraint.of(Set.of(a.get(), b.get()), operator, constant));
    }
}
