package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.AndConstraint;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Recognizes {@code and(...)} of arbitrary arity and nesting depth, routing through {@link
 * AndConstraint} (a real fixpoint over every recursively-resolved conjunct) instead of the
 * generic, unpropagated {@code PredicateConstraint}. Each son is resolved via {@code dispatch} --
 * the owning {@link Xcsp3CallbackHandler}'s full registered recognizer chain, not just this one --
 * so a conjunct can itself be any recognized shape, including a nested {@code and}/{@code or}.
 * Declining any one son declines the whole node, always safe, just less propagated.
 * <p>
 * Needs no direct reference to {@link Xcsp3CallbackHandler} itself, unlike most other recognizers
 * in this package -- {@code dispatch} is the only collaborator required.
 */
final class AndRecognizer implements ConstraintRecognizer {

    private final Function<XNode<XVarInteger>, Optional<Constraint>> dispatch;

    AndRecognizer(@NonNull Function<XNode<XVarInteger>, Optional<Constraint>> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> node) {
        if (node.getType() != TypeExpr.AND) return Optional.empty();
        return ConstraintRecognizer.resolveEachChild(node, dispatch)
                .map(constraints -> AndConstraint.of(Set.copyOf(constraints)));
    }
}
