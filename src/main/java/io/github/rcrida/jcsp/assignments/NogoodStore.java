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
 * is excluded from {@code equals}/{@code hashCode}/{@code toString}. A single {@code NogoodStore}
 * instance is shared across Luby restarts so learned nogoods survive and benefit every subsequent
 * restart.
 */
@Value
public class NogoodStore {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    Set<NogoodConstraint> nogoods = ConcurrentHashMap.newKeySet();

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
        nogoods.add(NogoodConstraint.of(nogood));
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
        return csp.toBuilder().constraints(nogoods).build();
    }

    public int size() {
        return nogoods.size();
    }
}
