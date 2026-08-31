package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Recognizes {@code A op B} where one of {@code A}/{@code B} is {@code add(t1, t2, ..., tn)} and
 * the other is a plain variable or constant -- e.g. {@code eq(add(x,y), z)} or {@code le(add(x,y),
 * 20)} -- routing onto {@link SumVariableConstraint}/{@link SumBoundConstraint} (every term a bare
 * variable) or {@link LinearVariableConstraint}/{@link LinearBoundConstraint} (at least one term
 * weighted) instead of the generic {@link PredicateConstraint}, which has no propagation at all.
 * Each {@code add} term must independently reduce to one or two (variable, coefficient) pairs --
 * see {@link #addTerm} for the four shapes recognized ({@code var}, {@code mul(var,const)}, {@code
 * neg(var)}, {@code sub(var,var)}) -- anything deeper (a nested {@code add} the canonizer didn't
 * already flatten, a product of two variables, a bare constant term, etc.) declines the whole
 * recognition, always safe, just less propagated, never incorrect, the same contract every
 * recognizer in this package documents. The choice between the "sum" and "linear" siblings is made
 * once, from every term's own final coefficient after same-variable terms are combined (e.g.
 * {@code add(x,x)} contributes coefficient 2 to {@code x}, {@code sub(x,x)} contributes 0 -- a
 * degenerate but harmless case, not specially rejected) -- {@code SumVariableConstraint}/{@code
 * SumBoundConstraint} only mean what they say when every one of that combined set is exactly
 * {@code 1}.
 * <p>
 * Confirmed empirically against the bundled XCSP3 competition corpus: the {@code var}/{@code
 * mul(var,const)} pair alone recognizes roughly 94% of what previously fell to {@link
 * PredicateConstraint} across several instances entirely (e.g. {@code CostasArray-12.xml.lzma},
 * {@code StillLife-wastage-03.xml.lzma}). {@code neg(var)}/{@code sub(var,var)} terms are not
 * corpus-confirmed the same way -- added because they're both linear (exactly as representable by
 * {@link LinearVariableConstraint}/{@link LinearBoundConstraint} as {@code mul(var,-1)}) and both
 * legal, plausible XCSP3 syntax a real instance could use even though none in the bundled sample
 * corpus happens to, per this project's own "the corpus is examples, not the boundary of what to
 * support" stance. A nested {@code add} genuinely never reaches this method unflattened, confirmed
 * empirically: {@code xcsp3-tools}' canonizer always flattens {@code add(add(x,y),w)} into one
 * three-term {@code add(x,y,w)} before {@code buildCtrIntension} ever sees it, since {@code add} is
 * associative -- so that specific "anything deeper" case is unreachable dead code, not a real gap.
 * <p>
 * Only checks {@code add} as {@code tree.sons[0]}, not the reverse, and only checks {@code
 * mul(var, constant)} operand order within each term, not the reverse: confirmed empirically that
 * {@code xcsp3-tools}' canonizer always places the compound {@code add} first against a plain
 * variable/constant target, and always places a {@code mul} term's variable operand before its
 * constant one -- the same complexity-based reordering {@link GroundRelationRecognizer}'s own
 * {@code EQ}/{@code NEQ} handling relies on. A defensive reverse-order check on either would be
 * permanently dead code.
 * <p>
 * The target side falls back to {@link Xcsp3CallbackHandler#resolveVariable} (materializing a
 * {@code neg}/{@code sub}/{@code div}/{@code mod} auxiliary) once both {@code asVariable} and
 * {@code asConstant} decline -- confirmed via a real corpus probe that this shape is reachable:
 * {@code eq(sub(div(x,2),y),z)} canonicalizes to {@code eq(add(y,z),div(x,2))}, moving the
 * subtracted term across the relation and leaving a genuinely compound target ({@code div(x,2)}),
 * not just a bare variable/constant the narrower pair alone could ever have matched.
 */
