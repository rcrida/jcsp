package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;
import java.util.function.Function;

/**
 * Recognizes two channel shapes with the same underlying algorithm: a bare {@code eq}/{@code ne}
 * between a plain variable and an arbitrary compound relation (e.g. {@code
 * ne(and(ne(q,x),or(...)),b)}, "{@code b} is the negation of whether {@code q} and {@code x} are a
 * queen's-move apart" -- XCSP3's {@code QueenAttacking} idiom: {@code b[i] != attacks(q,x[i])}),
 * and a literal {@code iff(X,Y)} biconditional (always {@code EQ}-equivalent -- {@code iff} has no
 * "not iff" counterpart the way {@code eq}/{@code ne} are a pair, so {@link Operator#NEQ} never
 * applies to it). Each operand is resolved via {@code dispatch} (the owning {@link
 * Xcsp3CallbackHandler}'s full recognizer chain, recursing into {@code and}/{@code or}/etc. the
 * same way {@link AndRecognizer}/{@link OrRecognizer} already recurse into their own children) or,
 * if that fails, treated as a bare variable.
 * <p>
 * Absorbed a formerly-separate {@code IffRecognizer} class that handled only {@code iff(...)}, with
 * a narrower, corpus-scoped operand resolution (just a binary/ground relation pair, never a bare
 * variable or a full-dispatch compound child). Once this class's own {@code eq}/{@code ne} handling
 * had already been generalized to full dispatch for the {@code QueenAttacking} channel shape above,
 * keeping a second, narrower implementation of the exact same "resolve two operands into
 * indicators, then compare" algorithm just for {@code iff} nodes was pure duplication with no
 * remaining reason to stay narrower -- e.g. {@code iff(eq(x,1),eq(add(y,z),3))} now recognizes via
 * {@link SumOrLinearRecognizer} reached through full dispatch on the right operand, where the old
 * {@code IffRecognizer} declined outright (its own binary/ground pair has no {@code add(...)}
 * case).
 * <p>
 * {@link #resolve} is read-only -- unlike {@link Xcsp3CallbackHandler#resolveVariable}'s own
 * div/mod materialization, it never registers anything against the shared builder -- so both
 * operands are fully resolved (and, critically, confirmed to <em>both</em> succeed) before either
 * one is actually reified into a {@link Variable Variable&lt;Boolean&gt;} indicator via {@link
 * #toIndicator}. Reifying a side eagerly, before knowing whether the other side will also resolve,
 * would leave an orphaned {@code ReifiedConstraint} behind on every declined node whose first
 * operand happens to recognize -- harmless (a fresh, otherwise-unconstrained indicator can't affect
 * the real solution set) but needless, and avoidable simply by deferring the two {@link
 * #toIndicator} calls until after both {@link #resolve} calls have already succeeded.
 * <p>
 * Once both operands resolve to an indicator, the relation between them is exactly a {@link
 * BinaryComparatorConstraint} with {@link Operator#EQ}/{@link Operator#NEQ}. Each side is tried
 * independently, in either order, so operand order doesn't matter: a bare-variable operand never
 * dispatches successfully on its own (every recognizer in the chain requires an operator-bearing
 * node, never a plain {@code VAR} leaf), so trying dispatch first and falling back to the
 * bare-variable case is always unambiguous.
 * <p>
 * Registered last in {@link Xcsp3CallbackHandler#recognizeConstraint}'s chain: every earlier
 * recognizer already covers a plain {@code var op var}/{@code var op constant} shape with a
 * tighter, natively-typed constraint (e.g. an integer {@link BinaryComparatorConstraint} rather
 * than this class's boolean-indicator bridging), so the {@code eq}/{@code ne} channel shape is only
 * ever reached once those have all declined -- i.e. when at least one side is a genuinely compound
 * relation. {@code iff} nodes reach this class unconditionally (no other recognizer targets {@code
 * TypeExpr.IFF}). Declining on either side is always safe, just less propagated, falling through to
 * the generic {@link PredicateConstraint} unchanged. The {@code eq}/{@code ne} channel shape is
 * confirmed via the bundled XCSP3 competition corpus ({@code QueenAttacking-06.xml.lzma}, 11
 * occurrences); {@code iff} via {@code Mario-easy-4.xml.lzma}'s {@code iff(eq(s[i],i),eq(g[i],0))}
 * gold-earning rule (13 occurrences), the shape that originally motivated {@code IffRecognizer}.
 */
final class ChannelRecognizer implements ConstraintRecognizer {

    /**
     * A resolved (but not yet reified) channel operand: either a plain variable or a compound
     * relation still awaiting reification via {@link #toIndicator}.
     */
    private sealed interface Operand permits BareVariable, CompoundRelation {
    }

    private record BareVariable(Variable<Integer> variable) implements Operand {
    }

    private record CompoundRelation(Constraint constraint) implements Operand {
    }

    private final Xcsp3CallbackHandler handler;
    private final Function<XNode<XVarInteger>, Optional<Constraint>> dispatch;
    private int channelIndicatorCount;

    ChannelRecognizer(@NonNull Xcsp3CallbackHandler handler,
                       @NonNull Function<XNode<XVarInteger>, Optional<Constraint>> dispatch) {
        this.handler = handler;
        this.dispatch = dispatch;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Operator operator;
        if (tree.getType() == TypeExpr.IFF) {
            operator = Operator.EQ;
        } else {
            operator = Xcsp3CallbackHandler.intensionRelationalOperator(tree.getType());
            if (operator != Operator.EQ && operator != Operator.NEQ) {
                return Optional.empty();
            }
        }
        if (tree.sons.length != 2) {
            return Optional.empty();
        }
        Optional<Operand> left = resolve(tree.sons[0]);
        if (left.isEmpty()) return Optional.empty();
        Optional<Operand> right = resolve(tree.sons[1]);
        if (right.isEmpty()) return Optional.empty();

        Variable<Boolean> leftIndicator = toIndicator(left.get(), "ChanL" + channelIndicatorCount);
        Variable<Boolean> rightIndicator = toIndicator(right.get(), "ChanR" + channelIndicatorCount++);
        return Optional.of(BinaryComparatorConstraint.of(leftIndicator, operator, rightIndicator));
    }

    private Optional<Operand> resolve(XNode<XVarInteger> node) {
        Optional<Constraint> constraint = dispatch.apply(node);
        if (constraint.isPresent()) {
            return Optional.of(new CompoundRelation(constraint.get()));
        }
        return handler.asVariable(node).map(BareVariable::new);
    }

    private Variable<Boolean> toIndicator(Operand operand, String namePrefix) {
        return switch (operand) {
            case BareVariable(Variable<Integer> v) -> handler.booleanIndicatorFor(v);
            case CompoundRelation(Constraint c) ->
                    handler.newReifiedIndicator(Xcsp3CallbackHandler.indicatorName(namePrefix, c), c);
        };
    }
}
