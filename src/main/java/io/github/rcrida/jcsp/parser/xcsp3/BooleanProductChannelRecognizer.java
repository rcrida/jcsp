package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.MinVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductVariableConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;
import java.util.Set;

/**
 * Recognizes {@code eq(mul(a,b), target)} -- confirmed empirically (via a temporary probe against
 * {@code Bibd-sc-06-050-25-03-10.xml.lzma}'s real "channeling" constraints, {@code s = x*y}) to be
 * the only operand order {@code xcsp3-tools}' canonizer produces for a two-variable {@code mul}
 * beside a bare variable -- the same complexity-based reordering the handler's own {@code
 * add(var,const)} recognition relies on. When both {@code mul} operands are declared with domain
 * exactly {@code {0,1}} (checked via the handler's own bounds registry, sufficient on its own:
 * min=0 and max=1 for an integer domain leaves no room for anything but exactly {@code {0,1}}),
 * {@code a*b} and {@code min(a,b)} are the same numeric value for every value in that domain -- an
 * exact identity, not an approximation -- so this routes through {@link MinVariableConstraint}
 * instead of the generic {@link PredicateConstraint}, which has no propagation at all (only
 * checked once every variable is assigned). This is exactly the "boolean AND channeling" idiom
 * XCSP3's {@code group}/template style produces when reformulating a product of two 0/1 variables
 * as an auxiliary variable, common in CSPLib designs (e.g. a BIBD's pairwise block intersection
 * indicator). {@link ProductVariableConstraint} isn't used here even though it would also be
 * numerically correct -- its own interval-arithmetic propagation explicitly bails out whenever a
 * factor's domain minimum is {@code <= 0} (see its own Javadoc), which is every 0/1 variable, so
 * it would silently give zero propagation on exactly the shape this class exists to strengthen.
 * Declines whenever either {@code mul} operand's domain isn't confirmed {@code {0,1}} -- the
 * identity doesn't hold for general integer domains (e.g. {@code min(2,3) = 2} but {@code 2*3 =
 * 6}) -- so recognition failure here is always safe, just less propagated, never incorrect, the
 * same contract every recognizer in this package documents. Also declines a self-product ({@code
 * a} and {@code b} the same variable, e.g. {@code eq(mul(x,x),y)}) -- {@link
 * MinVariableConstraint#of} takes a {@code Set<Variable<N>>}, which can't represent one variable
 * used twice as a factor (confirmed via a real corpus instance, {@code
 * LowAutocorrelation-015.xml.lzma}: an unguarded {@code Set.of(a, b)} throws {@code
 * IllegalArgumentException: duplicate element} the moment {@code a.equals(b)}) -- this is a
 * limitation of the {@code Set}-based factors representation itself, shared by every public
 * {@code productConstraint} entry point, not something worth working around just for this one
 * recognizer.
 * <p>
 * Package-private class members ({@code recognize}, unavoidably {@code public} to satisfy {@link
 * ConstraintRecognizer}) are exercised directly by a white-box test for one genuinely-unreachable
 * branch: {@code tree.sons[0] instanceof XNodeParent} being {@code false} can't occur through any
 * real, parseable XCSP3 file now that {@link GroundRelationRecognizer} is registered ahead of this
 * class in {@link Xcsp3CallbackHandler#recognizeConstraint} -- every {@code eq}-vs-constant or
 * {@code eq}-vs-variable shape with a leaf {@code tree.sons[0]} is intercepted there first, and
 * {@code xcsp3-tools}' canonizer always places a compound operand ahead of a leaf for {@code eq}
 * otherwise (confirmed empirically); a genuinely variable-free {@code eq(3,5)} top-level intension
 * isn't even parseable ({@code xcsp3-tools} itself throws a {@code NullPointerException} for one).
 */
final class BooleanProductChannelRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    BooleanProductChannelRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        if (tree.getType() != TypeExpr.EQ || tree.sons.length != 2) return Optional.empty();
        if (!(tree.sons[0] instanceof XNodeParent<XVarInteger> mulNode)
                || mulNode.getType() != TypeExpr.MUL || mulNode.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> a = handler.asVariable(mulNode.sons[0]);
        Optional<Variable<Integer>> b = handler.asVariable(mulNode.sons[1]);
        Optional<Variable<Integer>> target = handler.asVariable(tree.sons[1]);
        if (a.isEmpty() || b.isEmpty() || target.isEmpty() || a.get().equals(b.get())
                || !isBooleanDomain(a.get()) || !isBooleanDomain(b.get())) {
            return Optional.empty();
        }
        return Optional.of(MinVariableConstraint.of(Set.of(a.get(), b.get()), Operator.EQ, target.get()));
    }

    // No null check on the lookup: every Variable<Integer> reachable via asVariable() came from
    // the handler's own variable registry, populated in lockstep with its bounds registry, so a
    // variable present in one is guaranteed present in the other too.
    private boolean isBooleanDomain(Variable<Integer> variable) {
        return Xcsp3CallbackHandler.isBooleanBounds(handler.boundsOf(variable));
    }
}
