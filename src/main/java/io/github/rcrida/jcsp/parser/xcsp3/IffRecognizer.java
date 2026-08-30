package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

/**
 * Recognizes {@code iff(X, Y)} where both {@code X} and {@code Y} independently recognize as
 * standalone relations via {@link #recognizeRelation} (deliberately narrower than the handler's
 * own full {@link Xcsp3CallbackHandler#recognizeConstraint} chain -- just a bare binary/ground
 * relation, matching this recognizer's original, still corpus-sufficient scope) -- routes onto a
 * pair of fresh {@link ReifiedConstraint}-backed indicators instead of the generic {@link
 * PredicateConstraint}, which has no propagation at all. Neither operand of an {@code iff} is a
 * plain boolean variable the way ordinary top-level reification handles, so this can't route
 * through a single {@code reifyConstraint} call the way a top-level {@code reifiedBy} attribute
 * does -- it needs its own decomposition. Confirmed via a real competition instance ({@code
 * Mario-easy-4.xml.lzma}'s {@code iff(eq(s[i],i), eq(g[i],0))} gold-earning rule, 13 occurrences
 * all previously falling to {@link PredicateConstraint}). Recognition failure on either side is
 * always safe, just less propagated -- falls through to the rest of the chain unchanged.
 * <p>
 * Builds one fresh boolean indicator per side, each <em>unconditionally</em> reified (hard, always
 * added directly -- never wrapped, regardless of whether the {@code iff} itself is reified)
 * against its own operand: {@code leftIndicator <-> left}, {@code rightIndicator <-> right}. With
 * both indicators now exactly tracking their own operand's truth value unconditionally, {@code
 * left <-> right} reduces to a plain {@code leftIndicator == rightIndicator} over two ordinary
 * boolean variables -- a {@link BinaryComparatorConstraint} (generic over any {@code Comparable},
 * including {@link Boolean}), the same class {@link BinaryRelationRecognizer} already uses for a
 * bare {@code var op var} shape -- which the handler's own {@code addOrReify} then adds directly
 * or reifies against the constraint's own indicator exactly like any other recognized constraint.
 * <p>
 * A single shared indicator reified against <em>both</em> operands directly (an earlier version of
 * this design) is unsound once the pair itself needs reifying: nothing then forces the shared
 * indicator to the value that actually makes {@code left <-> right} hold, since it's otherwise
 * completely free -- e.g. with {@code left = right = true}, the shared indicator could still be
 * assigned {@code false} in a candidate solution (satisfying neither reification), silently
 * forcing the outer indicator to a wrong value. Caught by a real reified-{@code iff} test asserting
 * the outer indicator's truth value, not merely inferred. Splitting into two indicators with their
 * own <em>unconditional</em> reifications avoids this entirely: neither indicator has any degree
 * of freedom beyond exactly mirroring its own operand, in either the reified or unreified case.
 * <p>
 * Each indicator's name is derived from its own operand's variable set (not the constraint's own
 * XCSP3 {@code id}): many instances of this pattern share one XCSP3 {@code <group>}-templated
 * {@code id} (confirmed on {@code Mario-easy-4.xml.lzma}'s 13 houses), which would otherwise
 * collide.
 */
final class IffRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;
    private final BinaryRelationRecognizer binaryRelation;
    private final GroundRelationRecognizer groundRelation;

    IffRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
        this.binaryRelation = new BinaryRelationRecognizer(handler);
        this.groundRelation = new GroundRelationRecognizer(handler);
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        if (node.getType() != TypeExpr.IFF || node.sons.length != 2) return Optional.empty();
        Optional<Constraint> left = recognizeRelation(node.sons[0]);
        if (left.isEmpty()) return Optional.empty();
        Optional<Constraint> right = recognizeRelation(node.sons[1]);
        if (right.isEmpty()) return Optional.empty();

        Variable<Boolean> leftIndicator = handler.newReifiedIndicator(
                Xcsp3CallbackHandler.indicatorName("L", left.get()), left.get());
        Variable<Boolean> rightIndicator = handler.newReifiedIndicator(
                Xcsp3CallbackHandler.indicatorName("R", right.get()), right.get());
        return Optional.of(BinaryComparatorConstraint.of(leftIndicator, Operator.EQ, rightIndicator));
    }

    private Optional<Constraint> recognizeRelation(XNode<XVarInteger> node) {
        return binaryRelation.recognize(node).or(() -> groundRelation.recognize(node));
    }
}
