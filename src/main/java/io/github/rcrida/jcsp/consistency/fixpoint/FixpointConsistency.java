package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.consistency.PropagationResult;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

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
     * Re-runs the fixpoint using {@link Propagatable#propagateWithReasons} and returns the nogood
     * that explains a domain wipeout, or {@link Optional#empty()} if this constraint type caused
     * no conflict (the conflict is in a different {@link FixpointConsistency}). Tries, in order:
     * (1) the failing constraint's own {@link Propagatable#explainInfeasible} via {@code result.reason()}
     * — tightest when it applies, and free to be a ground or a range nogood depending on what the
     * propagator itself can prove (e.g. {@code AllDiffConstraint} tries ground on its Hall-violating
     * subset, then range over that same subset); (2) {@link RangeNogoodConstraint#fromCurrentBounds}
     * over the failing constraint's <em>entire</em> variable set — the generic fallback for
     * propagators that don't provide anything tighter, sound whenever (1) is {@code null}, since
     * {@code propagateWithReasons} already reported infeasibility given exactly these current
     * domains.
     * <p>
     * Earlier (feasible-step) reasons are never accumulated across constraints on the way to the
     * wipeout: the default {@code propagateWithReasons} always reports a {@code null} reason on its
     * feasible path, and no implementor overrides it to do otherwise, so only the terminal
     * infeasible step's own reason (or its tier-2 fallback) ever contributes anything.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        List<Propagatable> constraints = (List) csp.getConstraints().stream()
                .filter(constraintType::isInstance)
                .toList();
        if (constraints.isEmpty()) return Optional.empty();
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                PropagationResult result = constraint.propagateWithReasons(current.getVariableDomains());
                if (result.isInfeasible()) {
                    NogoodConstraint reason = result.reason();
                    if (reason != null) return Optional.of(reason);
                    return RangeNogoodConstraint.fromCurrentBounds(
                            ((Constraint) constraint).getVariables(), current.getVariableDomains());
                }
                var updates = result.updatedDomains();
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
        return Optional.empty();
    }
}
