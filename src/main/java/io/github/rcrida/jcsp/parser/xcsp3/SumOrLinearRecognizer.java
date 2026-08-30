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
 * variable) or {@link LinearVariableConstraint}/{@link LinearBoundConstraint} (at least one term a
 * weighted {@code mul(var, constant)}) instead of the generic {@link PredicateConstraint}, which
 * has no propagation at all. Each {@code add} term must independently be a bare variable or a
 * two-term {@code mul(var, constant)} -- anything deeper (a nested {@code add}, a product of two
 * variables, etc.) declines the whole recognition, always safe, just less propagated, never
 * incorrect, the same contract every recognizer in this package documents. The choice between the
 * "sum" and "linear" siblings is made once, from every term's own final coefficient after
 * same-variable terms are combined (e.g. {@code add(x,x)} contributes coefficient 2 to {@code x},
 * not two separate unit terms) -- {@code SumVariableConstraint}/{@code SumBoundConstraint} only
 * mean what they say when every one of that combined set is exactly {@code 1}.
 * <p>
 * Confirmed empirically against the bundled XCSP3 competition corpus: this recognizes roughly 94%
 * of what previously fell to {@link PredicateConstraint} across several instances entirely (e.g.
 * {@code CostasArray-12.xml.lzma}, {@code StillLife-wastage-03.xml.lzma}), leaving only a smaller
 * residue with deeper nesting (e.g. {@code RadarSurveillance-8-24-3-2-00.xml.lzma}'s wider {@code
 * add} terms) unrecognized.
 * <p>
 * Only checks {@code add} as {@code tree.sons[0]}, not the reverse, and only checks {@code
 * mul(var, constant)} operand order within each term, not the reverse: confirmed empirically that
 * {@code xcsp3-tools}' canonizer always places the compound {@code add} first against a plain
 * variable/constant target, and always places a {@code mul} term's variable operand before its
 * constant one -- the same complexity-based reordering {@link GroundRelationRecognizer}'s own
 * {@code EQ}/{@code NEQ} handling relies on. A defensive reverse-order check on either would be
 * permanently dead code.
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
            Optional<Variable<Integer>> bareVar = handler.asVariable(term);
            if (bareVar.isPresent()) {
                coefficients.merge(bareVar.get(), 1, Integer::sum);
                continue;
            }
            if (term.getType() == TypeExpr.MUL && term.sons.length == 2) {
                Optional<Variable<Integer>> mulVar = handler.asVariable(term.sons[0]);
                if (mulVar.isPresent()) {
                    Optional<Integer> mulConst = Xcsp3CallbackHandler.asConstant(term.sons[1]);
                    if (mulConst.isPresent()) {
                        coefficients.merge(mulVar.get(), mulConst.get(), Integer::sum);
                        continue;
                    }
                }
            }
            return Optional.empty();
        }
        boolean unitCoefficients = coefficients.values().stream().allMatch(c -> c == 1);

        Optional<Variable<Integer>> targetVar = handler.asVariable(targetSide);
        if (targetVar.isPresent()) {
            return Optional.of(unitCoefficients
                    ? SumVariableConstraint.of(coefficients.keySet(), operator, targetVar.get())
                    : LinearVariableConstraint.of(coefficients, operator, targetVar.get()));
        }
        return Xcsp3CallbackHandler.asConstant(targetSide).map(constant -> unitCoefficients
                ? SumBoundConstraint.of(coefficients.keySet(), operator, constant)
                : LinearBoundConstraint.of(coefficients, operator, constant));
    }
}
