package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accumulates learned nogoods during backtracking search.
 * <p>
 * A nogood is a partial assignment (variable → value map) that is known to lead to failure.
 * Any future search node whose assignment subsumes a recorded nogood is pruned immediately.
 * <p>
 * Follows the same mutable-runtime-state-inside-@Value pattern as {@link SolverLimits}:
 * the store is immutable as a configuration object but accumulates nogoods during search.
 * The internal list is excluded from {@code equals}/{@code hashCode}/{@code toString}.
 * A single {@code NogoodStore} instance is shared across Luby restarts so learned nogoods
 * survive and benefit every subsequent restart.
 */
@Value
public class NogoodStore {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    List<Map<Variable<?>, Object>> nogoods = new CopyOnWriteArrayList<>();

    public NogoodStore() {}

    /**
     * Records a nogood. The map is copied defensively so callers may reuse their map.
     * <p>
     * An empty map is ignored rather than recorded: {@link #isViolated} treats a nogood as
     * matched when every one of its entries is present in the candidate assignment, which is
     * vacuously true for an empty nogood — recording one would prune every future assignment.
     * Per {@link io.github.rcrida.jcsp.solver.ConflictExplainer}, an empty map means "no
     * explanation available"; callers are expected to substitute the full assignment in that
     * case, but this guards against a caller that doesn't.
     */
    public void record(Map<Variable<?>, Object> nogood) {
        if (nogood.isEmpty()) return;
        nogoods.add(Map.copyOf(nogood));
    }

    /**
     * Returns true when the assignment subsumes any recorded nogood — i.e. the assignment
     * contains every variable-value pair of some learned nogood, making this branch a
     * guaranteed dead end.
     */
    public boolean isViolated(Assignment assignment) {
        Map<Variable<?>, Object> values = assignment.getValues();
        return nogoods.stream()
                .anyMatch(nogood -> nogood.entrySet().stream()
                        .allMatch(e -> e.getValue().equals(values.get(e.getKey()))));
    }

    public int size() {
        return nogoods.size();
    }
}
