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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    private record FilterCache(Set<Constraint> source, List<Propagatable> filtered,
                               Map<Variable<?>, List<Propagatable>> byVariable) {}

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
     * <p>
     * Also builds {@link FilterCache#byVariable}, a {@code Variable -> constraints} index used by
     * {@link #relevant} to skip constraint objects none of whose variables changed since they were
     * last checked -- built unconditionally, not lazily/optionally the way {@link
     * NogoodFixpointConsistency}'s own {@code NogoodStore#byVariable} index is: unlike the nogood
     * set, {@code source} here is fixed at CSP-build time and never mutates mid-solve, so there is
     * no "expensive to keep rebuilding" tradeoff to weigh (the two reverted eager-nogood-index
     * attempts {@link NogoodFixpointConsistency} documents don't apply -- this index is built once
     * per distinct {@code source} reference and reused for that {@link ConstraintSatisfactionProblem}'s
     * entire solve, exactly like {@link FilterCache#filtered} itself already is).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private FilterCache filterCache(ConstraintSatisfactionProblem csp) {
        AtomicReference<FilterCache> holder = csp.computeAuxiliaryCacheIfAbsent(constraintType, ignored -> new AtomicReference<>());
        Set<Constraint> source = csp.getConstraints();
        FilterCache cached = holder.get();
        if (cached != null && cached.source() == source) {
            return cached;
        }
        List<Propagatable> filtered = (List) source.stream().filter(constraintType::isInstance).toList();
        Map<Variable<?>, List<Propagatable>> byVariable = new HashMap<>();
        for (Propagatable constraint : filtered) {
            for (Variable<?> variable : ((Constraint) constraint).getVariables()) {
                byVariable.computeIfAbsent(variable, ignored -> new ArrayList<>()).add(constraint);
            }
        }
        FilterCache fresh = new FilterCache(source, filtered, byVariable);
        holder.set(fresh);
        return fresh;
    }

    private List<Propagatable> filteredConstraints(ConstraintSatisfactionProblem csp) {
        return filterCache(csp).filtered();
    }

    /**
     * Returns every constraint in {@code cache}'s filtered list that references at least one
     * variable in {@code changed}, or the full unfiltered list when {@code changed} is {@code null}
     * (unknown -- the safe, always-correct fallback for a fixpoint call's first round, mirroring
     * {@link NogoodFixpointConsistency#relevant}'s identical semantics). The single-variable case
     * (the overwhelming majority in practice -- a fixpoint round typically narrows one variable at a
     * time) returns {@link FilterCache#byVariable}'s own backing list directly, with no extra
     * allocation. The multi-variable case dedupes via an {@link IdentityHashMap}-backed {@link Set}
     * rather than relying on a constraint's own {@code equals}/{@code hashCode} (e.g. {@link
     * io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint}'s recursively walks a {@code
     * Set<Assignment>}) -- identity is sufficient here since a given constraint only ever appears
     * once per {@link FilterCache#byVariable} entry it's stored under, same reasoning as {@link
     * NogoodFixpointConsistency#fromIndex}.
     */
    private static List<Propagatable> relevant(FilterCache cache, @Nullable Set<Variable<?>> changed) {
        if (changed == null) return cache.filtered();
        if (changed.size() == 1) {
            return cache.byVariable().getOrDefault(changed.iterator().next(), List.of());
        }
        Set<Propagatable> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Variable<?> variable : changed) {
            result.addAll(cache.byVariable().getOrDefault(variable, List.of()));
        }
        return new ArrayList<>(result);
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
        return apply(csp, null);
    }

    /**
     * Filters to {@link #relevant} constraints before running to fixpoint -- unlike the default
     * {@link ConstraintConsistency#apply(ConstraintSatisfactionProblem, Set)} inherited by most
     * other {@link ConstraintConsistency} implementors (which silently ignores {@code
     * changedSinceLastRun} and delegates to {@link #apply(ConstraintSatisfactionProblem)}), this is
     * a genuine override: skipping constraint objects none of whose variables changed since they
     * were last checked is pure waste elimination (see {@link #relevant}'s own Javadoc), not an
     * approximation, so this never loses propagation strength relative to the unfiltered scan.
     */
    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp,
                                                          @Nullable Set<Variable<?>> changedSinceLastRun) {
        FilterCache cache = filterCache(csp);
        var name = constraintType.getSimpleName();
        List<Propagatable> constraints = relevant(cache, changedSinceLastRun);
        if (constraints.isEmpty()) {
            return Optional.of(csp);
        }
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                var result = constraint.propagate(domains.view());
                if (result.isEmpty()) {
                    log.debug("{}: infeasible detected", name);
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    domains.record(updates);
                    changed = true;
                }
            }
        }
        log.debug("{}: fixpoint reached", name);
        return Optional.of(domains.finish(csp));
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
     * constraint's {@link Propagatable#propagate(Map, Set)} exactly once — identical cost to
     * {@link #apply} on the feasible path, since nothing extra is allocated or computed there —
     * and only on the constraint that actually causes a domain wipeout does it call {@link
     * Propagatable#explainInfeasible} to derive a reason, tried in the same two tiers {@link
     * #explainConflict} used to: (1) the constraint's own explanation, (2) {@link
     * RangeNogoodConstraint#fromCurrentBounds} over its whole variable set as a generic fallback.
     * {@code changedSinceLastRun} does double duty here: {@link #relevant} uses it to decide which
     * constraint <em>objects</em> to re-invoke at all (the win that matters when {@link
     * #constraintType} has many instances, e.g. thousands of small XCSP3 {@code <group>}-templated
     * table constraints), and each constraint still separately receives it via {@link
     * Propagatable#propagate(Map, Set)} so it can also skip <em>internal</em> sub-computations whose
     * inputs provably didn't change (of no help when a type typically has only one instance, e.g.
     * {@code DiffnConstraint} -- found via JFR profiling a hard XCSP3 packing instance to matter for
     * exactly that constraint, whose own cost is dominated by pairwise checks across all its
     * rectangles, not by how many constraint objects exist).
     */
    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem csp,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        FilterCache cache = filterCache(csp);
        List<Propagatable> constraints = relevant(cache, changedSinceLastRun);
        if (constraints.isEmpty()) return ConsistencyResult.feasible(csp);
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Propagatable constraint : constraints) {
                Optional<Map<Variable<?>, Domain<?>>> result = constraint.propagate(domains.view(), changedSinceLastRun);
                if (result.isEmpty()) {
                    NogoodConstraint reason = constraint.explainInfeasible(domains.view()).orElse(null);
                    if (reason == null) {
                        reason = RangeNogoodConstraint.fromCurrentBounds(
                                ((Constraint) constraint).getVariables(), domains.view()).orElse(null);
                    }
                    return ConsistencyResult.infeasible(reason);
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
