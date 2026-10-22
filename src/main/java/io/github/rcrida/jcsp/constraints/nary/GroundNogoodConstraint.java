package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Valuation;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A {@link NogoodConstraint} forbidding one specific ground value per variable: the clause
 * {@code OR(x1 != v1, x2 != v2, ..., xk != vk)} over {@link #forbidden} — violated only when every
 * one of its variables is assigned exactly its forbidden value.
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
public class GroundNogoodConstraint extends NaryConstraint implements NogoodConstraint {
    @NonNull private final Valuation forbidden;

    /**
     * Where this nogood reports that it did work, or {@code null} when nobody is counting. Not part
     * of equality: two nogoods forbidding the same values are the same nogood whoever counts them.
     */
    @EqualsAndHashCode.Exclude
    private final Hits hits;

    /**
     * A tally of how often the nogoods that share it pruned a domain or proved a node infeasible,
     * which is the evidence that recording them was worth it. One per search that wants the figure.
     */
    public static final class Hits {
        private final AtomicLong count = new AtomicLong();

        private void record() {
            count.incrementAndGet();
        }

        public long count() {
            return count.get();
        }
    }

    /**
     * The nogood forbidding exactly {@code forbidden}'s entries. Keeps {@code forbidden} itself
     * rather than a copy: a {@link Valuation} is immutable, and passing an {@link Assignment} keeps
     * it reachable only as the values-only type, so its statistics, listener and cancellation are
     * never read.
     */
    public static GroundNogoodConstraint of(@NonNull Valuation forbidden) {
        return of(forbidden, null);
    }

    /** As {@link #of(Valuation)}, reporting each pruning or failure it causes to {@code hits}. */
    public static GroundNogoodConstraint of(@NonNull Valuation forbidden, Hits hits) {
        // An empty forbidden valuation is the empty clause: propagate() would report infeasible for
        // every state (undeterminedCount == 0), pruning the whole search. Callers must not record one.
        assert forbidden.size() > 0 : "GroundNogoodConstraint requires at least one forbidden assignment";
        return GroundNogoodConstraint.builder()
                .variables(forbidden.variables())
                .forbidden(forbidden)
                .hits(hits)
                .build();
    }

    public static GroundNogoodConstraint of(@NonNull Map<Variable<?>, Object> forbidden) {
        return of(Assignment.of(forbidden));
    }

    /**
     * Converts a raw ground-value reason map, as built by {@link io.github.rcrida.jcsp.consistency.Propagatable#addIfSingleton}
     * or {@link io.github.rcrida.jcsp.consistency.Propagatable#allSingletonReason}, into the
     * {@code explainInfeasible}-ready shape: {@link Optional#empty()} if {@code reason} is empty
     * (no explanation available), otherwise {@link #of} wrapped in {@link Optional#of}. The
     * mechanical wrapper every {@code explainInfeasible} override that still builds a plain ground
     * map ends its method with.
     */
    public static Optional<NogoodConstraint> fromReason(@NonNull Map<Variable<?>, Object> reason) {
        return reason.isEmpty() ? Optional.empty() : Optional.of(of(reason));
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (var entry : forbidden.entries()) {
            Object value = assignment.getValueOrNull(entry.getKey());
            if (value == null || !value.equals(entry.getValue())) return true;
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

        for (var entry : forbidden.entries()) {
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

        if (undeterminedCount == 0) { // every literal falsified
            if (hits != null) hits.record();
            return Optional.empty();
        }

        if (undeterminedCount == 1 && domains.get(undeterminedVar) instanceof DiscreteDomain<?> dom) {
            if (hits != null) hits.record();
            DiscreteDomain<Object> discreteDom = (DiscreteDomain<Object>) dom;
            Domain<Object> newDom = discreteDom.toBuilder().delete(undeterminedValue).build();
            return Optional.of(Map.of(undeterminedVar, newDom));
        }

        return Optional.of(Map.of());
    }

    /**
     * All falsified means every variable is already pinned to exactly its forbidden value —
     * the clause itself is already the (unconditionally sound) explanation, no further reasoning
     * needed.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return Optional.of(this);
    }

    @Override
    public String getRelation() {
        return forbidden.entryStream()
                .map(e -> e.getKey() + "!=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(" OR ", "nogood(", ")"));
    }
}
