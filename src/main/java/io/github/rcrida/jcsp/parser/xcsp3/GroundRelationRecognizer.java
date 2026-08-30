package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;

/**
 * Matches a bare {@code eq}/{@code ne}/{@code le}/{@code lt} between a variable and a constant --
 * no {@code add(...)} wrapper, the shape {@code asVariablePlusConstant} doesn't cover. Reuses
 * {@link Xcsp3CallbackHandler#LITERAL_OPERATORS} (the same operator set {@link OrRecognizer}'s own
 * literal recognition accepts) -- {@code ge}/{@code gt} are excluded for the identical confirmed-
 * canonizer reason documented there.
 * <p>
 * Checks <em>both</em> operand orders, unlike every other "ground" recognizer in this package
 * (e.g. {@link OrRecognizer}'s own literal recognition): {@code eq}/{@code ne} are confirmed to
 * always canonicalize variable-first (the guarantee those other methods rely on), but {@code
 * le}/{@code lt} are not -- confirmed via a real corpus instance ({@code MagicSequence}-style
 * {@code iff(le(0,p[i]),le(0,s[i]))} clauses): the constant genuinely stays first when that's how
 * the relation's own direction was originally written (only a {@code ge}/{@code gt} rewrite forces
 * a swap, and only because eliminating {@code ge}/{@code gt} requires one). A constant-first match
 * reinterprets the relation from the variable's own perspective via {@link
 * Xcsp3CallbackHandler#flip}: {@code le(0, p[i])} ({@code 0 <= p[i]}) becomes {@code
 * UnaryComparatorConstraint.of(p[i], GEQ, 0)} ({@code p[i] >= 0}) -- the same operand-swap {@link
 * BinaryRelationRecognizer} already applies for its own {@code var op (var+const)} case.
 * <p>
 * Routes every operator through {@link UnaryComparatorConstraint} uniformly, {@code EQ}/{@code
 * NEQ} included -- real discrete narrowing and a real {@code explainInfeasible}, not just a
 * bounded-domain clip -- so there's no reason to route two operators through a different, more
 * general class than the other four. Originally added to recognize {@code iff(le(...),le(...))} as
 * {@code iff}'s own two operands -- confirmed via the bundled competition corpus that this shape
 * occurs 15 times, previously falling all the way to unpropagated {@code PredicateConstraint}
 * since neither operand was a plain boolean variable nor an {@code EQ}/{@code NEQ} ground
 * relation.
 * <p>
 * Also registered directly in {@link Xcsp3CallbackHandler#recognizeConstraint}'s own chain, not
 * just reachable via {@link IffRecognizer}: a bare top-level {@code eq}/{@code ne}/{@code le}/
 * {@code lt} against a constant (e.g. {@code le(0,x)}) is a genuinely single-variable intension,
 * which {@code genericIntensionConstraint} already routes to {@code UnaryPredicateConstraint}
 * regardless -- fine when the constraint is added unconditionally (a real {@code UnaryConstraint}
 * either way, so node consistency prunes it directly before search), but {@code
 * UnaryPredicateConstraint} implements no propagation, so a {@code reifiedBy}-attributed
 * occurrence of this exact shape got zero incremental propagation from its reification wrapper
 * (only a final satisfaction check once every variable is assigned) -- confirmed via the bundled
 * competition corpus, 65 {@code le(N,VAR)} occurrences alone. Recognizing it here instead routes
 * to {@link UnaryComparatorConstraint}, which genuinely participates in a reified constraint's own
 * propagation reasoning either way, so this closes the gap specifically for the reified case
 * without weakening the unreified one.
 * <p>
 * The non-constant side is resolved via {@link Xcsp3CallbackHandler#resolveVariable}, not the
 * narrower {@code asVariable}, so a compound sub-expression the handler knows how to materialize
 * (currently {@code div}/{@code mod}) works here too -- e.g. {@code le(0,div(x,6))} -- not just a
 * bare variable. The constant side is checked first (via {@link Xcsp3CallbackHandler#asConstant},
 * cheap and side-effect-free) specifically so that {@code resolveVariable} is only ever asked to
 * resolve the <em>other</em> side -- {@code resolveVariable} itself would happily resolve a bare
 * constant too (via its own constant-variable case), which would otherwise make this method's two
 * branches ambiguous for a genuinely both-sides-ground shape.
 */
final class GroundRelationRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    GroundRelationRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(node.getType());
        if (!Xcsp3CallbackHandler.LITERAL_OPERATORS.contains(operator) || node.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Integer> rightConstant = Xcsp3CallbackHandler.asConstant(node.sons[1]);
        if (rightConstant.isPresent()) {
            return handler.resolveVariable(node.sons[0])
                    .map(v -> UnaryComparatorConstraint.of(v, operator, rightConstant.get()));
        }
        Optional<Integer> leftConstant = Xcsp3CallbackHandler.asConstant(node.sons[0]);
        if (leftConstant.isPresent()) {
            return handler.resolveVariable(node.sons[1])
                    .map(v -> UnaryComparatorConstraint.of(v, Xcsp3CallbackHandler.flip(operator), leftConstant.get()));
        }
        return Optional.empty();
    }
}
