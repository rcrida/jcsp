package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.util.Comparator;
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
 * <b>Forgetting policy.</b> The set is deduplicated and capped at {@link #maxNogoods}: once
 * {@link #record} pushes the size over the cap, the largest-arity nogoods are evicted first
 * (ties broken arbitrarily). Arity is used as a proxy for reusability rather than a fixed "too
 * big" cutoff, because there is no size threshold that's meaningful across problems of different
 * scale — a nogood's largest source, {@link io.github.rcrida.jcsp.solver.MacAndFixpointConflictExplainer}'s
 * full-assignment fallback, is bounded by search depth, not a constant. Evicting is always safe:
 * nogoods are derived facts, never a source of truth, so forgetting one at worst costs re-deriving
 * the same failure later — never correctness. {@link #forProblem} scales the cap itself to the
 * problem's size for the same reason, rather than using one constant for every CSP.
 */
@Value
public class NogoodStore {

    private static final int MIN_MAX_NOGOODS = 50;
    private static final int VARIABLES_PER_NOGOOD_BUDGET = 20;

    int maxNogoods;

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

    public NogoodStore() {
        this(MIN_MAX_NOGOODS);
    }

    public NogoodStore(int maxNogoods) {
        if (maxNogoods <= 0) throw new IllegalArgumentException("maxNogoods must be positive, got: " + maxNogoods);
        this.maxNogoods = maxNogoods;
    }

    /**
     * Scales the eviction cap to problem size rather than using one constant for every CSP:
     * currently a budget of {@link #VARIABLES_PER_NOGOOD_BUDGET} nogoods per variable, floored at
     * {@link #MIN_MAX_NOGOODS} so small problems still have comfortable headroom. Takes the whole
     * {@code csp} (not just a variable count) so the formula can draw on other attributes —
     * constraint count, arity distribution, etc. — without changing this method's signature again.
     */
    public static NogoodStore forProblem(ConstraintSatisfactionProblem csp) {
        int variableCount = csp.getVariableDomains().size();
        return new NogoodStore(Math.max(MIN_MAX_NOGOODS, VARIABLES_PER_NOGOOD_BUDGET * variableCount));
    }

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
        if (nogoods.add(NogoodConstraint.of(nogood))) {
            cachedAugmented.set(null); // nogood set grew: the cached augmented graph is now stale
            evictIfOverCap();
        }
    }

    /**
     * Evicts the largest-arity nogoods first once {@link #maxNogoods} is exceeded (see the class
     * javadoc for why arity rather than a fixed size threshold). {@code nogoods} is weakly
     * consistent under concurrent {@link #record} calls from parallel independent-subproblem
     * solves, so the excess computed here may be slightly stale by the time eviction finishes —
     * harmless, since a later call re-checks and corrects it.
     */
    private void evictIfOverCap() {
        int excess = nogoods.size() - maxNogoods;
        if (excess <= 0) return;
        nogoods.stream()
                .sorted(Comparator.comparingInt((NogoodConstraint n) -> n.getVariables().size()).reversed())
                .limit(excess)
                .forEach(nogoods::remove);
        cachedAugmented.set(null);
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
