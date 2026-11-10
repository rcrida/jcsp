package io.github.rcrida.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.DomainOverlay;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.variables.VariableMap;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the Arc-Consistency algorithm (AC-3) for constraint satisfaction problems.
 * This class applies the arc-consistency algorithm to enforce consistency on binary constraints
 * in the given problem. It attempts to reduce the domains of variables while ensuring that
 * each constraint is satisfied.
 * <p>
 * The algorithm works by maintaining a queue of binary constraints (arcs) and iteratively revising
 * the domains of variables associated with those constraints.
 */
@Slf4j
public class AC3 implements ConstraintConsistency {
    public static final AC3 INSTANCE = new AC3();

    /**
     * An arc together with the constraints revised along it, so that the revise queue holds
     * everything a revision needs and taking an arc off it costs no lookup.
     */
    record ArcEntry(Arc arc, List<BinaryConstraint<?, ?>> constraints) {}

    /**
     * {@code entries} is only for {@link #applyQueue}'s and {@link #applyQueueWithReason}'s public
     * {@link Arc} queues and the single-arc {@link #revise(ConstraintSatisfactionProblem, Arc)}; the
     * revise loop itself reads {@code entriesByTarget}.
     */
    private record ArcIndex(List<ArcEntry> allEntries, Map<Arc, ArcEntry> entries,
                             Map<Variable<?>, List<ArcEntry>> entriesByTarget) {}

    private AC3() {}

    @Override
    public String toString() {
        return "AC3";
    }

    /**
     * Memoized via {@link ConstraintSatisfactionProblem#computeAuxiliaryCacheIfAbsent}, keyed per
     * problem structure rather than on this class's own shared {@link #INSTANCE} — a single cache
     * slot on the singleton itself would let two different problems solved concurrently (e.g.
     * independent subproblems) keep evicting each other's entry; see that method's own Javadoc.
     */
    private ArcIndex arcIndex(ConstraintSatisfactionProblem problem) {
        return problem.computeAuxiliaryCacheIfAbsent(ArcIndex.class, this::buildArcIndex);
    }

    /**
     * Every arc whose {@link Arc#getTo} is {@code variable}, read straight off {@link
     * ArcIndex#entriesByTarget} -- the same memoized index {@link #applyQueue}'s own internal requeue
     * rule uses. Package-private so {@link MAC} can reuse it instead of filtering {@code
     * ConstraintSatisfactionProblem#getAllBinaryArcs()} itself, which cost {@code O(|arcs|)} plus a
     * fresh {@link java.util.HashSet} at every search node for a result this lookup gives in
     * {@code O(1)}.
     */
    List<ArcEntry> arcsInto(ConstraintSatisfactionProblem problem, Variable<?> variable) {
        return arcIndex(problem).entriesByTarget().getOrDefault(variable, List.of());
    }

    /**
     * Exactly the variables {@link #seedQueue} can turn into queued arcs -- the {@code to} sides,
     * since an arc {@code (X_k, X_i)} is only revisable when its support side {@code X_i} shrank.
     * Waking this pass for any other variable could only ever produce an empty queue.
     */
    @Override
    public Set<Variable<?>> variablesCovered(ConstraintSatisfactionProblem problem) {
        return arcIndex(problem).entriesByTarget().keySet();
    }

    /** The revise queue requeues every arc a narrowing makes revisable, so a pass ends arc-consistent. */
    @Override
    public boolean convergesInternally() {
        return true;
    }

    private ArcIndex buildArcIndex(ConstraintSatisfactionProblem problem) {
        List<ArcEntry> allEntries = problem.getAllBinaryArcConstraints().entrySet().stream()
                .map(e -> new ArcEntry(e.getKey(), e.getValue()))
                .toList();
        Map<Arc, ArcEntry> entries = allEntries.stream()
                .collect(Collectors.toUnmodifiableMap(ArcEntry::arc, entry -> entry));
        Map<Variable<?>, List<ArcEntry>> entriesByTarget = allEntries.stream()
                .collect(Collectors.groupingBy(entry -> entry.arc().getTo(), Collectors.toUnmodifiableList()));
        return new ArcIndex(allEntries, entries, entriesByTarget);
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem) {
        return run(problem, new ArrayDeque<>(arcIndex(problem).allEntries()));
    }

