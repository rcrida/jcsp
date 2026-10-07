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
import java.util.BitSet;
import java.util.HashMap;
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

    /**
     * {@code indicesByVariable} indexes into {@code filtered} by position rather than holding
     * {@link Propagatable} references, so {@link ConstraintQueue} can track membership in a {@link
     * BitSet} keyed on those positions -- see its own Javadoc for why that replaced an
     * {@link java.util.IdentityHashMap}-backed {@link Set}.
     */
    private record FilterCache(Set<Constraint> source, List<Propagatable> filtered,
                               Map<Variable<?>, int[]> indicesByVariable) {}

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
     * Also builds {@link FilterCache#indicesByVariable}, a {@code Variable -> constraint positions}
     * index used by {@link ConstraintQueue} to skip constraint objects none of whose variables
     * changed since they were last checked -- built unconditionally, not lazily/optionally the way
     * {@link NogoodFixpointConsistency}'s own {@code NogoodStore#byVariable} index is: unlike the nogood
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
        Map<Variable<?>, List<Integer>> incidences = new HashMap<>();
        for (int i = 0; i < filtered.size(); i++) {
            for (Variable<?> variable : ((Constraint) filtered.get(i)).getVariables()) {
                incidences.computeIfAbsent(variable, ignored -> new ArrayList<>()).add(i);
            }
        }
        Map<Variable<?>, int[]> indicesByVariable = new HashMap<>(incidences.size() * 2);
        incidences.forEach((variable, indices) -> indicesByVariable.put(variable, toIntArray(indices)));
        FilterCache fresh = new FilterCache(source, filtered, indicesByVariable);
        holder.set(fresh);
        return fresh;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private List<Propagatable> filteredConstraints(ConstraintSatisfactionProblem csp) {
        return filterCache(csp).filtered();
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
    /**
     * The variables of this type's own constraints, straight off {@link
     * FilterCache#indicesByVariable} -- the same index {@link ConstraintQueue} already uses, so this
     * costs nothing beyond what {@link #apply} would compute anyway. Exact and stable for a given
     * constraint graph, since a constraint type's instances are fixed at CSP-build time.
     */
    @Override
    public Set<Variable<?>> variablesCovered(ConstraintSatisfactionProblem csp) {
        return filterCache(csp).indicesByVariable().keySet();
    }

    public boolean appliesTo(ConstraintSatisfactionProblem csp) {
        return !filteredConstraints(csp).isEmpty()
                || csp.getAllBinaryConstraints().stream().anyMatch(constraintType::isInstance);
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        return apply(csp, null);
    }

    /**
     * Visits only the constraints {@link ConstraintQueue} seeds from {@code changedSinceLastRun}
     * before running to fixpoint -- unlike the default {@link
     * ConstraintConsistency#apply(ConstraintSatisfactionProblem, Set)} inherited by most
     * other {@link ConstraintConsistency} implementors (which silently ignores {@code
     * changedSinceLastRun} and delegates to {@link #apply(ConstraintSatisfactionProblem)}), this is
     * a genuine override: skipping constraint objects none of whose variables changed since they
     * were last checked is pure waste elimination (see {@link ConstraintQueue}'s own Javadoc), not
     * an approximation, so this never loses propagation strength relative to the unfiltered scan.
     */
    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp,
                                                          @Nullable Set<Variable<?>> changedSinceLastRun) {
        FilterCache cache = filterCache(csp);
        var name = constraintType.getSimpleName();
        ConstraintQueue queue = new ConstraintQueue(cache, changedSinceLastRun);
        if (queue.isEmpty()) {
            return Optional.of(csp);
        }
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        for (Propagatable constraint = queue.poll(); constraint != null; constraint = queue.poll()) {
            var result = constraint.propagate(domains.view());
            if (result.isEmpty()) {
                log.debug("{}: infeasible detected", name);
                return Optional.empty();
            }
            var updates = result.get();
            if (!updates.isEmpty()) {
                domains.record(updates);
                queue.wake(updates.keySet());
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
     * {@code changedSinceLastRun} does double duty here: {@link ConstraintQueue} uses it to decide
     * which constraint <em>objects</em> to re-invoke at all (the win that matters when {@link
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
        ConstraintQueue queue = new ConstraintQueue(cache, changedSinceLastRun);
        if (queue.isEmpty()) return ConsistencyResult.feasible(csp);
        DomainAccumulator domains = new DomainAccumulator(csp.getVariableDomains());
        for (Propagatable constraint = queue.poll(); constraint != null; constraint = queue.poll()) {
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
                queue.wake(updates.keySet());
            }
        }
        return ConsistencyResult.feasible(domains.finish(csp));
    }

    /**
     * Per-constraint worklist driving {@link #apply}/{@link #applyWithReason} to fixpoint.
     * <p>
     * Replaces the nested {@code while (changed) for (constraint : constraints)} loop both methods
     * used until 2026-09-19, which re-propagated <em>every</em> relevant constraint on each pass
     * until a whole pass changed nothing. Here a constraint is re-propagated only when one of its own
     * variables has actually been narrowed since it last ran -- the same dirty-tracking argument
     * this class applies when seeding, extended to iterations within the call. On {@code
     * driverlogw-09.xml.lzma} -- 17,447 constraints of this type over 650 variables, so roughly 27
     * constraints share each variable -- the old shape rescanned hundreds of constraints per pass to
     * find the handful that could still prune.
     * <p>
     * Converging internally is also what lets {@link io.github.rcrida.jcsp.solver.FixpointPropagation}'s
     * own propagator worklist skip re-waking this pass for changes it made itself (see {@link
     * ConstraintConsistency#convergesInternally}): when this returns, no constraint of this type can
     * prune further against the domains it produced.
     */
    private static final class ConstraintQueue {
        private static final int[] NO_INDICES = new int[0];

        private final FilterCache cache;
        /**
         * Ring buffer of {@link FilterCache#filtered} positions. Capacity is exactly the filtered
         * constraint count, which is sufficient because {@link #queued} admits each position at most
         * once at a time, so the buffer can never hold more entries than it has slots -- no growth
         * path, and no bounds reasoning beyond that invariant.
         * <p>
         * Sizing it to the seed instead, with doubling growth, was built and measured: the thought
         * was that a type with many instances is exactly where one narrowed variable wakes a handful
         * of them, so {@code GraphColoring-3-fullins-4}'s 3,524 disequalities zero 14KB per {@code
         * apply} call to use a few slots. It made no measurable difference there and was slightly
         * worse on {@code GolombRuler-09-a4} and {@code driverlogw-09}, so the simpler shape stands.
         */
        private final int[] queue;
        private final BitSet queued;
        private int head;
        private int tail;
        private int size;

        /**
         * Seeds the queue with the constraints to visit: every one of them when {@code changed} is
         * {@code null} (unknown -- the safe, always-correct fallback for a fixpoint call's first
         * round, mirroring {@link NogoodFixpointConsistency#relevant}'s identical semantics), and
         * otherwise just those referencing a variable in {@code changed}. The latter folds what used
         * to be a separate {@code relevant()} pass into the seeding: it built a deduped {@link List}
         * only for the constructor to immediately copy it into the queue and again into the queued
         * set, three traversals and two allocations per {@code apply} call where this does one.
         */
        ConstraintQueue(FilterCache cache, @Nullable Set<Variable<?>> changed) {
            this.cache = cache;
            int count = cache.filtered().size();
            this.queue = new int[count];
            this.queued = new BitSet(count);
            if (changed == null) {
                for (int i = 0; i < count; i++) queue[i] = i;
                queued.set(0, count);
                size = count;
            } else {
                wake(changed);
            }
        }

        boolean isEmpty() {
            return size == 0;
        }

        @Nullable Propagatable poll() {
            if (size == 0) return null;
            int index = queue[head];
            if (++head == queue.length) head = 0;
            size--;
            queued.clear(index);
            return cache.filtered().get(index);
        }

        void wake(Set<Variable<?>> narrowed) {
            for (Variable<?> variable : narrowed) {
                for (int index : cache.indicesByVariable().getOrDefault(variable, NO_INDICES)) {
                    if (!queued.get(index)) {
                        queued.set(index);
                        queue[tail] = index;
                        if (++tail == queue.length) tail = 0;
                        size++;
                    }
                }
            }
        }
    }

    /** This pass runs its own constraints to fixpoint before returning -- see {@link ConstraintQueue}. */
    @Override
    public boolean convergesInternally() {
        return true;
    }
}
