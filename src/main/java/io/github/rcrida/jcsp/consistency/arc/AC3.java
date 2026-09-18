package io.github.rcrida.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
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

    private record ArcIndex(Set<Arc> allArcs, Map<Arc, List<BinaryConstraint<?, ?>>> arcConstraints,
                             Map<Variable<?>, List<Arc>> arcsByTarget) {}

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
     * ArcIndex#arcsByTarget} -- the same memoized index {@link #applyQueue}'s own internal requeue
     * rule uses. Package-private so {@link MAC} can reuse it instead of filtering {@code
     * ConstraintSatisfactionProblem#getAllBinaryArcs()} itself, which cost {@code O(|arcs|)} plus a
     * fresh {@link java.util.HashSet} at every search node for a result this lookup gives in
     * {@code O(1)}.
     */
    List<Arc> arcsInto(ConstraintSatisfactionProblem problem, Variable<?> variable) {
        return arcIndex(problem).arcsByTarget().getOrDefault(variable, List.of());
    }

    private ArcIndex buildArcIndex(ConstraintSatisfactionProblem problem) {
        Map<Arc, List<BinaryConstraint<?, ?>>> arcConstraints = problem.getAllBinaryArcConstraints();
        Map<Variable<?>, List<Arc>> arcsByTarget = arcConstraints.keySet().stream()
                .collect(Collectors.groupingBy(Arc::getTo, Collectors.toUnmodifiableList()));
        return new ArcIndex(arcConstraints.keySet(), arcConstraints, arcsByTarget);
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem) {
        val queue = new ArrayDeque<>(arcIndex(problem).allArcs());
        return applyQueue(problem, queue);
    }

    /**
     * Seeds the revise queue from {@code changedSinceLastRun} rather than re-enqueuing every arc in
     * the problem, which is what this class did on every call until 2026-09-18 -- including at every
     * search node, where the incoming problem is the parent's already-arc-consistent CSP plus
     * whatever one branching decision narrowed.
     * <p>
     * Sound because it enqueues exactly what {@link #applyQueue}'s own internal requeue rule already
     * enqueues after it narrows a domain: an arc {@code (X_k, X_i)} can only newly prune {@code D_k}
     * when {@code D_i} -- its <em>support</em> side -- has shrunk, so {@link ArcIndex#arcsByTarget}
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
    private Queue<Arc> seedQueue(ConstraintSatisfactionProblem problem, @Nullable Set<Variable<?>> changedSinceLastRun) {
        val index = arcIndex(problem);
        if (changedSinceLastRun == null) return new ArrayDeque<>(index.allArcs());
        val queue = new ArrayDeque<Arc>();
        for (Variable<?> variable : changedSinceLastRun) {
            queue.addAll(index.arcsByTarget().getOrDefault(variable, List.of()));
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
        return applyQueue(problem, seedQueue(problem, changedSinceLastRun));
    }

    public Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        val index = arcIndex(problem);
        val arcConstraints = index.arcConstraints();
        val arcsByTarget = index.arcsByTarget();
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints.get(arc)) {
                val optionalRevisedD_i = revise(variableDomains, arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        log.debug("Domain of variable {} is empty after AC3", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = arcsByTarget.getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return Optional.of(problem.withDomains(variableDomains));
    }

    /**
     * Thin wrapper over {@link #applyQueueWithReason}, kept for direct callers/tests and for
     * {@link ConstraintConsistency}'s own default {@code applyWithReason} fallback: returns {@link
     * Optional#empty()} when no wipeout occurred.
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem problem) {
        ConsistencyResult result = applyQueueWithReason(problem, new ArrayDeque<>(arcIndex(problem).allArcs()));
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    /** As {@link #apply(ConstraintSatisfactionProblem, Set)}, seeded via {@link #seedQueue}. */
    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                             @Nullable Set<Variable<?>> changedSinceLastRun) {
        return applyQueueWithReason(problem, seedQueue(problem, changedSinceLastRun));
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
        val index = arcIndex(problem);
        val arcConstraints = index.arcConstraints();
        val arcsByTarget = index.arcsByTarget();
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints.get(arc)) {
                val optionalRevisedD_i = revise(variableDomains, arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        val reason = Propagatable.allSingletonReason(List.of(X_i, X_j), variableDomains);
                        return ConsistencyResult.infeasible(reason.isEmpty() ? null : GroundNogoodConstraint.of(reason));
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = arcsByTarget.getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return ConsistencyResult.feasible(problem.withDomains(variableDomains));
    }

    public Optional<ConstraintSatisfactionProblem> revise(ConstraintSatisfactionProblem problem, Arc arc) {
        val arcConstraints = arcIndex(problem).arcConstraints().getOrDefault(arc, List.of());
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints) {
            val optionalRevisedD = revise(variableDomains, arc, binaryConstraint);
            if (optionalRevisedD.isPresent()) {
                val revisedD = optionalRevisedD.get();
                if (revisedD.isEmpty()) {
                    log.debug("Domain of variable {} is empty after revising arc {}", arc.getFrom(), arc);
                    return Optional.empty();
                }
                variableDomains.put(arc.getFrom(), revisedD);
            }
        }
        return Optional.of(problem.withDomains(variableDomains));
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
        List<Object> valuesToDelete = null;
        for (Object x : D_i.asCollection()) {
            if (!hasSupport(constraint, arc, x, jValues)) {
                if (valuesToDelete == null) valuesToDelete = new ArrayList<>();
                valuesToDelete.add(x);
            }
        }
        if (valuesToDelete == null) return Optional.empty();
        val revisedBuilder = D_i.toBuilder();
        valuesToDelete.forEach(revisedBuilder::delete);
        return Optional.of(revisedBuilder.build());
    }

    private static boolean hasSupport(BinaryConstraint<?, ?> constraint, Arc arc, Object x, Collection<?> jValues) {
        for (Object y : jValues) {
            if (constraint.isSatisfiedByArcValues(arc, x, y)) return true;
        }
        return false;
    }
}
