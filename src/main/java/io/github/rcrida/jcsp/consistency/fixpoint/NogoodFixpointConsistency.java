package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ConstraintConsistency} specialized for {@link NogoodConstraint}: unlike every other
 * entry in {@link io.github.rcrida.jcsp.solver.FixpointPropagation#PROPAGATORS} (each backed by a
 * fixed, small constraint count set at CSP-build time), the nogood set grows unboundedly over the
 * course of a search (see
 * {@link io.github.rcrida.jcsp.assignments.NogoodStore}'s eviction cap, up to {@code 20 * variableCount}), so re-checking every one
 * of them on every fixpoint round — as the generic {@link FixpointConsistency} does — becomes the
 * dominant propagation cost in long searches (measured ~2.5-4x wall-clock overhead on Golomb ruler
 * UNSAT proofs, see {@code NogoodPropagationBenchmark}).
 *
 * <p>{@link #apply(ConstraintSatisfactionProblem, Set)} exploits the hint {@link
 * io.github.rcrida.jcsp.solver.FixpointPropagation#applyFixpoint} now threads through its outer
 * loop: a {@link
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
    public String toString() {
        return "NogoodFixpointConsistency";
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
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NogoodConstraint constraint : toCheck) {
                var result = constraint.propagate(domains.view());
                if (result.isEmpty()) {
                    log.debug("NogoodConstraint: infeasible detected");
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    domains.record(updates);
                    changed = true;
                }
            }
        }
        log.debug("NogoodConstraint: fixpoint reached");
        return Optional.of(domains.finish(csp));
    }

    /**
     * Returns every nogood in {@code nogoods} that references at least one variable in {@code
     * changed}, or all of {@code nogoods} unfiltered when {@code changed} is {@code null} (unknown
     * — the safe, always-correct fallback used on a fixpoint call's first round). Still an O({@code
     * nogoods.size()}) scan, but replaces the original nested-{@link java.util.stream.Stream} filter
     * (JFR-profiled on a nogood-heavy XCSP3 sports-scheduling instance to spend most of its own time
     * in {@code Stream}/{@code Spliterator} plumbing, not the underlying membership check itself)
     * with {@link Collections#disjoint}, which iterates whichever of the two collections is smaller
     * against a plain {@link Set#contains} on the other -- same asymptotic scan cost over {@code
     * nogoods}, no stream pipeline construction per nogood. A genuine {@code Variable ->
     * Set<NogoodConstraint>} index was also tried (real algorithmic improvement, O({@code
     * changed.size()}) instead of O({@code nogoods.size()})) but measured *worse* on the same
     * profiled scenario: rebuilding the whole index from scratch on every newly-learned nogood
     * (roughly once per node here) outweighed the savings, since a node's fixpoint only reuses that
     * index across a handful of rounds before the next nogood invalidates it. This simpler version
     * has no such rebuild tax and measured both fewer nodes lost to overhead (higher node throughput
     * under a fixed time budget) and a lower nogood-related CPU share than the indexed version.
     */
    private static Collection<NogoodConstraint> relevant(Set<NogoodConstraint> nogoods, @Nullable Set<Variable<?>> changed) {
        if (changed == null) return nogoods;
        List<NogoodConstraint> result = new ArrayList<>();
        for (NogoodConstraint nogood : nogoods) {
            if (!Collections.disjoint(nogood.getVariables(), changed)) {
                result.add(nogood);
            }
        }
        return result;
    }

    /**
     * Thin wrapper over {@link #applyWithReason}, kept for direct callers/tests and for {@link
     * ConstraintConsistency}'s own default {@code applyWithReason} fallback. Always scans every
     * nogood ({@code null} hint, matching {@link #relevant}'s "unknown, full scan" semantics) since
     * this is a cold path, only reached once a conflict is already known to exist.
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem csp) {
        ConsistencyResult result = applyWithReason(csp, null);
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    /**
     * Single-pass combination of {@link #apply} and the old separate {@code explainConflict}
     * traversal: calls each nogood's plain {@link io.github.rcrida.jcsp.consistency.Propagatable#propagate}
     * exactly once — identical cost to {@link #apply} on the feasible path — and only on the nogood
     * that actually causes a wipeout does it call {@link
     * io.github.rcrida.jcsp.consistency.Propagatable#explainInfeasible}. Unlike {@link
     * FixpointConsistency#applyWithReason}'s generic two-tier reasoning, there is only ever one
     * tier here: every {@link NogoodConstraint} implementation's own {@code explainInfeasible}
     * unconditionally returns {@code Optional.of(this)} — a falsified nogood is always its own
     * sound explanation, with no singleton-gating or other condition under which it would return
     * empty.
     */
    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem csp,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        Set<NogoodConstraint> nogoods = csp.getNogoods();
        if (nogoods.isEmpty()) return ConsistencyResult.feasible(csp);
        Collection<NogoodConstraint> toCheck = relevant(nogoods, changedSinceLastRun);
        if (toCheck.isEmpty()) return ConsistencyResult.feasible(csp);
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NogoodConstraint constraint : toCheck) {
                var result = constraint.propagate(domains.view());
                if (result.isEmpty()) {
                    return ConsistencyResult.infeasible(
                            constraint.explainInfeasible(domains.view()).orElse(null));
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    domains.record(updates);
                    changed = true;
                }
            }
        }
        return ConsistencyResult.feasible(domains.finish(csp));
    }
}