    /**
     * Seeds the revise queue from {@code changedSinceLastRun} rather than re-enqueuing every arc in
     * the problem, which is what this class did on every call until 2026-09-18 -- including at every
     * search node, where the incoming problem is the parent's already-arc-consistent CSP plus
     * whatever one branching decision narrowed.
     * <p>
     * Sound because it enqueues exactly what {@link #applyQueue}'s own internal requeue rule already
     * enqueues after it narrows a domain: an arc {@code (X_k, X_i)} can only newly prune {@code D_k}
     * when {@code D_i} -- its <em>support</em> side -- has shrunk, so {@link ArcIndex#entriesByTarget}
     * for each changed variable is the complete set of arcs that could have become revisable. Every
     * other arc was left arc-consistent by the pass that last ran to fixpoint over it, and nothing
     * outside {@code changedSinceLastRun} has touched it since.
     * <p>
     * Relies on the same per-round invariant {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency#apply(ConstraintSatisfactionProblem, Set)}
     * already relies on (see docs/adr/0019): a propagator running earlier in the <em>same</em>
     * {@link io.github.rcrida.jcsp.solver.FixpointPropagation} round may narrow a variable this call
     * therefore skips, but that narrowing lands in the next round's {@code changedSinceLastRun} diff,
     * and the fixpoint loop only exits after a whole round changes nothing -- so no arc is ever
     * dropped, only deferred by at most one round.
     * <p>
     * {@code null} (unknown -- a preprocessing call, or any caller with no parent state to diff
     * against) falls back to the full arc queue, exactly as before.
     */
    private Queue<ArcEntry> seedQueue(ConstraintSatisfactionProblem problem, @Nullable Set<Variable<?>> changedSinceLastRun) {
        val index = arcIndex(problem);
        if (changedSinceLastRun == null) return new ArrayDeque<>(index.allEntries());
        val queue = new ArrayDeque<ArcEntry>();
        for (Variable<?> variable : changedSinceLastRun) {
            queue.addAll(index.entriesByTarget().getOrDefault(variable, List.of()));
        }
        return queue;
    }

