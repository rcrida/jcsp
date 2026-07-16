package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.util.Comparator;
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
 * is immutable as a configuration object but accumulates nogoods during search. The internal set is
 * excluded from {@code equals}/{@code hashCode}/{@code toString}. A single {@code NogoodStore}
 * instance is shared across Luby restarts so learned nogoods survive and benefit every subsequent
 * restart.
 * <p>
 * <b>No graph rebuild, and no redundant merge either.</b> {@link #apply} augments a CSP with the
 * recorded nogoods via {@link ConstraintSatisfactionProblem#withNogoods}, which layers nogoods on
 * top of the CSP's existing constraint graph without ever touching it — a {@link NogoodConstraint}
 * never contributes to neighbours, binary decomposition, or cycle/connectivity analysis, so there
 * is nothing to recompute there regardless of how often {@code apply} is called. The flat union of
 * structural constraints and nogoods that backs {@link ConstraintSatisfactionProblem#getConstraints()}
 * used to be rebuilt from scratch on every {@code withNogoods} call regardless of whether anything
 * had actually changed since the last one — confirmed via {@code NogoodPropagationBenchmark} to be
 * the dominant per-node cost in long searches, since it scales with total nogood count (up to
 * {@code 20 * variableCount}) and ran unconditionally. {@link #snapshot} plus {@code
 * ConstraintSatisfactionProblem}'s own merge cache (keyed on that same stable reference) together
 * mean the whole pipeline collapses to a couple of reference checks between nogood-learning events.
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
     * Caches the last {@link Set#copyOf} snapshot of {@link #nogoods}, invalidated (set to {@code
     * null}) only inside {@link #record} and {@link #evictIfOverCap} when the set actually
     * mutates -- not on every {@link #apply} call. Between nogood-learning events (the common case
     * across most of a search tree), {@code apply} reuses this same immutable reference instead of
     * re-copying an unboundedly-growing set on every node; {@link ConstraintSatisfactionProblem}'s
     * own merge cache then keys off this same stable reference to skip its rebuild too.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    AtomicReference<Set<NogoodConstraint>> snapshot = new AtomicReference<>();

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
     * Records a nogood constraint, deduplicated via {@link Set#add} (see the class javadoc for
     * why a {@code Set} rather than a {@code List}). Callers typically get {@code nogood} from
     * {@link io.github.rcrida.jcsp.solver.ConflictExplainer#explain}'s {@code Optional} — there is
     * no "empty nogood" case to guard against here any more, since a {@link NogoodConstraint} is
     * non-empty by construction (see e.g. {@link io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint#of}).
     */
    public void record(NogoodConstraint nogood) {
        if (nogoods.add(nogood)) {
            snapshot.set(null);
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
        snapshot.set(null);
    }

    /**
     * Returns {@code csp} augmented with every nogood constraint recorded so far, via
     * {@link ConstraintSatisfactionProblem#withNogoods}, or {@code csp} unchanged if none have been
     * recorded yet. {@code withNogoods} never rebuilds {@code csp}'s constraint graph — nogoods are
     * layered on top of it. Reuses the cached {@link #snapshot} instead of copying {@link #nogoods}
     * again when nothing has been recorded/evicted since the last call (see {@link #snapshot}'s
     * javadoc) — the common case across most of a search tree, where many nodes are visited between
     * nogood-learning events.
     */
    public ConstraintSatisfactionProblem apply(ConstraintSatisfactionProblem csp) {
        if (nogoods.isEmpty()) return csp;
        Set<NogoodConstraint> current = snapshot.get();
        if (current == null) {
            current = Set.copyOf(nogoods);
            snapshot.set(current);
        }
        return csp.withNogoods(current);
    }

    public int size() {
        return nogoods.size();
    }
}
