package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A constraint that can propagate domain reductions from the current variable domains.
 * <p>
 * Implementing constraints compute the tightest feasible domain for each of their variables
 * given the current domains of the other variables, and return the changed domains as a map.
 */
public interface Propagatable {
    /**
     * @param domains current variable domains for the whole problem
     * @return updated domains for variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is provably infeasible
     */
    Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains);

    /**
     * Propagates domain reductions and returns the reason for each change: the variable-value
     * pairs from the current state that caused the pruning.
     * <p>
     * Delegates to {@link #propagate}; on infeasibility delegates further to
     * {@link #explainInfeasible}. Constraints wanting a tighter nogood override
     * {@code explainInfeasible} instead of this method, so the feasible-path wrapping (identical
     * for every implementer) lives here once rather than being copy-pasted per override.
     */
    default PropagationResult propagateWithReasons(Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains)
                .map(updated -> PropagationResult.feasible(updated, null))
                .orElseGet(() -> PropagationResult.infeasible(explainInfeasible(domains).orElse(null)));
    }

    /**
     * Explains a {@link #propagate} infeasibility by identifying the nogood responsible for the
     * domain wipeout. The default returns {@link Optional#empty()}, meaning no explanation is
     * provided and the caller substitutes the full assignment as the nogood. Constraints override
     * this to return a tighter, sound explanation — a {@link NogoodConstraint}, not necessarily a
     * ground one; see {@link io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint}/{@link io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint}.
     */
    default Optional<NogoodConstraint> explainInfeasible(Map<Variable<?>, Domain<?>> domains) {
        return Optional.empty();
    }

    /**
     * Reports whether this constraint is already guaranteed satisfied by {@code domains}, even
     * though they may not yet be fully assigned — the mirror of the infeasibility {@link
     * #propagate} already signals via {@link Optional#empty()}, but for "definitely true" rather
     * than "definitely false". The default returns {@code false}: soundly detecting this generally
     * requires reasoning specific to the constraint (e.g. an element already forced into a set
     * variable's lower bound, so its membership can't be undone by any further narrowing), which
     * isn't derivable from {@link #propagate}'s own contract the way infeasibility is. Constraints
     * for which this is cheap and sound override it; {@code io.github.rcrida.jcsp.constraints.nary
     * .ReifiedConstraint} uses it to force its indicator {@code true} as soon as the body is
     * guaranteed to hold, rather than only once every body variable is singleton.
     */
    default boolean isNecessarilySatisfied(Map<Variable<?>, Domain<?>> domains) {
        return false;
    }

    /**
     * If {@code domain} is a singleton, records its sole value against {@code variable} in
     * {@code reason}. Shared by {@code propagateWithReasons} overrides that attribute an
     * infeasible narrowing to whichever side of a binary constraint already holds a pinned
     * value — a non-singleton side is left unblamed since no single value can be cited for it.
     */
    static void addIfSingleton(@NonNull Domain<?> domain, Variable<?> variable, @NonNull Map<Variable<?>, Object> reason) {
        if (domain.isSingleton()) {
            domain.singleValue().ifPresent(value -> reason.put(variable, value));
        }
    }

    /**
     * Returns a full variable-value reason map when every one of {@code variables} currently
     * holds a singleton domain in {@code domains}, or an empty map otherwise. Shared by
     * {@code propagateWithReasons} overrides whose infeasibility can only be soundly attributed
     * to the fully collective set of concrete values — no single variable's value alone proves
     * the constraint infeasible (e.g. sums, weighted sums, and the collective half of max/min's
     * two-part explanation) — since a partial subset can't rule out an unlisted open-domain
     * variable also participating in the violation.
     */
    static Map<Variable<?>, Object> allSingletonReason(@NonNull Collection<? extends Variable<?>> variables,
                                                       @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        for (Variable<?> var : variables) {
            Optional<?> value = domains.get(var).singleValue();
            if (value.isEmpty()) return Map.of();
            reason.put(var, value.get());
        }
        return reason;
    }
}
