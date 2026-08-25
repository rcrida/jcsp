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
 * AC3bit+rm: a bitset+residue variant of {@link AC3} (Lecoutre &amp; Vion, "Enforcing Arc
 * Consistency using Bitwise Operations"). Enforces the identical arc-consistency notion as
 * {@link AC3} -- same queue-driven closure over binary arcs, same wipeout/explanation semantics --
 * but replaces {@link AC3}'s {@code O(|D_i|*|D_j|)} per-revise support search (a nested loop calling
 * {@link BinaryConstraint#isSatisfiedByArcValues}) with two precomputed, per-{@link
 * ConstraintSatisfactionProblem}-graph pieces of structure:
 * <ol>
 *     <li><b>bit</b>: a {@link BitSet} per (arc, constraint, from-value) recording which
 *     to-side values are compatible, checked against the to-side's <em>current</em> live values
 *     (also a {@link BitSet}) via word-parallel {@link BitSet#intersects} instead of a
 *     value-by-value virtual-call scan.</li>
 *     <li><b>rm</b>: a multidirectional residue cache -- the last value found to support a given
 *     (arc, constraint, from-value) triple is tried first, and a support found in one direction is
 *     also recorded for the reverse arc, since {@link BinaryConstraint#isSatisfiedByArcValues} is
 *     the same underlying relation regardless of which {@link Arc} direction frames the call.</li>
 * </ol>
 * <p>
 * Both pieces of structure are built once per {@link ConstraintSatisfactionProblem}'s underlying
 * constraint graph (same {@link ConstraintSatisfactionProblem#computeAuxiliaryCacheIfAbsent}
 * mechanism {@link AC3#arcIndex} already uses for its own, purely structural cache) from {@link
 * ConstraintSatisfactionProblem#getDeclaredDomains()} -- the graph's original, widest-ever domains,
 * captured once at graph construction (see {@link io.github.rcrida.jcsp.ConstraintGraph#declaredDomains}'s
 * own Javadoc) -- rather than whichever domains happen to be live the first time this class is used
 * against that graph: a propagator whose cache is first populated from inside a bare {@code
 * MAC}/{@code DomWdegLubySearch} call (bypassing {@code PropagationFixpointSolver}'s own
 * whole-problem preprocessing) would otherwise only ever see whatever value got assigned first,
 * since {@code MAC#apply} narrows its target variable to a singleton before ever calling into this
 * class -- a real, previously-hit failure mode, not just a hypothetical one.
 * <p>
 * The residue arrays are shared and mutated for the rest of the graph's lifetime -- across every
 * search node, restart, and (unlike a naive reading of ADR-0021's precedent might suggest) even
 * concurrent branches racing on the same graph -- without any synchronization, and this is sound
 * rather than merely convenient: a residue slot only ever holds either its initial {@code -1} or a
 * value {@code y} found by scanning {@code support[i]}'s own bitset, so "{@code y} supports index
 * {@code i}" is a permanent structural fact about the (immutable, once-built) support table,
 * independent of which thread or search branch wrote it. The read side never trusts that fact by
 * itself: every use re-checks {@code liveJ.get(cachedY)} against the <em>reading</em> call's own
 * current domain, so a stale or racily-overwritten residue is simply treated as a miss (identical
 * cost to never having cached it) rather than ever causing an incorrect deletion. Individual {@code
 * int} array-element reads/writes are also always atomic per the JLS (no tearing), so concurrent
 * unsynchronized access can produce staleness but never a corrupted value. This is a different
 * shape of state to ADR-0021's own concern for {@code NaryTuplesConstraint} (a genuinely
 * backtracking-coupled, order-dependent "current tuple list"): a residue here is monotone and
 * self-verifying, not something that needs undo-on-backtrack.
 * <p>
 * An (arc, constraint) pair whose endpoints aren't both {@link DiscreteDomain} when this class's
 * cache is first built for a graph (e.g. one side is a {@link
 * io.github.rcrida.jcsp.domains.BoundedDomain}) has no precomputed support and falls back directly
 * to {@link AC3}'s own naive scan ({@link AC3#revise(Map, Arc, BinaryConstraint)}) -- reused rather
 * than duplicated, since it is exactly {@link AC3}'s existing, already-tested logic for that case.
 * <p>
 * <b>Not wired into any production chain</b> ({@code MAC}, {@code FixpointPropagation#PROPAGATORS},
 * {@code TreeSolver}, {@code LocalSolver.Factory#PREPROCESSORS} all use {@link AC3} directly) --
 * despite every fix above, JMH benchmarking (see {@code
 * io.github.rcrida.jcsp.benchmark.AC3BitRmBenchmark}, ADR-0022) found this class loses or ties
 * {@link AC3} across every scenario built from this codebase's typical cheap per-pair constraint
 * checks (comparators, table lookups), including domains spanning multiple {@link BitSet} words --
 * domain width alone doesn't get it a win. It only wins when a constraint's own {@link
 * BinaryConstraint#isSatisfiedByArcValues} does genuinely expensive work per pair, since this class
 * pays that cost once per pair at table-build time instead of on every revise call; real constraints
 * in this codebase are almost all cheap per-pair checks. Available as a documented, independently
 * tested alternative for exactly that expensive-check case -- construct the same chain {@code
 * FixpointPropagation}/{@code MAC} build, substituting this class for {@link AC3} at both call
 * sites, the way {@code AC3BitRmBenchmark#buildChain} does -- rather than wired in as a default. See
 * ADR-0022 for the full benchmark numbers and the optimization/correctness journey that produced
 * this class's current implementation (bitset-vs-clone, {@code Map.copyOf} vs plain {@code HashMap},
 * persisted-residue soundness, and the {@link IdentityHashMap}/canonical-{@link Arc} fix).
 */
@Slf4j
public class AC3BitRm implements ConstraintConsistency {
    public static final AC3BitRm INSTANCE = new AC3BitRm();

    private record ValueIndex(List<Object> valuesByIndex, Map<Object, Integer> indexOf) {
        int size() { return valuesByIndex.size(); }
    }

    /**
     * @param supports parallel to {@code arcConstraints.get(arc)}: {@code supports.get(arc).get(k)}
     *                 is {@code null} when that (arc, constraint) pair's endpoints weren't both
     *                 {@link DiscreteDomain} at build time (fall back to {@link AC3#revise(Map, Arc,
     *                 BinaryConstraint)}), otherwise a {@link BitSet}{@code []} indexed by the
     *                 from-side's {@link ValueIndex}.
     * @param residues shared, mutable, per-graph residue cache -- {@code residues.get(arc)[k]} is
     *                 {@code null} exactly when {@code supports.get(arc).get(k)} is (no bitset means
     *                 no residue to cache either), otherwise an {@code int[]} indexed like {@code
     *                 support[i]} above, holding the last value found to support each from-index
     *                 (initially all {@code -1}, "unknown"). See this class's own top-level Javadoc
     *                 for why sharing and mutating this across calls/threads is sound.
     * @param reverseArcs precomputed {@code arc -> Arc.of(arc.getTo(), arc.getFrom())}, so revising
     *                 an arc and updating its reverse's residue never needs to allocate a throwaway
     *                 {@link Arc} just to look one up.
     * @param canonicalArcs a plain, equality-based (not identity-based) {@code arc -> arc} map, used
     *                 solely to translate a caller-supplied {@link Arc} (e.g. {@link
     *                 #revise(ConstraintSatisfactionProblem, Arc)}'s own parameter, necessarily a
     *                 fresh, non-canonical {@code Arc.of(...)} instance from the caller's own
     *                 perspective) into the one canonical instance every {@link IdentityHashMap}
     *                 above is actually keyed on -- everywhere {@code arc} originates internally
     *                 (the queue-driven {@link #applyQueue}/{@link #applyQueueWithReason}) it is
     *                 already canonical and this translation is a same-instance no-op.
     */
    private record BitIndex(Set<Arc> allArcs, Map<Arc, List<BinaryConstraint<?, ?>>> arcConstraints,
                             Map<Variable<?>, List<Arc>> arcsByTarget,
                             Map<Variable<?>, ValueIndex> valueIndices,
                             Map<Arc, List<BitSet @Nullable []>> supports,
                             Map<Arc, int @Nullable [][]> residues,
                             Map<Arc, Arc> reverseArcs,
                             Map<Arc, Arc> canonicalArcs) {}

    private AC3BitRm() {}

    @Override
    public String toString() {
        return "AC3bit+rm";
    }

    /**
     * Memoized via {@link ConstraintSatisfactionProblem#computeAuxiliaryCacheIfAbsent}, keyed per
     * problem structure exactly like {@link AC3#arcIndex} -- see this class's own Javadoc for why a
     * domain-content-dependent cache is safe to build this way.
     */
    private BitIndex bitIndex(ConstraintSatisfactionProblem problem) {
        return problem.computeAuxiliaryCacheIfAbsent(BitIndex.class, this::buildBitIndex);
    }

    private BitIndex buildBitIndex(ConstraintSatisfactionProblem problem) {
        Set<BinaryConstraint<?, ?>> source = problem.getAllBinaryConstraints();
        // Grouped via a plain (equality-based) HashMap first, deliberately not IdentityHashMap:
        // BinaryConstraint#getArcs() builds a fresh Arc.of(...) per call, so when two different
        // constraints share the same (from, to) pair, each contributes its own .equals()-but-not-==
        // Arc instance -- grouping by identity here would wrongly split them into separate keys
        // instead of consolidating them under one canonical arc.
        Map<Arc, List<BinaryConstraint<?, ?>>> groupedByEquality = source.stream()
                .flatMap(binaryConstraint -> binaryConstraint.getArcs()
                        .map(arc -> new AbstractMap.SimpleEntry<>(arc, binaryConstraint)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toUnmodifiableList())));
        // Now that grouping has consolidated down to exactly one canonical Arc instance per (from,
        // to) pair, every map below can safely key on that instance by identity -- see this class's
        // own top-level Javadoc for why that's sound (not just an unverified assumption) and faster.
        Map<Arc, List<BinaryConstraint<?, ?>>> arcConstraints = new IdentityHashMap<>(groupedByEquality);
        Set<Arc> allArcs = Set.copyOf(arcConstraints.keySet());
        Map<Variable<?>, List<Arc>> arcsByTarget = allArcs.stream()
                .collect(Collectors.groupingBy(Arc::getTo, IdentityHashMap::new, Collectors.toUnmodifiableList()));

        // getDeclaredDomains(), not getVariableDomains(): the latter is this specific problem
        // snapshot's own current domains, which can already be narrower than what this graph's
        // variables will ever hold across its whole lineage (e.g. MAC#apply narrows its target
        // variable to a singleton before ever calling into this class) -- see
        // ConstraintGraph#declaredDomains's own Javadoc.
        Map<Variable<?>, Domain<?>> domains = problem.getDeclaredDomains();
        Map<Variable<?>, ValueIndex> valueIndices = new IdentityHashMap<>();
        for (Arc arc : allArcs) {
            buildValueIndexIfAbsent(valueIndices, arc.getFrom(), domains);
            buildValueIndexIfAbsent(valueIndices, arc.getTo(), domains);
        }

        Map<Arc, List<BitSet @Nullable []>> supports = new IdentityHashMap<>();
        for (var entry : arcConstraints.entrySet()) {
            Arc arc = entry.getKey();
            ValueIndex fromIndex = valueIndices.get(arc.getFrom());
            ValueIndex toIndex = valueIndices.get(arc.getTo());
            List<BitSet @Nullable []> perConstraint = new ArrayList<>();
            for (BinaryConstraint<?, ?> constraint : entry.getValue()) {
                perConstraint.add(fromIndex == null || toIndex == null
                        ? null
                        : buildSupport(arc, constraint, fromIndex, toIndex));
            }
            // Not List.copyOf: perConstraint deliberately holds null entries (no precomputed
            // support for that constraint index), and List.of/List.copyOf reject nulls.
            supports.put(arc, Collections.unmodifiableList(perConstraint));
        }

        // canonicalArcByEquality: a plain (equality-based) lookup from an ad hoc Arc.of(to, from) --
        // built solely to compute a reverse pairing, so necessarily a fresh, non-canonical instance
        // -- back to the one canonical Arc instance actually used as a key everywhere above.
        Map<Arc, Arc> canonicalArcByEquality = new HashMap<>();
        for (Arc arc : allArcs) canonicalArcByEquality.put(arc, arc);

        Map<Arc, int @Nullable [][]> residues = new IdentityHashMap<>();
        Map<Arc, Arc> reverseArcs = new IdentityHashMap<>();
        for (Arc arc : allArcs) {
            ValueIndex fromIndex = valueIndices.get(arc.getFrom());
            List<BitSet @Nullable []> arcSupports = supports.get(arc);
            int[][] perArc = new int[arcSupports.size()][];
            for (int k = 0; k < arcSupports.size(); k++) {
                if (arcSupports.get(k) != null) {
                    int[] fresh = new int[fromIndex.size()];
                    Arrays.fill(fresh, -1);
                    perArc[k] = fresh;
                }
            }
            residues.put(arc, perArc);
            reverseArcs.put(arc, canonicalArcByEquality.get(Arc.of(arc.getTo(), arc.getFrom())));
        }

        return new BitIndex(allArcs, arcConstraints, arcsByTarget,
                Collections.unmodifiableMap(valueIndices), Collections.unmodifiableMap(supports),
                Collections.unmodifiableMap(residues), Collections.unmodifiableMap(reverseArcs),
                Collections.unmodifiableMap(canonicalArcByEquality));
    }

    private static void buildValueIndexIfAbsent(Map<Variable<?>, ValueIndex> valueIndices, Variable<?> variable,
                                                  Map<Variable<?>, Domain<?>> domains) {
        if (valueIndices.containsKey(variable)) return;
        if (!(domains.get(variable) instanceof DiscreteDomain<?> discreteDomain)) return;
        List<Object> valuesByIndex = List.copyOf(discreteDomain.asCollection());
        // IdentityHashMap, not a plain equality-based HashMap: this is looked up once per domain
        // value on every single revise() call (the hottest path in this whole class). Sound because
        // domain narrowing (every DiscreteDomain builder's own delete()/build(), the only mechanism
        // used throughout this codebase to shrink a domain) only ever removes values from the
        // existing backing Set -- it never reconstructs a retained value, so every value reachable
        // from any later, narrower domain in this variable's lineage is == to the one captured here
        // from getDeclaredDomains(), not just .equals() to it. A plain Set already deduplicates by
        // .equals() before this loop ever runs, so there's no risk of two distinct-identity-but-equal
        // values needing to collide into one slot. See this class's own top-level Javadoc.
        Map<Object, Integer> indexOf = new IdentityHashMap<>();
        for (int i = 0; i < valuesByIndex.size(); i++) indexOf.put(valuesByIndex.get(i), i);
        valueIndices.put(variable, new ValueIndex(valuesByIndex, Collections.unmodifiableMap(indexOf)));
    }

    private static BitSet[] buildSupport(Arc arc, BinaryConstraint<?, ?> constraint, ValueIndex fromIndex, ValueIndex toIndex) {
        BitSet[] support = new BitSet[fromIndex.size()];
        for (int i = 0; i < fromIndex.size(); i++) {
            Object x = fromIndex.valuesByIndex().get(i);
            BitSet bits = new BitSet(toIndex.size());
            for (int j = 0; j < toIndex.size(); j++) {
                Object y = toIndex.valuesByIndex().get(j);
                if (constraint.isSatisfiedByArcValues(arc, x, y)) bits.set(j);
            }
            support[i] = bits;
        }
        return support;
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem) {
        return applyQueue(problem, new ArrayDeque<>(bitIndex(problem).allArcs()));
    }

    public Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        val index = bitIndex(problem);
        queue = canonicalize(index, queue);
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            val constraints = index.arcConstraints().get(arc);
            val supports = index.supports().get(arc);
            for (int k = 0; k < constraints.size(); k++) {
                val optionalRevisedD_i = revise(variableDomains, index, arc, k, constraints.get(k), supports.get(k));
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        log.debug("Domain of variable {} is empty after AC3bit+rm", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = index.arcsByTarget().getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return Optional.of(problem.withDomains(variableDomains));
    }

    /** Thin wrapper over {@link #applyQueueWithReason}, mirroring {@link AC3#explainConflict}. */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem problem) {
        ConsistencyResult result = applyQueueWithReason(problem, new ArrayDeque<>(bitIndex(problem).allArcs()));
        return result.isInfeasible() ? Optional.ofNullable(result.reason()) : Optional.empty();
    }

    @Override
    public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                              @Nullable Set<Variable<?>> changedSinceLastRun) {
        return applyQueueWithReason(problem, new ArrayDeque<>(bitIndex(problem).allArcs()));
    }

    /**
     * Single-pass combination of {@link #applyQueue} and a from-scratch {@code explainConflict}
     * traversal, mirroring {@link AC3#applyQueueWithReason} exactly -- same wipeout/soundness
     * argument (see that method's own Javadoc), unaffected by how support is computed here.
     */
    public ConsistencyResult applyQueueWithReason(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        val index = bitIndex(problem);
        queue = canonicalize(index, queue);
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            val constraints = index.arcConstraints().get(arc);
            val supports = index.supports().get(arc);
            for (int k = 0; k < constraints.size(); k++) {
                val optionalRevisedD_i = revise(variableDomains, index, arc, k, constraints.get(k), supports.get(k));
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        val reason = Propagatable.allSingletonReason(List.of(X_i, X_j), variableDomains);
                        return ConsistencyResult.infeasible(reason.isEmpty() ? null : GroundNogoodConstraint.of(reason));
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = index.arcsByTarget().getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return ConsistencyResult.feasible(problem.withDomains(variableDomains));
    }

    /**
     * Translates every element of a caller-supplied {@code queue} to this graph's own canonical
     * {@link Arc} instances (see {@link BitIndex#canonicalArcs()}'s own Javadoc for why that's
     * needed at all), once, up front -- not per {@link Queue#poll()} inside {@link #applyQueue}/
     * {@link #applyQueueWithReason}'s own loop, since every arc those loops requeue internally (via
     * {@link BitIndex#arcsByTarget()}) is already canonical, so translating on every poll would pay
     * a lookup for the (typically large) majority of polls that need no translation at all.
     */
    private static Queue<Arc> canonicalize(BitIndex index, Queue<Arc> queue) {
        Queue<Arc> canonical = new ArrayDeque<>(queue.size());
        for (Arc arc : queue) canonical.add(index.canonicalArcs().getOrDefault(arc, arc));
        return canonical;
    }

    /**
     * Revises {@code arc} alone (every constraint on it, not a full queue-driven closure) --
     * mirrors {@link AC3#revise(ConstraintSatisfactionProblem, Arc)}, used the same way by callers
     * that walk arcs one at a time outside a fixpoint (e.g. a topologically-sorted sweep).
     * <p>
     * Unlike {@link #applyQueue}/{@link #applyQueueWithReason}, {@code arc} here is caller-supplied
     * -- necessarily a fresh, non-canonical {@code Arc.of(...)} instance from the caller's own
     * perspective, only {@code .equals()} (not {@code ==}) to whatever this class's {@link
     * IdentityHashMap}-backed internal structures are actually keyed on -- so it's translated via
     * {@link BitIndex#canonicalArcs()} before being used for any lookup below.
     */
    public Optional<ConstraintSatisfactionProblem> revise(ConstraintSatisfactionProblem problem, Arc arc) {
        val index = bitIndex(problem);
        arc = index.canonicalArcs().getOrDefault(arc, arc);
        val constraints = index.arcConstraints().getOrDefault(arc, List.of());
        val supports = index.supports().getOrDefault(arc, List.of());
        val variableDomains = new HashMap<Variable<?>, Domain<?>>(problem.getVariableDomains());
        for (int k = 0; k < constraints.size(); k++) {
            val optionalRevisedD = revise(variableDomains, index, arc, k, constraints.get(k), supports.get(k));
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
     * Revises {@code arc}'s {@code constraintIndex}-th constraint against {@code domains}. When
     * {@code support} is {@code null} (this (arc, constraint) pair had a non-{@link DiscreteDomain}
     * endpoint when {@link #bitIndex}'s cache was built), delegates straight to {@link
     * AC3#revise(Map, Arc, BinaryConstraint)} rather than duplicating that scan.
     * <p>
     * Otherwise: builds the to-side's current-domain bitset once, then for each from-side value
     * checks the shared residue first (an O(1) live-bit test), falling back to a word-parallel
     * {@link BitSet#intersects} test against the precomputed support only on a residue miss --
     * {@link BitSet#intersects} rather than {@code clone()}+{@link BitSet#and}: no allocation is
     * needed just to answer the wipeout question, and when support does exist, scanning {@code
     * support[i]}'s own set bits against {@code liveJ} finds one without ever materialising the
     * intersection itself. A fresh support found this way is recorded both for this (arc, constraint,
     * from-index) and -- multidirectionally -- for the reverse arc's own residue at the found
     * to-index, since support is symmetric per constraint regardless of which {@link Arc} direction
     * frames the call. See this class's own top-level Javadoc for why writing into the shared,
     * cross-call residue arrays here is sound without synchronization.
     */
    private Optional<DiscreteDomain<?>> revise(Map<Variable<?>, Domain<?>> domains, BitIndex index,
                                                Arc arc, int constraintIndex,
                                                BinaryConstraint<?, ?> constraint, BitSet @Nullable [] support) {
        if (support == null) return AC3.revise(domains, arc, constraint);

        @SuppressWarnings("unchecked") DiscreteDomain<Object> D_i = (DiscreteDomain<Object>) domains.get(arc.getFrom());
        @SuppressWarnings("unchecked") DiscreteDomain<Object> D_j = (DiscreteDomain<Object>) domains.get(arc.getTo());
        ValueIndex fromIndex = index.valueIndices().get(arc.getFrom());
        ValueIndex toIndex = index.valueIndices().get(arc.getTo());

        BitSet liveJ = liveBits(toIndex, D_j);
        int[] residue = index.residues().get(arc)[constraintIndex];
        int[] reverseResidue = index.residues().get(index.reverseArcs().get(arc))[constraintIndex];

        List<Object> valuesToDelete = null;
        for (Object x : D_i.asCollection()) {
            // Auto-unboxes; a null here would mean this class's own closed-superset invariant
            // (ConstraintGraph#declaredDomains) was violated -- a genuine bug, not a case to guard
            // against, so this throws NullPointerException naturally rather than via an assert
            // (which JaCoCo's assert-elimination filter doesn't recognise for a null-check shape,
            // unlike a plain boolean condition).
            int i = fromIndex.indexOf().get(x);
            int cachedY = residue[i];
            if (cachedY >= 0 && liveJ.get(cachedY)) continue;
            if (!support[i].intersects(liveJ)) {
                if (valuesToDelete == null) valuesToDelete = new ArrayList<>();
                valuesToDelete.add(x);
            } else {
                int y = support[i].nextSetBit(0);
                while (!liveJ.get(y)) y = support[i].nextSetBit(y + 1);
                residue[i] = y;
                reverseResidue[y] = i;
            }
        }
        if (valuesToDelete == null) return Optional.empty();
        val revisedBuilder = D_i.toBuilder();
        valuesToDelete.forEach(revisedBuilder::delete);
        return Optional.of(revisedBuilder.build());
    }

    private static BitSet liveBits(ValueIndex index, DiscreteDomain<?> domain) {
        BitSet bits = new BitSet(index.size());
        for (Object value : domain.asCollection()) {
            // See the analogous unboxing note in revise() above.
            int i = index.indexOf().get(value);
            bits.set(i);
        }
        return bits;
    }
}