final class SumOrLinearRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;

    SumOrLinearRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
        if (operator == null || tree.sons.length != 2 || tree.sons[0].getType() != TypeExpr.ADD) {
            return Optional.empty();
        }
        XNode<XVarInteger> addSide = tree.sons[0];
        XNode<XVarInteger> targetSide = tree.sons[1];

        Map<Variable<Integer>, Integer> coefficients = new LinkedHashMap<>();
        for (XNode<XVarInteger> term : addSide.sons) {
            if (!addTerm(coefficients, term)) {
                return Optional.empty();
            }
        }
        boolean unitCoefficients = coefficients.values().stream().allMatch(c -> c == 1);

        Optional<Variable<Integer>> targetVar = handler.asVariable(targetSide);
        if (targetVar.isPresent()) {
            return Optional.of(unitCoefficients
                    ? SumVariableConstraint.of(coefficients.keySet(), operator, targetVar.get())
                    : LinearVariableConstraint.of(coefficients, operator, targetVar.get()));
        }
        Optional<Integer> targetConstant = Xcsp3CallbackHandler.asConstant(targetSide);
        if (targetConstant.isPresent()) {
            return Optional.of(unitCoefficients
                    ? SumBoundConstraint.of(coefficients.keySet(), operator, targetConstant.get())
                    : LinearBoundConstraint.of(coefficients, operator, targetConstant.get()));
        }
        // Neither a bare variable nor a constant -- e.g. eq(add(y,z),div(x,2)), the shape
        // xcsp3-tools' canonizer produces from eq(sub(div(x,2),y),z) by moving the subtracted term
        // across the relation. resolveVariable materializes a compound target (neg/sub/div/mod) into
        // a real auxiliary variable, so the variable-target siblings still apply -- just with one
        // extra auxiliary instead of the tighter constant-bound form.
        return handler.resolveVariable(targetSide).map(resolvedTarget -> unitCoefficients
                ? SumVariableConstraint.of(coefficients.keySet(), operator, resolvedTarget)
                : LinearVariableConstraint.of(coefficients, operator, resolvedTarget));
    }

    /**
     * Folds one {@code add} term into {@code coefficients}, recognizing four shapes: a bare
     * variable (coefficient {@code 1}), {@code mul(var, constant)} (coefficient {@code constant}),
     * {@code neg(var)} (coefficient {@code -1}), and {@code sub(var, var)} (coefficient {@code 1}
     * for the left operand, {@code -1} for the right -- the one shape here contributing to two
     * different variables from a single term). Returns {@code false} (declining the whole
     * recognition, per this class's own contract) for anything else.
     */
    private boolean addTerm(Map<Variable<Integer>, Integer> coefficients, XNode<XVarInteger> term) {
        Optional<Variable<Integer>> bareVar = handler.asVariable(term);
        if (bareVar.isPresent()) {
            coefficients.merge(bareVar.get(), 1, Integer::sum);
            return true;
        }
        if (term.getType() == TypeExpr.MUL && term.sons.length == 2) {
            Optional<Variable<Integer>> mulVar = handler.asVariable(term.sons[0]);
            if (mulVar.isPresent()) {
                Optional<Integer> mulConst = Xcsp3CallbackHandler.asConstant(term.sons[1]);
                if (mulConst.isPresent()) {
                    coefficients.merge(mulVar.get(), mulConst.get(), Integer::sum);
                    return true;
                }
            }
        }
        if (term.getType() == TypeExpr.NEG && term.sons.length == 1) {
            Optional<Variable<Integer>> negVar = handler.asVariable(term.sons[0]);
            if (negVar.isPresent()) {
                coefficients.merge(negVar.get(), -1, Integer::sum);
                return true;
            }
        }
        if (term.getType() == TypeExpr.SUB && term.sons.length == 2) {
            Optional<Variable<Integer>> subLeft = handler.asVariable(term.sons[0]);
            Optional<Variable<Integer>> subRight = handler.asVariable(term.sons[1]);
            if (subLeft.isPresent() && subRight.isPresent()) {
                coefficients.merge(subLeft.get(), 1, Integer::sum);
                coefficients.merge(subRight.get(), -1, Integer::sum);
                return true;
            }
        }
        return false;
    }
}
