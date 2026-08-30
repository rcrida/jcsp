package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Recognizes {@code A op B} where one of {@code A}/{@code B} is {@code add(r1, r2, ..., rn)} and
 * every {@code ri} is itself a relation (not a plain variable/weighted term -- that shape is
 * {@link SumOrLinearRecognizer}'s own, and this recognizer only ever reaches a term that one
 * already declined) -- e.g. {@code eq(add(le(2,x),le(3,y),eq(z,1)),1)}, "exactly one of these three
 * relations holds". XCSP3's arithmetic evaluates a relational sub-expression to {@code 1}/{@code 0}
 * (true/false), so {@code add} over relations is a genuine count -- this recognizer materializes
 * that count directly as a weighted sum of freshly reified boolean indicators (weight {@code 1}
 * each) via {@link LinearBooleanBoundConstraint} (constant target) or {@link
 * LinearBooleanVariableConstraint} (variable target), instead of falling all the way to the
 * generic {@link PredicateConstraint}, which has no propagation at all.
 * <p>
 * Each {@code ri} is resolved via {@code dispatch} (the owning {@link Xcsp3CallbackHandler}'s full
 * registered recognizer chain, not a narrower one) -- so a term can be any recognized relation
 * shape, not just a bare literal: a nested {@code and}/{@code or}, an {@code iff}, a {@code
 * dist}-based comparison, etc. all resolve the same way {@link OrRecognizer}'s own N-ary fallback
 * resolves each disjunct. This also means the "is this term boolean-valued" question never needs
 * its own answer here: {@code dispatch} only succeeds for a node whose top-level type is itself a
 * relation/logical combinator (every recognizer in the chain requires that), so a genuine
 * arithmetic term (a bare variable, {@code mul(x,2)}, ...) always fails to dispatch and correctly
 * declines the whole node -- {@link SumOrLinearRecognizer} is the recognizer for that case, tried
 * earlier in the chain. Declining any one term declines the whole node, always safe, just less
 * propagated, falling through to the generic fallback unchanged.
 * <p>
 * Confirmed via the bundled XCSP3 competition corpus to be the single largest remaining fallback
 * shape after {@link SumOrLinearRecognizer}/{@link OrRecognizer}/etc.: {@code
 * eq(add(le(c,v)+),c)} (a sliding-window "count of these thresholds met" idiom) alone accounts for
 * 61 of 104 residual {@code PredicateConstraint} occurrences across the corpus.
 * <p>
 * Only checks {@code add} as {@code tree.sons[0]}, not the reverse, matching {@link
 * SumOrLinearRecognizer}'s own confirmed canonizer-ordering guarantee (the compound side always
 * precedes a plain variable/constant target).
 */
final class RelationSumRecognizer implements ConstraintRecognizer {

    private final Function<XNode<XVarInteger>, Optional<Constraint>> dispatch;
    private final Xcsp3CallbackHandler handler;

    /**
     * Counter ensuring a unique indicator name per term, not just per operand variable set --
     * mirrors {@link OrRecognizer#orIndicatorCount}'s own reasoning: two structurally different
     * terms can share the exact same variable set (e.g. two threshold checks on the same
     * variable against different constants).
     */
    private int termIndicatorCount;

    RelationSumRecognizer(@NonNull Xcsp3CallbackHandler handler,
                          @NonNull Function<XNode<XVarInteger>, Optional<Constraint>> dispatch) {
        this.handler = handler;
        this.dispatch = dispatch;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
        if (operator == null || tree.sons.length != 2 || tree.sons[0].getType() != TypeExpr.ADD) {
            return Optional.empty();
        }
        XNode<XVarInteger> addSide = tree.sons[0];
        XNode<XVarInteger> targetSide = tree.sons[1];

        Optional<List<Constraint>> terms = ConstraintRecognizer.resolveEachChild(addSide, dispatch);
        if (terms.isEmpty()) {
            return Optional.empty();
        }

        Map<Variable<Boolean>, Integer> weights = new LinkedHashMap<>();
        for (Constraint term : terms.get()) {
            Variable<Boolean> indicator = handler.newReifiedIndicator(
                    Xcsp3CallbackHandler.indicatorName("RelSum" + termIndicatorCount++, term), term);
            weights.put(indicator, 1);
        }

        Optional<Variable<Integer>> targetVar = handler.asVariable(targetSide);
        if (targetVar.isPresent()) {
            return Optional.of(LinearBooleanVariableConstraint.of(weights, operator, targetVar.get()));
        }
        return Xcsp3CallbackHandler.asConstant(targetSide)
                .map(constant -> LinearBooleanBoundConstraint.of(weights, operator, constant));
    }
}
