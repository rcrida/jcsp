package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * Variant of {@link #propagate(Map)} that accepts a hint of which variables' domains changed
     * since this constraint last propagated in the current fixpoint loop (see {@link
     * io.github.rcrida.jcsp.solver.FixpointPropagation#applyFixpoint}), or {@code null} meaning
     * "unknown -- assume everything may have changed". Mirrors {@link
     * io.github.rcrida.jcsp.consistency.ConstraintConsistency#apply(io.github.rcrida.jcsp.ConstraintSatisfactionProblem, Set)}'s
     * own hint, but one level deeper: that one lets a {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency} instance skip whole
     * constraint <em>objects</em> that don't reference a changed variable -- of no help to a
     * constraint whose own cost is dominated by work <em>internal</em> to a single object (e.g.
     * {@code DiffnConstraint}'s pairwise compulsory-part checks across all its rectangles). The
     * default ignores the hint and delegates to {@link #propagate(Map)}, so every existing
     * implementor is unaffected; a constraint whose internal cost scales with its own variable
     * count overrides this to skip the sub-computations the hint proves can't have changed.
     */
    default Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains,
                                                              @Nullable Set<Variable<?>> changedSinceLastRun) {
        return propagate(domains);
    }

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
     * {@code reason}. Sound only when {@code variable} being pinned to that value is, on its own,
     * a fully self-contained explanation for the failure being cited — i.e. every other variable
     * the derivation depended on is either cited independently in the same {@code reason} map or
     * genuinely irrelevant to it, not merely "currently non-singleton and therefore skipped."
     * {@link io.github.rcrida.jcsp.constraints.nary.CircuitConstraint}'s self-loop citation is the
     * safe pattern: the domain being singleton at exactly the pruned value is itself the complete
     * cause, with no other variable involved at all.
     * <p>
     * Do <em>not</em> call this independently per side of a jointly-interacting pair (e.g. once for
     * each side of a binary constraint), omitting whichever side isn't currently singleton — that
     * pattern shipped as a real, confirmed bug in {@code BinaryComparatorConstraint}/{@code
     * BinaryOffsetConstraint}/{@code AbsoluteDifferenceConstraint}/{@code DivisionConstraint}'s
     * former {@code explainInfeasible} implementations: the omitted side's current domain can be
     * narrower than its full range (narrowed by a <em>different</em> constraint sharing it), so
     * silently dropping it from the citation produces a nogood that's unsound in a branch where the
     * omitted side's excluded values would have provided a valid escape. That regression was
     * confirmed via {@code QuasiGroup-7-09.xml.lzma} intermittently reporting a false {@code
     * UNSATISFIABLE} despite a verified solution existing. Use {@link
     * io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint#fromCurrentState} instead for
     * a jointly-interacting pair or group: it tries {@link #allSingletonReason} first (this
     * helper's collective, sound analogue) and falls back to citing each variable's <em>exact</em>
     * current value set rather than omitting any of them.
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