    /**
     * Genuine override of {@link ConstraintConsistency#apply(ConstraintSatisfactionProblem, Set)}'s
     * hint-ignoring default -- see {@link #seedQueue} for why the narrowed queue loses no pruning.
     */
    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem,
                                                          @Nullable Set<Variable<?>> changedSinceLastRun) {
        return run(problem, seedQueue(problem, changedSinceLastRun));
    }

    /** Revises {@code queue}'s arcs, and every arc a revision makes revisable, to arc consistency. */
    public Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        return run(problem, toEntries(problem, queue));
    }

    /** {@code queue}'s arcs as this problem's {@link ArcEntry}s, in order. */
    private Queue<ArcEntry> toEntries(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        val entries = arcIndex(problem).entries();
        val result = new ArrayDeque<ArcEntry>(queue.size());
        for (Arc arc : queue) result.add(entries.get(arc));
        return result;
    }

    /** {@link #applyQueue} over a queue of entries, so that taking an arc off it needs no lookup. */
    Optional<ConstraintSatisfactionProblem> run(ConstraintSatisfactionProblem problem, Queue<ArcEntry> queue) {
        val entriesByTarget = arcIndex(problem).entriesByTarget();
        val revisions = new Revisions(problem);
        while (!queue.isEmpty()) {
            val entry = queue.poll();
            val arc = entry.arc();
            val X_i = arc.getFrom();
            for (BinaryConstraint<?, ?> binaryConstraint : entry.constraints()) {
                val optionalRevisedD_i = revise(revisions.view(), arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        log.debug("Domain of variable {} is empty after AC3", X_i);
                        return Optional.empty();
                    }
                    revisions.record(X_i, revisedD_i);
                    requeueNeighbours(queue, entriesByTarget, arc);
                }
            }
        }
        return Optional.of(revisions.finish(problem));
    }

    /**
     * Queues every arc into {@code revised}'s {@code from} side except the one back from its {@code
     * to} side, which {@code revised} itself just made consistent.
     */
    private static void requeueNeighbours(Queue<ArcEntry> queue, Map<Variable<?>, List<ArcEntry>> entriesByTarget,
                                          Arc revised) {
        for (ArcEntry neighbour : entriesByTarget.getOrDefault(revised.getFrom(), List.of())) {
            if (!neighbour.arc().getFrom().equals(revised.getTo())) queue.add(neighbour);
        }
    }

    /**
     * Accumulates this traversal's narrowed domains, allocating nothing until the first arc actually
     * revises something. Until 2026-09-19 both traversals instead copied every domain into a fresh
     * {@link HashMap} on entry and handed the whole copy to {@link
     * ConstraintSatisfactionProblem#withDomains} on exit -- two {@code O(variables)} copies plus a new
     * {@link ConstraintSatisfactionProblem} on <em>every</em> call, including the overwhelmingly common
     * one where no arc revises anything at all. Returning {@code problem} itself unchanged in that case
     * also gives callers a correct {@code O(1)} reference-equality test for "this pass changed
     * nothing", which {@link io.github.rcrida.jcsp.solver.FixpointPropagation}'s propagator worklist
     * relies on to decide whether to wake anything -- {@link
     * io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency} already had this property via its
     * own accumulator, and AC3 was the one propagator that did not.
     */
    private static final class Revisions {
        private final Map<Variable<?>, Domain<?>> base;
        /** The narrowings read through {@link DomainOverlay} over {@link #base}, rather than a copy of it. */
        private @Nullable Map<Variable<?>, Domain<?>> working;
        private @Nullable Map<Variable<?>, Domain<?>> updates;

        Revisions(ConstraintSatisfactionProblem problem) {
            this.base = problem.getVariableDomains();
        }

        Map<Variable<?>, Domain<?>> view() {
            return working != null ? working : base;
        }

        void record(Variable<?> variable, Domain<?> narrowed) {
            if (updates == null) {
                updates = new VariableMap<>();
                working = DomainOverlay.of(base, updates);
            }
            updates.put(variable, narrowed);
        }

        ConstraintSatisfactionProblem finish(ConstraintSatisfactionProblem problem) {
            return updates == null ? problem : problem.withDomains(updates);
        }
    }

    /**
     * Thin wrapper over {@link #applyQueueWithReason}, kept for direct callers/tests and for
     * {@link ConstraintConsistency}'s own default {@code applyWithReason} fallback: returns {@link
     * Optional#empty()} when no wipeout occurred.
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem problem) {
        ConsistencyResult result = runWithReason(problem, new ArrayDeque<>(arcIndex(problem).allEntries()));
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    /** As {@link #apply(ConstraintSatisfactionProblem, Set)}, seeded via {@link #seedQueue}. */
    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        return runWithReason(problem, seedQueue(problem, changedSinceLastRun));
    }

    /**
     * Single-pass combination of {@link #applyQueue} and the old separate {@code explainConflict}
     * traversal: identical arc-revision bookkeeping and cost on the feasible path as {@link
     * #applyQueue} — {@link #revise} is called exactly the same number of times either way — and
     * only at the exact arc/point a wipeout is found does it compute a reason, instead of a second,
     * from-scratch replay.
     * <p>
     * A wipeout on arc {@code (X_i, X_j)} means: {@code revise} found that, of the values currently
     * in {@code D_i} (this traversal's own progressively-narrowed map — not X_i's declared/original
     * domain, its current, context-dependent one), none satisfy the constraint against {@code D_j}
     * (likewise current). Citing only {@code X_j}'s value would be <em>unsound</em> here even when
     * {@code X_j} is singleton: it would claim the constraint rules out every value {@code X_i}
     * could <em>ever</em> take, when it only ruled out what was in {@code D_i} <em>at this point in
     * the search</em> — a claim that doesn't generalise to branches where X_i's domain differs. The
     * reason is sound only when {@code X_i} is <em>also</em> singleton at this point ({@link
     * Propagatable#allSingletonReason}): then the wipeout reduces to one concrete pair violating the
     * (fixed, structural) constraint, unconditionally true regardless of search state.
     * <p>
     * Deliberately ground-only, unlike {@link io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency#applyWithReason},
     * which additionally falls back to a range-based nogood over a whole failing constraint's
     * variables when the ground reason is empty. That fallback's soundness argument rests on
     * {@link Propagatable#propagate} directly reporting infeasibility for a constraint given its
     * current domains; AC3's wipeout is a per-arc support-existence check on {@link #revise}, not a
     * single constraint's own {@code propagate} call, so extending the same argument here would
     * need separate justification not attempted in this pass.
     */
    public ConsistencyResult applyQueueWithReason(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        return runWithReason(problem, toEntries(problem, queue));
    }

    /** {@link #applyQueueWithReason} over a queue of entries, so that taking an arc off it needs no lookup. */
    ConsistencyResult runWithReason(ConstraintSatisfactionProblem problem, Queue<ArcEntry> queue) {
        val entriesByTarget = arcIndex(problem).entriesByTarget();
        val revisions = new Revisions(problem);
        while (!queue.isEmpty()) {
            val entry = queue.poll();
            val arc = entry.arc();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint<?, ?> binaryConstraint : entry.constraints()) {
                val optionalRevisedD_i = revise(revisions.view(), arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        val reason = Propagatable.allSingletonReason(List.of(X_i, X_j), revisions.view());
                        return ConsistencyResult.infeasible(reason.isEmpty() ? null : GroundNogoodConstraint.of(reason));
                    }
                    revisions.record(X_i, revisedD_i);
                    requeueNeighbours(queue, entriesByTarget, arc);
                }
            }
        }
        return ConsistencyResult.feasible(revisions.finish(problem));
    }

    public Optional<ConstraintSatisfactionProblem> revise(ConstraintSatisfactionProblem problem, Arc arc) {
        val entry = arcIndex(problem).entries().get(arc);
        val arcConstraints = entry == null ? List.<BinaryConstraint<?, ?>>of() : entry.constraints();
        val revisions = new Revisions(problem);
        for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints) {
            val optionalRevisedD = revise(revisions.view(), arc, binaryConstraint);
            if (optionalRevisedD.isPresent()) {
                val revisedD = optionalRevisedD.get();
                if (revisedD.isEmpty()) {
                    log.debug("Domain of variable {} is empty after revising arc {}", arc.getFrom(), arc);
                    return Optional.empty();
                }
                revisions.record(arc.getFrom(), revisedD);
            }
        }
        return Optional.of(revisions.finish(problem));
    }

    /**
     * Reads {@code arc}'s two domains from {@code problem} as it stands at the moment of the call —
     * the correct behaviour for this method's own external callers (e.g. {@link
     * io.github.rcrida.jcsp.solver.tree.TreeSolver}), which pass an already-current {@code problem}
     * per call. {@link #applyQueue} and {@link #explainConflict} instead call the {@link Map}
     * overload directly with their own progressively-narrowed domains, since a single call to
     * either of them revises many arcs across many rounds and must see each arc's effect on the
     * next, not just the domains {@code problem} was built with.
     */
    public Optional<DiscreteDomain<?>> revise(ConstraintSatisfactionProblem problem, Arc arc, BinaryConstraint<?, ?> constraint) {
        return revise(problem.getVariableDomains(), arc, constraint);
    }

    /**
     * Reads from whichever {@code domains} map it is handed, rather than re-reading a frozen
     * {@link ConstraintSatisfactionProblem} snapshot. Until 2026-07-16, {@link #applyQueue}/{@link
     * #applyQueueWithReason} called the 3-arg {@code revise} overload with the original, unchanging
     * {@code problem} parameter instead of their own progressively-narrowed map; since {@code revise}
     * is a pure function of the domains it's given, a given {@code (arc, constraint)} pair then
     * always produced the same result no matter how many times it was requeued — letting a
     * sufficiently dense/cyclic binary-constraint graph re-trigger the same neighbour-requeue
     * forever. Confirmed as a genuine hang, not just a slowdown: a 12-variable complete-graph binary
     * CSP with an unlucky tightness never returned, while 10 variables solved in 42ms.
     * <p>
     * Package-private, not {@code private}: {@link AC3BitRm} reuses this directly as its own
     * fallback for an (arc, constraint) pair whose endpoints aren't both {@link DiscreteDomain} at
     * its bit-index cache's build time, rather than duplicating this same naive scan.
     */
    static Optional<DiscreteDomain<?>> revise(Map<Variable<?>, Domain<?>> domains, Arc arc, BinaryConstraint<?, ?> constraint) {
        if (!(domains.get(arc.getFrom()) instanceof DiscreteDomain<?> D_i)) return Optional.empty();
        if (!(domains.get(arc.getTo()) instanceof DiscreteDomain<?> D_j)) return Optional.empty();
        // D_i/D_j are read via asCollection(), not toList() or a Stream pipeline: profiling found
        // both to be dominant costs on large CSPs. Streams (.stream().filter(...)) were rejected
        // first -- their Spliterator/SpinedNodeBuilder machinery was the single largest allocation
        // source in the whole solver, this being O(nodes * arcs) in call count even though each
        // individual D_i/D_j is typically tiny. toList() (List.copyOf(values()) for every concrete
        // DiscreteDomain except the singleton/empty ones) was the next attempt, avoiding Streams but
        // still paying a full array-copy of the domain's backing Set on every single revise() call
        // for a result that's only ever iterated once, never indexed -- asCollection() returns that
        // backing Set directly with no copy at all (see its own Javadoc).
        val jValues = D_j.asCollection();
        // Resolved once here rather than per value pair: the arc's orientation is the same for
        // every pair, and deriving it compares two Variables, i.e. two names.
        val arcFromLeft = constraint.isArcFromLeft(arc);
        // A constraint that can tell from D_j alone that no value of D_i can lose support skips the
        // scan below entirely. Optional.empty() is this method's own "no revision" answer -- the
        // same thing the scan returns when it deletes nothing -- so a true precheck and a scan that
        // finds nothing are indistinguishable to callers. Checked here rather than inside the loop
        // (as a per-value hasSupport override) because this also skips iterating D_i and rebuilding
        // the domain: both shapes were measured, and this one subsumed the inner one. See
        // docs/adr/0045-cheap-arc-prechecks-over-excluding-types-from-ac3.md.
        if (!constraint.mayPruneAlongArc(arcFromLeft, jValues)) return Optional.empty();
        List<Object> valuesToDelete = null;
        for (Object x : D_i.asCollection()) {
            if (!hasSupport(constraint, arcFromLeft, x, jValues)) {
                if (valuesToDelete == null) valuesToDelete = new ArrayList<>();
                valuesToDelete.add(x);
            }
        }
        if (valuesToDelete == null) return Optional.empty();
        val revisedBuilder = D_i.toBuilder();
        valuesToDelete.forEach(revisedBuilder::delete);
        return Optional.of(revisedBuilder.build());
    }

    private static boolean hasSupport(BinaryConstraint<?, ?> constraint, boolean arcFromLeft, Object x, Collection<?> jValues) {
        for (Object y : jValues) {
            if (constraint.isSatisfiedByArcValues(arcFromLeft, x, y)) return true;
        }
        return false;
    }
}
