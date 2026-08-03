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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link ConstraintConsistency} that runs all {@link Propagatable} constraints of a given
 * type to fixpoint: filters, propagates, and repeats until no further domain reductions occur,
 * returning {@link Optional#empty()} as soon as any propagator signals infeasibility.
 *
 * <p>Use the {@link #of} factory to create instances. Adding a new propagator to the solver
 * chains ({@link io.github.rcrida.jcsp.solver.FixpointPropagation#PROPAGATORS}, {@code
 * LocalSolver.Factory.PREPROCESSORS})
 * requires only a single {@code FixpointConsistency.of(MyConstraint.class)} entry.
 */
@Slf4j
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class FixpointConsistency implements ConstraintConsistency {
    @NonNull Class<? extends Propagatable> constraintType;

    private record FilterCache(Set<Constraint> source, List<Propagatable> filtered) {}

    public static FixpointConsistency of(Class<? extends Propagatable> constraintType) {
        return new FixpointConsistency(constraintType);
    }

    @Override
    public String toString() {
        return "FixpointConsistency(" + constraintType.getSimpleName() + ")";
    }

    /**
     * Filters {@code csp.getConstraints()} to this instance's {@link #constraintType}, reusing the
     * last computed result when the incoming constraint {@link Set} is the exact same reference as
     * last time (a cheap identity check — always correct on a miss, since it just falls back to a
     * fresh filter). Note {@code csp.getConstraints()}, unlike {@code getAllBinaryConstraints()},
     * changes reference whenever a nogood is learned, not just when the structural constraint set
     * changes — this instance's own cache holder is keyed per {@link ConstraintSatisfactionProblem}
     * structure (via {@link ConstraintSatisfactionProblem#computeAuxiliaryCacheIfAbsent}, on {@link
     * #constraintType} — every {@link FixpointConsistency} in the solver chains targets an ordinary
     * structural constraint type, never a {@link NogoodConstraint} subtype, since those are handled
     * separately by {@link NogoodFixpointConsistency}, so this filtered result never actually
     * changes as nogoods accumulate in practice — but the reference-equality check below is what
     * makes that a correctness guarantee rather than an assumption), not on this instance itself:
     * a single cache slot shared across every {@link ConstraintSatisfactionProblem} solved by the
     * same shared {@code PROPAGATORS}-list instance would let two different problems solved
     * concurrently (e.g. independent subproblems) keep evicting each other's entry.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Propagatable> filteredConstraints(ConstraintSatisfactionProblem csp) {
        AtomicReference<FilterCache> holder = csp.computeAuxiliaryCacheIfAbsent(constraintType, ignored -> new AtomicReference<>());
        Set<Constraint> source = csp.getConstraints();
        FilterCache cached = holder.get();
        if (cached != null && cached.source() == source) {
            return cached.filtered();
        }
        List<Propagatable> filtered = (List) source.stream().filter(constraintType::isInstance).toList();
        holder.set(new FilterCache(source, filtered));
        return filtered;
    }

    /**
     * Whether {@code csp} contains at least one {@link #constraintType} instance -- i.e. whether
     * this propagator could possibly do anything for it. Reuses {@link #filteredConstraints}'s own
     * per-{@link ConstraintSatisfactionProblem} cache, so a caller deciding whether to include this
     * propagator at all (see {@link io.github.rcrida.jcsp.solver.FixpointPropagation.Factory}) pays
     * no extra cost beyond what {@link #apply}/{@link #applyWithReason} would compute anyway.
     *
     * <p>Also checks {@link ConstraintSatisfactionProblem#getAllBinaryConstraints()}, not just
     * {@link #filteredConstraints}'s {@code csp.getConstraints()} source, because a sub-CSP built by
     * {@code ConstraintSatisfactionProblem#withVariableSubset} (cutset/tree decomposition) can
     * materialize a straddling {@code BinaryDecomposable}'s decomposition -- e.g. an
     * {@code IncreasingConstraint}'s pairwise {@code BinaryComparatorConstraint}s, or a {@code
     * PartitionConstraint}'s pairwise {@code DisjointConstraint}s -- as real structural constraints,
     * even though the original constraint they came from is dropped once it straddles the cut. A
     * filter computed once from the whole-solve top-level CSP (see {@link
     * io.github.rcrida.jcsp.solver.FixpointPropagation.Factory#forProblem}) would otherwise never
     * see those concrete types if the top-level CSP has no matching constraint of its own, silently
     * losing propagation on exactly the sub-problems decomposition was meant to make tractable. Safe
     * to check unconditionally for every {@link #constraintType}: {@link
     * ConstraintSatisfactionProblem#getAllBinaryConstraints()} only ever contains {@code
     * BinaryConstraint} instances, so this second check is a no-op for every non-binary {@link
     * #constraintType} (e.g. {@code AllDiffConstraint}, {@code SumBoundConstraint}).
     */
    public boolean appliesTo(ConstraintSatisfactionProblem csp) {
        return !filteredConstraints(csp).isEmpty()
                || csp.getAllBinaryConstraints().stream().anyMatch(constraintType::isInstance);
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        List<Propagatable> constraints = filteredConstraints(csp);
        var name = constraintType.getSimpleName();
        if (constraints.isEmpty()) {
            return Optional.of(csp);
        }
        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(current.getVariableDomains());
                if (result.isEmpty()) {
                    log.debug("{}: infeasible detected", name);
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    current = current.withDomains(updates);
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
     * can prove (e.g. {@link io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint} tries ground on its Hall-violating subset, then
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
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem csp,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        List<Propagatable> constraints = filteredConstraints(csp);
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
                    current = current.withDomains(updates);
                    changed = true;
                }
            }
        }
        return ConsistencyResult.feasible(current);
    }
}
