package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One pattern-matcher in {@link Xcsp3CallbackHandler}'s XCSP3 {@code <intension>} recognition
 * chain: tries to recognize {@code node} as a specific relation shape and, on success, returns a
 * real propagating {@link Constraint} for it instead of falling through to the generic,
 * unpropagated {@code PredicateConstraint}/{@code UnaryPredicateConstraint}. Declining (returning
 * {@link Optional#empty()}) is always safe -- the caller tries the next registered recognizer, or
 * ultimately falls back to the generic predicate form; a recognizer must never guess.
 * <p>
 * Implementations are registered, in a fixed priority order matching the order each one used to
 * appear in {@link Xcsp3CallbackHandler#buildCtrIntension}'s hand-written chain, on {@link
 * Xcsp3CallbackHandler#recognizeConstraint}. Adding support for a new corpus-confirmed shape is a
 * matter of writing one new implementation and adding it to that registration list -- no change to
 * {@link Xcsp3CallbackHandler} itself beyond that one line.
 */
@FunctionalInterface
interface ConstraintRecognizer {

    Optional<Constraint> recognize(XNode<XVarInteger> node);

    /**
     * Resolves every son of {@code node} via {@code dispatch} (the owning handler's full
     * registered recognizer chain, not just one recognizer), declining the whole node if any son
     * fails to resolve. Shared by {@link AndRecognizer}/{@link OrRecognizer}, the two recognizers
     * that recurse into children rather than matching a single relation directly.
     */
    static Optional<List<Constraint>> resolveEachChild(
            XNode<XVarInteger> node, Function<XNode<XVarInteger>, Optional<Constraint>> dispatch) {
        List<Constraint> results = new ArrayList<>();
        for (XNode<XVarInteger> son : node.sons) {
            Optional<Constraint> c = dispatch.apply(son);
            if (c.isEmpty()) return Optional.empty();
            results.add(c.get());
        }
        return Optional.of(results);
    }
}
