package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.RelationLogicConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Recognizes {@code or(...)} of arbitrary arity and nesting depth. Prefers {@link
 * #recognizeOrOfLiterals} first when exactly 2-ary (cheaper -- no extra indicator variables,
 * already fully propagating via {@link RelationLogicConstraint}); otherwise resolves each son via
 * {@code dispatch} (the owning {@link Xcsp3CallbackHandler}'s full registered recognizer chain, so
 * a disjunct can itself be any recognized shape, including a nested {@code and}/{@code or}),
 * reifies each into a fresh indicator, and combines via {@link AtLeastNConstraint} with {@code
 * n=1} -- a genuine N-ary OR primitive, avoiding the extra indicator-per-fold-step a pairwise
 * {@code BinaryLogicConstraint} chain would need (that class isn't used anywhere in this design --
 * it isn't {@link io.github.rcrida.jcsp.consistency.Propagatable}, so reifying it would get zero
 * incremental narrowing until every literal is already singleton). Declining any one son declines
 * the whole node, always safe, just less propagated -- falls through to the generic fallback
 * unchanged.
 */
final class OrRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;
    private final Function<XNode<XVarInteger>, Optional<Constraint>> dispatch;

    /**
     * Counter ensuring a unique indicator name per disjunct, not just per variable set: {@link
     * Xcsp3CallbackHandler#indicatorName} derives its name purely from the operand's own
     * variables, so two structurally different disjuncts sharing the exact same variable set
     * (e.g. two AND-branches each over the same div/mod auxiliaries, differing only in their
     * target constants -- XCSP3's knight's-move adjacency idiom) would otherwise collide onto one
     * indicator, silently aliasing two genuinely different reifications together. Owned by this
     * class alone (not the handler) since it's the only caller of {@link
     * Xcsp3CallbackHandler#indicatorName} that needs per-disjunct rather than per-operand
     * uniqueness.
     */
    private int orIndicatorCount;

    /**
     * When true, this instance only ever offers the auxiliary-free {@link #recognizeOrOfLiterals}
     * fast path and declines every other {@code or}, leaving it to a later recognizer.
     * <p>
     * The registry holds two instances either side of {@link TabulationRecognizer} for exactly this
     * reason: the fast path produces a {@link RelationLogicConstraint} with no auxiliary variables
     * and must keep winning, while the decomposition below reifies every disjunct into a fresh
     * indicator and is what tabulation exists to displace. Registering one instance before
     * tabulation and one after is what separates them, since a single recognizer cannot be half
     * ahead of another.
     */
    private final boolean literalsOnly;

    OrRecognizer(@NonNull Xcsp3CallbackHandler handler,
                 @NonNull Function<XNode<XVarInteger>, Optional<Constraint>> dispatch,
                 boolean literalsOnly) {
        this.handler = handler;
        this.dispatch = dispatch;
        this.literalsOnly = literalsOnly;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        if (node.getType() != TypeExpr.OR) return Optional.empty();
        if (node.sons.length == 2) {
            // A node with getType() == OR is necessarily an XNodeParent -- only a parent node ever
            // carries a logical-combinator type; a leaf's type is always VAR/LONG/SYMBOL -- so this
            // cast is always safe, no defensive instanceof check needed.
            Optional<Constraint> literalOr = recognizeOrOfLiterals((XNodeParent<XVarInteger>) node);
            if (literalOr.isPresent()) return literalOr;
        }
        if (literalsOnly) return Optional.empty();
        return ConstraintRecognizer.resolveEachChild(node, dispatch).map(constraints -> {
            Set<Variable<Boolean>> indicators = new LinkedHashSet<>();
            for (Constraint c : constraints) {
                Variable<Boolean> indicator = handler.newReifiedIndicator(
                        Xcsp3CallbackHandler.indicatorName("Or" + orIndicatorCount++, c), c);
                indicators.add(indicator);
            }
            return AtLeastNConstraint.builder().variables(Set.copyOf(indicators)).n(1).build();
        });
    }

    /**
     * Recognizes {@code or(A, B)} where both {@code A} and {@code B} are a bare {@code eq}/{@code
     * ne}/{@code le}/{@code lt} literal -- variable-vs-constant or variable-vs-variable, via {@link
     * #recognizeLiteral} -- and routes it onto {@link RelationLogicConstraint} instead of the
     * generic {@link PredicateConstraint}. Confirmed empirically against the bundled XCSP3
     * competition corpus: every real {@code or} intension node has exactly two children (never
     * more). Ordering literals ({@code le}/{@code lt}) were added after the original {@code
     * eq}/{@code ne}-only version, for a single instance ({@code RoomMate-sr0050-int.xml.lzma})
     * alone contributing 2,450 {@code or(le(...),le(...))} clauses (a preference-ranking encoding)
     * -- previously, together with the original {@code eq}/{@code ne} shape, the single largest
     * source of unpropagated intension in that corpus. Recognition failure on either side (a
     * nested/compound child, e.g. {@code and(...)} or a {@code dist}-based relation) is always
     * safe, just less propagated, falling through to this class's own N-ary fallback above.
     */
    private Optional<Constraint> recognizeOrOfLiterals(XNodeParent<XVarInteger> tree) {
        Optional<RelationLogicConstraint.Literal> left = recognizeLiteral(tree.sons[0]);
        if (left.isEmpty()) return Optional.empty();
        Optional<RelationLogicConstraint.Literal> right = recognizeLiteral(tree.sons[1]);
        if (right.isEmpty()) return Optional.empty();
        return Optional.of(RelationLogicConstraint.of(left.get(), LogicOperator.OR, right.get()));
    }

    /**
     * Matches a bare {@code eq}/{@code ne}/{@code le}/{@code lt} between a variable and either a
     * constant or another variable -- {@link RelationLogicConstraint.ValueLiteral}/{@link
     * RelationLogicConstraint.VariableLiteral} respectively -- in either operand order, mirroring
     * {@link GroundRelationRecognizer}'s own dual-order check (flipping the operator via {@link
     * Xcsp3CallbackHandler#flip} for a constant-first match, e.g. {@code le(0,x)} -> {@code
     * ValueLiteral(x, GEQ, 0)}). A full-corpus scan of the bundled competition corpus found every
     * real {@code or(...)} literal using the variable-first form regardless -- an earlier version
     * of this method only checked that one order for exactly that reason -- but per this project's
     * "the corpus is examples, not the boundary of what to support" stance (a real XCSP3 instance
     * outside the bundled sample could easily write a constant-first {@code le}/{@code lt}, the same
     * way {@link GroundRelationRecognizer}'s own real corpus instance does at an intension's own
     * root), checking both orders here costs one extra branch and is never wrong.
     * <p>
     * {@code ge}/{@code gt} are deliberately excluded from {@link Xcsp3CallbackHandler#LITERAL_OPERATORS}:
     * confirmed via a corpus probe that the canonizer always rewrites them into {@code le}/{@code
     * lt} first -- against a constant, with the constant and variable operands <em>swapped</em>
     * (e.g. {@code ge(x,5)} becomes {@code le(5,x)}, not {@code le(x,5)}) -- so a bare {@code
     * ge}/{@code gt} node is never actually reachable here regardless of which operand order this
     * method checks; declining an unreachable operator is always safe.
     */
    private Optional<RelationLogicConstraint.Literal> recognizeLiteral(XNode<XVarInteger> node) {
        Operator operator = Xcsp3CallbackHandler.intensionRelationalOperator(node.getType());
        if (!Xcsp3CallbackHandler.LITERAL_OPERATORS.contains(operator) || node.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Variable<Integer>> leftVar = handler.asVariable(node.sons[0]);
        if (leftVar.isPresent()) {
            Optional<Variable<Integer>> rightVar = handler.asVariable(node.sons[1]);
            if (rightVar.isPresent()) {
                return Optional.of(new RelationLogicConstraint.VariableLiteral(leftVar.get(), operator, rightVar.get()));
            }
            return Xcsp3CallbackHandler.asConstant(node.sons[1])
                    .map(constant -> new RelationLogicConstraint.ValueLiteral(leftVar.get(), operator, constant));
        }
        return Xcsp3CallbackHandler.asConstant(node.sons[0])
                .flatMap(constant -> handler.asVariable(node.sons[1])
                        .map(rightVar -> new RelationLogicConstraint.ValueLiteral(
                                rightVar, Xcsp3CallbackHandler.flip(operator), constant)));
    }
}
