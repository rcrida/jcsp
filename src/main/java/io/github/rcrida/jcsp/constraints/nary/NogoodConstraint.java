package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An n-ary constraint modelling a learned nogood: a specific combination of variable values known
 * to be jointly infeasible. Equivalent to the clause {@code OR(x1 != v1, x2 != v2, ..., xk != vk)}
 * over {@code forbidden} — violated only when every one of its variables is assigned exactly its
 * forbidden value.
 * <p>
 * Recording a nogood as an actual constraint (rather than a separate variable-value map matched
 * against the search's explicit assignment) lets it participate in the same propagation fixpoint
 * as every other constraint: {@link #propagate} reasons from current domain state directly, so a
 * variable forced to its forbidden singleton value by some <em>other</em> constraint's propagation
 * — never explicitly assigned by search — is still caught. A plain assignment-map comparison
 * could never see that case, since a variable that's only ever narrowed by propagation never
 * appears in any candidate assignment's value map at all.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NogoodConstraint extends NaryConstraint implements Propagatable {
    @NonNull private final Map<Variable<?>, Object> forbidden;

    public static NogoodConstraint of(@NonNull Map<Variable<?>, Object> forbidden) {
        // An empty forbidden map is the empty clause: propagate() would report infeasible for every
        // state (undeterminedCount == 0), pruning the whole search. Callers must not record one.
        assert !forbidden.isEmpty() : "NogoodConstraint requires at least one forbidden assignment";
        return NogoodConstraint.builder()
                .variables(forbidden.keySet())
                .forbidden(Map.copyOf(forbidden))
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (var entry : forbidden.entrySet()) {
            var value = assignment.getValue(entry.getKey());
            if (value.isEmpty() || !value.get().equals(entry.getValue())) return true;
        }
        return false;
    }

    /**
     * Generalised unit propagation over {@code OR(x1 != v1, ..., xk != vk)}. Classifies each
     * (variable, forbidden value) pair against its current domain using the generic
     * {@link Domain#contains} / {@link Domain#isSingleton}, so it works uniformly for discrete and
     * bounded domains alike:
     * <ul>
     *   <li><b>satisfied</b>: the domain no longer contains the forbidden value — this literal is
     *       guaranteed true, so the whole clause is permanently satisfied regardless of the rest;
     *       propagation stops immediately with no changes.</li>
     *   <li><b>falsified</b>: the domain is exactly {@code {value}} — this literal is currently
     *       false.</li>
     *   <li><b>undetermined</b>: the domain still contains the value alongside others.</li>
     * </ul>
     * All falsified (none satisfied, none undetermined) → infeasible. Exactly one undetermined
     * (every other literal falsified) → that one variable must avoid its forbidden value to keep
     * the clause satisfiable, so it is pruned from that variable's domain — but only when the
     * domain is a {@link DiscreteDomain}; removing a single point from a non-singleton bounded
     * (continuous) domain isn't a meaningful narrowing, so that case is left untouched (the
     * all-falsified infeasibility check above still applies to it regardless).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Variable<?> undeterminedVar = null;
        Object undeterminedValue = null;
        int undeterminedCount = 0;

        for (var entry : forbidden.entrySet()) {
            Variable<?> var = entry.getKey();
            Object value = entry.getValue();
            Domain<?> dom = domains.get(var);
            if (!dom.contains(value)) {
                return Optional.of(Map.of()); // this literal is guaranteed true: clause permanently satisfied
            }
            if (!dom.isSingleton()) {
                undeterminedCount++;
                undeterminedVar = var;
                undeterminedValue = value;
            }
        }

        if (undeterminedCount == 0) return Optional.empty(); // every literal falsified

        if (undeterminedCount == 1 && domains.get(undeterminedVar) instanceof DiscreteDomain<?> dom) {
            DiscreteDomain<Object> discreteDom = (DiscreteDomain<Object>) dom;
            Domain<Object> newDom = discreteDom.toBuilder().delete(undeterminedValue).build();
            return Optional.of(Map.of(undeterminedVar, newDom));
        }

        return Optional.of(Map.of());
    }

    /** All falsified means every variable is already pinned to exactly its forbidden value: the nogood itself. */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return Map.copyOf(forbidden);
    }

    @Override
    public String getRelation() {
        return forbidden.entrySet().stream()
                .map(e -> e.getKey() + "!=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(" OR ", "nogood(", ")"));
    }
}
