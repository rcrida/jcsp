package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ConstraintConsistency} that runs all {@link Propagatable} constraints of a given
 * type to fixpoint: filters, propagates, and repeats until no further domain reductions occur,
 * returning {@link Optional#empty()} as soon as any propagator signals infeasibility.
 *
 * <p>Use the {@link #of} factory to create instances. Adding a new propagator to the solver
 * chains ({@code PropagationFixpointSolver.PROPAGATORS}, {@code LocalSolver.Factory.PREPROCESSORS})
 * requires only a single {@code FixpointConsistency.of(MyConstraint.class)} entry.
 */
public final class FixpointConsistency implements ConstraintConsistency {
    private static final Logger log = LoggerFactory.getLogger(FixpointConsistency.class);

    private final Class<? extends Propagatable> constraintType;

    private FixpointConsistency(Class<? extends Propagatable> constraintType) {
        this.constraintType = constraintType;
    }

    public static FixpointConsistency of(Class<? extends Propagatable> constraintType) {
        return new FixpointConsistency(constraintType);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<Propagatable> constraints = (List) csp.getConstraints().stream()
                .filter(constraintType::isInstance)
                .toList();
        var name = constraintType.getSimpleName();
        if (constraints.isEmpty()) {
            log.debug("{}: fixpoint reached", name);
            return Optional.of(csp);
        }
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("{}: infeasible detected", name);
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        builder.variableDomainEntry((Variable) entry.getKey(), (Domain) entry.getValue());
                    }
                    current = builder.build();
                    changed = true;
                }
            }
        }
        log.debug("{}: fixpoint reached", name);
        return Optional.of(current);
    }

    /**
     * Thin wrapper over {@link #applyWithReason}, kept for direct callers/tests and for {@link
     * ConstraintConsistency}'s own default {@code applyWithReason} fallback (used by implementors
     * that don't override it): returns the nogood that explains a domain wipeout, tried in order —
     * (1) the failing constraint's own {@link Propagatable#explainInfeasible} — tightest when it
     * applies, and free to be a ground or a range nogood depending on what the propagator itself
     * can prove (e.g. {@code AllDiffConstraint} tries ground on its Hall-violating subset, then
     * range over that same subset); (2) {@link RangeNogoodConstraint#fromCurrentBounds} over the
     * failing constraint's <em>entire</em> variable set — the generic fallback for propagators that
     * don't provide anything tighter, sound whenever (1) is empty, since {@link Propagatable#propagate}
     * already reported infeasibility given exactly these current domains — or {@link Optional#empty()}
     * if this constraint type caused no conflict (the conflict is in a different {@link FixpointConsistency}).
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        ConsistencyResult result = applyWithReason(csp, null);
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    /**
     * Single-pass combination of {@link #apply} and {@link #explainConflict}: calls each
     * constraint's plain {@link Propagatable#propagate} exactly once — identical cost to {@link
     * #apply} on the feasible path, since nothing extra is allocated or computed there — and only
     * on the constraint that actually causes a domain wipeout does it call {@link
     * Propagatable#explainInfeasible} to derive a reason, tried in the same two tiers {@link
     * #explainConflict} used to: (1) the constraint's own explanation, (2) {@link
     * RangeNogoodConstraint#fromCurrentBounds} over its whole variable set as a generic fallback.
     * {@code changedSinceLastRun} is accepted for interface conformance but unused, same as {@link
     * #apply(ConstraintSatisfactionProblem, Set)} — this propagator's cost scales with its own
     * fixed, small constraint count, so the dirty-variable hint has nothing to save here.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem csp,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        List<Propagatable> constraints = (List) csp.getConstraints().stream()
                .filter(constraintType::isInstance)
                .toList();
        if (constraints.isEmpty()) return ConsistencyResult.feasible(csp);
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                Optional<Map<Variable<?>, Domain<?>>> result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    NogoodConstraint reason = constraint.explainInfeasible(current.getVariableDomains()).orElse(null);
                    if (reason == null) {
                        reason = RangeNogoodConstraint.fromCurrentBounds(
                                ((Constraint) constraint).getVariables(), current.getVariableDomains()).orElse(null);
                    }
                    return ConsistencyResult.infeasible(reason);
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        builder.variableDomainEntry((Variable) entry.getKey(), (Domain) entry.getValue());
                    }
                    current = builder.build();
                    changed = true;
                }
            }
        }
        return ConsistencyResult.feasible(current);
    }
}
