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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        Collection<NogoodConstraint> toCheck = relevant(csp, nogoods, changedSinceLastRun);
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
     * — the safe, always-correct fallback used on a fixpoint call's first round).
     * <p>
     * When {@code csp} carries a live {@code Variable -> nogoods} index (see {@link
     * io.github.rcrida.jcsp.assignments.NogoodStore#byVariable}), looks affected nogoods up directly
     * — O({@code changed.size()}) — instead of scanning every one of {@code nogoods}. Two earlier
     * attempts at this exact idea were reverted after measuring worse: a {@code Variable ->
     * Set<NogoodConstraint>} index rebuilt from scratch on every newly-learned nogood, and (this
     * session) a single-slot cache keyed on {@code nogoods}' own identity that rebuilt on a miss —
     * both paid an O({@code nogoods.size()}) rebuild on essentially every learn event, which
     * outweighed the O({@code changed.size()}) win, since a node's fixpoint only reuses either
     * index across a handful of rounds before the next learned/evicted nogood invalidates it. {@link
     * io.github.rcrida.jcsp.assignments.NogoodStore#byVariable} avoids that failure mode by never
     * rebuilding in bulk at all: it's maintained incrementally, touching only the changed nogood's
     * own O(arity) variables on every {@code record}/eviction, so there is no rebuild cost to
     * outweigh the lookup savings with.
     * <p>
     * Falls back to the plain O({@code nogoods.size()}) scan — {@link Collections#disjoint}, which
     * iterates whichever of the two collections is smaller against a plain {@link Set#contains} on
     * the other, replacing an original nested-{@link java.util.stream.Stream} filter JFR-profiled to
     * spend most of its own time in {@code Stream}/{@code Spliterator} plumbing rather than the
     * membership check itself — when no index is available (e.g. nogoods added directly via the
     * builder's {@code nogood(...)} method rather than through a {@link
     * io.github.rcrida.jcsp.assignments.NogoodStore}, as in most direct/test construction).
     */
    private static Collection<NogoodConstraint> relevant(ConstraintSatisfactionProblem csp,
            Set<NogoodConstraint> nogoods, @Nullable Set<Variable<?>> changed) {
        if (changed == null) return nogoods;
        Map<Variable<?>, Set<NogoodConstraint>> index = csp.getNogoodsByVariable();
        if (index != null) return fromIndex(index, changed);
        List<NogoodConstraint> result = new ArrayList<>();
        for (NogoodConstraint nogood : nogoods) {
            if (!Collections.disjoint(nogood.getVariables(), changed)) {
                result.add(nogood);
            }
        }
        return result;
    }

    /**
     * Unions {@code index}'s entries for each variable in {@code changed}. The single-variable case
     * (the overwhelming majority in practice — a fixpoint round typically narrows one variable at a
     * time) returns the index's own backing set directly, with no extra allocation or deduplication
     * needed. The multi-variable case dedupes via an {@link IdentityHashMap}-backed {@link Set}
     * rather than relying on {@link NogoodConstraint}'s own {@code equals}/{@code hashCode} (already
     * known expensive elsewhere in this class — it recursively walks a {@code Set<Variable<?>>}) —
     * identity is sufficient here since a given nogood only ever appears once per {@code index}
     * entry it's stored under.
     */
    private static Collection<NogoodConstraint> fromIndex(Map<Variable<?>, Set<NogoodConstraint>> index, Set<Variable<?>> changed) {
        if (changed.size() == 1) {
            return index.getOrDefault(changed.iterator().next(), Set.of());
        }
        Set<NogoodConstraint> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Variable<?> v : changed) {
            result.addAll(index.getOrDefault(v, Set.of()));
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
        Collection<NogoodConstraint> toCheck = relevant(csp, nogoods, changedSinceLastRun);
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
