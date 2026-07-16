package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.PropagationResult;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ConstraintConsistency} specialized for {@link NogoodConstraint}: unlike every other
 * entry in {@code PropagationFixpointSolver.PROPAGATORS} (each backed by a fixed, small constraint
 * count set at CSP-build time), the nogood set grows unboundedly over the course of a search (see
 * {@code NogoodStore}'s eviction cap, up to {@code 20 * variableCount}), so re-checking every one
 * of them on every fixpoint round — as the generic {@link FixpointConsistency} does — becomes the
 * dominant propagation cost in long searches (measured ~2.5-4x wall-clock overhead on Golomb ruler
 * UNSAT proofs, see {@code NogoodPropagationBenchmark}).
 *
 * <p>{@link #apply(ConstraintSatisfactionProblem, Set)} exploits the hint {@code
 * PropagationFixpointSolver#applyFixpoint} now threads through its outer loop: a {@link
 * NogoodConstraint}'s {@link io.github.rcrida.jcsp.consistency.Propagatable#propagate} result
 * depends only on the current domains of its own {@link io.github.rcrida.jcsp.constraints.Constraint#getVariables()}
 * (never on any other variable), so a nogood whose variables are all untouched since the last time
 * it was checked is provably unable to have produced a different result -- re-checking it is pure
 * waste, not an approximation. A {@code null} hint (first round of a fixpoint call, when nothing is
 * yet known to be safely unchanged) falls back to the same full scan {@link FixpointConsistency}
 * always does, so no round loses propagation strength -- only wasted re-checks are skipped.
 */
@Slf4j
public final class NogoodFixpointConsistency implements ConstraintConsistency {

    public static final NogoodFixpointConsistency INSTANCE = new NogoodFixpointConsistency();

    private NogoodFixpointConsistency() {
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        return apply(csp, null);
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp,
                                                          @Nullable Set<Variable<?>> changedSinceLastRun) {
        Set<NogoodConstraint> nogoods = csp.getNogoods();
        if (nogoods.isEmpty()) {
            log.debug("NogoodConstraint: fixpoint reached");
            return Optional.of(csp);
        }
        Collection<NogoodConstraint> toCheck = relevant(nogoods, changedSinceLastRun);
        if (toCheck.isEmpty()) {
            log.debug("NogoodConstraint: no nogood references a changed variable, skipping");
            return Optional.of(csp);
        }
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NogoodConstraint constraint : toCheck) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("NogoodConstraint: infeasible detected");
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        variableDomainEntry(builder, entry.getKey(), entry.getValue());
                    }
                    current = builder.build();
                    changed = true;
                }
            }
        }
        log.debug("NogoodConstraint: fixpoint reached");
        return Optional.of(current);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void variableDomainEntry(ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder,
                                            Variable<?> variable, Domain<?> domain) {
        builder.variableDomainEntry((Variable) variable, (Domain) domain);
    }

    /**
     * Returns every nogood in {@code nogoods} that references at least one variable in {@code
     * changed}, or all of {@code nogoods} unfiltered when {@code changed} is {@code null} (unknown
     * — the safe, always-correct fallback used on a fixpoint call's first round).
     */
    private static Collection<NogoodConstraint> relevant(Set<NogoodConstraint> nogoods,
                                                          @Nullable Set<Variable<?>> changed) {
        if (changed == null) return nogoods;
        return nogoods.stream()
                .filter(n -> n.getVariables().stream().anyMatch(changed::contains))
                .toList();
    }

    /**
     * Unlike {@link FixpointConsistency#explainConflict}'s generic two-tier reasoning, there is
     * only ever one tier here: every {@link NogoodConstraint} implementation's own {@code
     * explainInfeasible} unconditionally returns {@code Optional.of(this)} — a falsified nogood is
     * always its own sound explanation, with no singleton-gating or other condition under which it
     * would return empty — so {@link PropagationResult#reason()} is never {@code null} on the
     * infeasible branch below; {@link Optional#ofNullable} is used anyway rather than an unchecked
     * {@link Optional#of} so this stays correct even if a future {@code NogoodConstraint}
     * implementation doesn't uphold that guarantee. Always scans every nogood (no {@code changed}
     * hint) since this is a cold path, only reached once a conflict is already known to exist.
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        Set<NogoodConstraint> nogoods = csp.getNogoods();
        if (nogoods.isEmpty()) return Optional.empty();
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NogoodConstraint constraint : nogoods) {
                PropagationResult result = constraint.propagateWithReasons(current.getVariableDomains());
                if (result.isInfeasible()) {
                    return Optional.ofNullable(result.reason());
                }
                var updates = result.updatedDomains();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        variableDomainEntry(builder, entry.getKey(), entry.getValue());
                    }
                    current = builder.build();
                    changed = true;
                }
            }
        }
        return Optional.empty();
    }
}
