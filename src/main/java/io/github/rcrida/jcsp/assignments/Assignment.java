package io.github.rcrida.jcsp.assignments;

import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Builder(toBuilder = true)
public record Assignment(@Singular Map<Variable<?>, Object> values, Statistics statistics, SolverListener listener,
                          Cancellation cancellation) {

    public Assignment {
        if (statistics == null) statistics = new Statistics();
        if (listener == null) listener = SolverListener.NONE;
        if (cancellation == null) cancellation = Cancellation.NEVER;
    }

    public static Assignment empty() {
        return Assignment.builder().build();
    }

    public Map<Variable<?>, Object> getValues() {
        return values;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public static Assignment of(Map<? extends Variable<?>, ?> values) {
        return Assignment.builder().values(values).build();
    }

    /**
     * Constructs an {@link Assignment} directly from an already-built values map, statistics,
     * listener, and cancellation token, skipping the Lombok builder's ArrayList-based accumulation
     * entirely — for hot per-candidate construction paths (e.g.
     * {@link io.github.rcrida.jcsp.solver.LargeNeighborhoodSolver}'s per-combo enumeration) where
     * the caller already owns a freshly-built map it will never mutate again. {@link #values} is
     * wrapped in an unmodifiable view (an O(1) wrap, not a copy) so the returned {@link Assignment}
     * still gets the same immutability guarantee {@link #getValues} callers rely on elsewhere, even
     * though this path never goes through the builder's own defensive copy. {@code statistics},
     * {@code listener}, and {@code cancellation} are required, not defaulted, so a caller deriving
     * from an existing {@link Assignment} must explicitly carry its {@link #statistics}/
     * {@link #listener}/{@link #cancellation} forward (e.g. {@code
     * base.getStatistics()}/{@code base.listener()}/{@code base.cancellation()}) rather than
     * silently starting a fresh, disconnected one for the derived {@link Assignment}.
     */
    public static Assignment ofTrusted(@NonNull Map<Variable<?>, Object> values, @NonNull Statistics statistics,
                                        @NonNull SolverListener listener, @NonNull Cancellation cancellation) {
        return new Assignment(Collections.unmodifiableMap(values), statistics, listener, cancellation);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(@NonNull Variable<T> variable) {
        return Optional.ofNullable((T) values.get(variable));
    }

    /**
     * Iterates {@code variables} and looks each one up in {@link #values} directly, rather than
     * scanning every entry in {@link #values} and filtering by membership in {@code variables} —
     * {@code variables} is typically a single constraint's own handful of variables, far smaller
     * than the full assignment (profiling
     * {@link io.github.rcrida.jcsp.constraints.nary.UniformNaryConstraint#isSatisfiedBy}, this
     * method's dominant caller, found the original O(|values|)-per-call scan to be the runaway
     * cost in {@link io.github.rcrida.jcsp.solver.LargeNeighborhoodSolver}'s per-combo,
     * per-constraint violation checking).
     * {@link #values} never intentionally maps a variable to {@code null} (same assumption {@link
     * #getValue} already relies on via {@link Optional#ofNullable}), so a {@code null} lookup
     * result is only ever "not yet assigned", matching the original's membership-filter semantics.
     * Deliberately backed by a plain {@link java.util.HashMap}, not a {@link
     * java.util.LinkedHashMap}: this result is read-only and discarded immediately by its dominant
     * caller (only {@link Map#values()} is ever read, never the entries themselves), so there's no
     * order to preserve, and re-profiling after an earlier attempt to use {@link
     * java.util.LinkedHashMap} here (for consistency with {@link #values}'s own iteration order)
     * found it measurably regressed this exact hot path (~20% fewer LNS steps per second on
     * {@code ParkrunSchedulingTest}, a test-scope class this main-sources Javadoc can't link to)
     * for a guarantee nothing actually observes.
     */
    public Map<Variable<?>, Object> partialValues(@NonNull Set<? extends Variable<?>> variables) {
        Map<Variable<?>, Object> partial = new HashMap<>();
        for (Variable<?> variable : variables) {
            Object value = values.get(variable);
            if (value != null) partial.put(variable, value);
        }
        return Collections.unmodifiableMap(partial);
    }

    /**
     * Same lookup as {@link #partialValues} but wrapped in a full {@link Assignment} — needed by
     * callers that rely on {@link Assignment} identity/equality (e.g.
     * {@link io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint} comparing against a
     * table of tuples), unlike
     * {@link io.github.rcrida.jcsp.constraints.nary.UniformNaryConstraint#isSatisfiedBy}, which
     * only ever needs the raw value collection and calls {@link #partialValues} directly to skip
     * this wrapping.
     */
    public Assignment extractPartialAssignment(@NonNull Set<? extends Variable<?>> variables) {
        return Assignment.of(partialValues(variables));
    }

    public Assignment withValue(@NonNull Variable<?> variable, @NonNull Object value) {
        val next = toBuilder().value(variable, value).build();
        next.statistics.incrementNodesExplored();
        next.listener.onNodeExplored(variable, value, next);
        return next;
    }

    public Assignment merge(@NonNull Assignment another) {
        val builder = toBuilder();
        builder.values(another.getValues());
        val merged = builder.build();
        merged.statistics.add(another.statistics);
        return merged;
    }

    public boolean isConsistent(ConstraintSatisfactionProblem csp) {
        validateAssignment(csp);
        return isConsistentAmong(csp.getConstraints());
    }

    /**
     * Sibling of {@link #isConsistent}, checked against a caller-supplied {@code candidateConstraints}
     * rather than {@code csp.getConstraints()} -- for a caller that has already narrowed the full
     * constraint set down to the ones that could possibly be affected (e.g. {@link
     * io.github.rcrida.jcsp.solver.tree.TreeSolver} pre-filtering to just the constraints touching a
     * single newly-assigned variable), so repeated calls don't re-scan the whole set every time.
     * Skips {@link #validateAssignment}, since callers using this narrower form are typically
     * re-checking the same {@link ConstraintSatisfactionProblem} they already validated against once.
     * <p>
     * Runs on every node of every terminal solver, so the per-constraint "does it reference an
     * assigned variable" test uses {@link Collections#disjoint} against a plain loop over {@code
     * candidateConstraints} rather than a nested {@link java.util.stream.Stream} pipeline (one
     * {@code constraint.getVariables().stream().anyMatch(values::containsKey)} built per candidate)
     * -- JFR profiling found the same stream-construction cost here that {@code
     * NogoodFixpointConsistency#relevant} had, fixed the same way there first.
     */
    public boolean isConsistentAmong(@NonNull Collection<? extends Constraint> candidateConstraints) {
        for (Constraint constraint : candidateConstraints) {
            if (Collections.disjoint(values.keySet(), constraint.getVariables())) continue;
            statistics.incrementConstraintChecks();
            if (!constraint.isSatisfiedBy(this)) {
                if (constraint instanceof NogoodConstraint) statistics.incrementNogoodRejections();
                return false;
            }
        }
        return true;
    }

    public boolean isComplete(ConstraintSatisfactionProblem csp) {
        validateAssignment(csp);
        return csp.getVariableDomains().keySet().stream().allMatch(values::containsKey);
    }

    public boolean isSolution(ConstraintSatisfactionProblem csp) {
        return isComplete(csp) && isConsistent(csp);
    }

    private void validateAssignment(ConstraintSatisfactionProblem csp) {
        for (Map.Entry<Variable<?>, Object> entry : values.entrySet()) {
            assert csp.isAllowedValue(entry.getKey(), entry.getValue()) : String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Assignment a)) return false;
        return Objects.equals(values, a.values);
    }

    /**
     * Deliberately not {@code Objects.hash(values)} (equivalently, {@link Map#hashCode()}'s own
     * XOR-sum contract): that collapses badly when many {@code Assignment}s share the same
     * variable set and small-integer domain values -- {@link Integer#hashCode()} is the value
     * itself, so XORing it directly against a per-key hash barely perturbs the low bits, and
     * summing across entries whose values only range over a handful of small integers leaves the
     * whole sum confined to a narrow band. Confirmed empirically parsing a real XCSP3 instance
     * ({@code Steiner3-08.xml.lzma}): 80,640 distinct 6-tuples over domain {@code 1..8} collapsed
     * to just 57 distinct hashcodes, making {@code HashSet<Assignment>} construction (during
     * parsing) and {@link io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint}'s own
     * {@code Set#contains} lookups (during search, over any extensional/tuple constraint with a
     * small-integer domain) degrade toward a linear scan instead of the expected O(1) average.
     * Multiplying each value's hashcode by a large odd constant first (a standard integer-avalanche
     * technique) spreads it across the full 32-bit range before combining; the per-entry
     * combination and the sum across entries are both still order-independent, so two equal maps
     * (order doesn't affect {@link Map#equals}) still produce equal hashcodes.
     */
    @Override
    public int hashCode() {
        int hash = 0;
        for (Map.Entry<Variable<?>, Object> entry : values.entrySet()) {
            hash += entry.getKey().hashCode() ^ (entry.getValue().hashCode() * 0x9E3779B1);
        }
        return hash;
    }

    @Override
    public String toString() {
        return String.valueOf(values);
    }
}
