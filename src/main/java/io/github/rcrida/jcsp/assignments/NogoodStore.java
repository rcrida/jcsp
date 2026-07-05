package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Accumulates learned nogoods during backtracking search, as actual {@link NogoodConstraint}s
 * rather than a separate variable-value map matched against the search's explicit assignment.
 * <p>
 * A nogood is a specific combination of variable values known to lead to failure. Modelling it as
 * a constraint lets it join the same propagation fixpoint as every other constraint: it reasons
 * from domain state directly, so a variable forced to its forbidden value by some <em>other</em>
 * constraint's propagation — never explicitly assigned by search — is still caught, which a plain
 * assignment-map comparison could never see.
 * <p>
 * Backed by a {@code Set} (matching {@link ConstraintSatisfactionProblem}'s own constraint
 * storage) rather than a list: {@link NogoodConstraint} has value-based equality on its forbidden
 * map, so re-deriving the same nogood twice (e.g. independently in two branches before either
 * benefits from the other's recorded nogood) collapses into one entry instead of growing
 * unboundedly, keeping {@link #size()} a meaningful distinct-nogood count.
 * <p>
 * Follows the same mutable-runtime-state-inside-@Value pattern as {@link SolverLimits}: the store
 * is immutable as a configuration object but accumulates nogoods during search. The internal set
 * (and the cached augmented CSP described below) are excluded from
 * {@code equals}/{@code hashCode}/{@code toString}. A single {@code NogoodStore} instance is shared
 * across Luby restarts so learned nogoods survive and benefit every subsequent restart.
 * <p>
 * <b>Graph caching.</b> {@link #apply} augments a CSP with the recorded nogoods. Building a CSP
 * whose constraint set has grown forces {@link ConstraintSatisfactionProblem} to recompute its
 * constraint graph (neighbours, binary decomposition, cycle/connectivity analysis) — and
 * {@code apply} is called for every candidate at every search node. Since a search's base
 * constraints and variable set are fixed and only domains change between nodes, the augmented graph
 * depends solely on the nogood set. {@code apply} therefore caches the last augmented CSP and, while
 * the nogood set is unchanged, serves subsequent nodes by swapping only the domains into that cached
 * CSP via {@link ConstraintSatisfactionProblem#toBuilder()} — whose constraint-set-equality reuse
 * path returns the cached graph untouched. {@link #record} invalidates the cache whenever it adds a
 * genuinely new nogood, so the graph is rebuilt at most once per distinct learned nogood rather than
 * once per node.
 * <p>
 * <b>No forgetting policy.</b> The nogood set is deduplicated but never pruned — it grows
 * monotonically for the lifetime of a search (and across its Luby restarts). This is intentional
 * for the problem sizes this solver targets; a very long search would eventually want a
 * relevance-bounded nogood database (evicting low-utility nogoods) to cap the per-node propagation
 * and memory cost, which is not implemented here.
 */
@Value
public class NogoodStore {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    Set<NogoodConstraint> nogoods = ConcurrentHashMap.newKeySet();

    /**
     * The last CSP produced by {@link #apply}, reused as a graph template while the nogood set is
     * unchanged. Holds a final reference to mutable runtime state (same pattern as the
     * {@code nogoods} set); {@code null} means "rebuild on next {@link #apply}".
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    AtomicReference<ConstraintSatisfactionProblem> cachedAugmented = new AtomicReference<>();

    public NogoodStore() {}

    /**
     * Records a nogood. The map is copied defensively so callers may reuse their map.
     * <p>
     * An empty map is ignored rather than recorded: {@link NogoodConstraint#isSatisfiedBy} treats
     * a nogood as matched when every one of its entries is present in the candidate assignment,
     * which is vacuously true for an empty nogood — recording one would prune every future
     * assignment. Per {@link io.github.rcrida.jcsp.solver.ConflictExplainer}, an empty map means
     * "no explanation available"; callers are expected to substitute the full assignment in that
     * case, but this guards against a caller that doesn't.
     */
    public void record(Map<Variable<?>, Object> nogood) {
        if (nogood.isEmpty()) return;
        // TODO: implement a forgetting policy. The set is deduplicated but never pruned, so it grows
        //  monotonically across a search and its Luby restarts, capping per-node propagation and
        //  memory cost only by the number of distinct nogoods learned. A relevance-bounded database
        //  (e.g. evict low-activity nogoods past a size threshold) would bound both for long searches.
        if (nogoods.add(NogoodConstraint.of(nogood))) {
            cachedAugmented.set(null); // nogood set grew: the cached augmented graph is now stale
        }
    }

    /**
     * Returns {@code csp} augmented with every nogood constraint recorded so far, or {@code csp}
     * unchanged if none have been recorded yet. Since {@link ConstraintSatisfactionProblem}
     * stores constraints in a {@code Set}, re-adding nogoods already present in {@code csp} (e.g.
     * from an ancestor node further up the same search path) is a safe no-op — callers can call
     * this on every node without needing to track what's already included.
     */
    public ConstraintSatisfactionProblem apply(ConstraintSatisfactionProblem csp) {
        if (nogoods.isEmpty()) return csp;
        ConstraintSatisfactionProblem cached = cachedAugmented.get();
        if (cached != null) {
            // Reuse the cached augmented graph; only the domains differ between search nodes.
            // toBuilder() carries the cached constraints + graph, so swapping domains hits the
            // constructor's constraint-set-equality reuse path (no graph recomputation).
            return cached.toBuilder()
                    .clearVariableDomains()
                    .variableDomains(csp.getVariableDomains())
                    .build();
        }
        ConstraintSatisfactionProblem built = csp.toBuilder().constraints(nogoods).build();
        cachedAugmented.set(built);
        return built;
    }

    public int size() {
        return nogoods.size();
    }
}
