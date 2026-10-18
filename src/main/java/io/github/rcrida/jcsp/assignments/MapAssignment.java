package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An {@link Assignment} backed by one unmodifiable {@link Map}, whose entry order is unspecified.
 * {@link #values} never leaves this class; callers read through {@link Assignment}'s accessors.
 */
record MapAssignment(Map<Variable<?>, Object> values, Statistics statistics, SolverListener listener,
                     Cancellation cancellation) implements Assignment {

    MapAssignment {
        if (statistics == null) statistics = new Statistics();
        if (listener == null) listener = SolverListener.NONE;
        if (cancellation == null) cancellation = Cancellation.NEVER;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public Object getValueOrNull(@NonNull Variable<?> variable) {
        return values.get(variable);
    }

    @Override
    public boolean isAssigned(@NonNull Variable<?> variable) {
        return values.containsKey(variable);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Set<Variable<?>> variables() {
        return values.keySet();
    }

    @Override
    public Iterable<Map.Entry<Variable<?>, Object>> entries() {
        return values.entrySet();
    }

    @Override
    public Assignment withValue(@NonNull Variable<?> variable, @NonNull Object value) {
        val next = toBuilder().value(variable, value).build();
        next.getStatistics().incrementNodesExplored();
        next.listener().onNodeExplored(variable, value, next);
        return next;
    }

    @Override
    public Assignment withValues(@NonNull Map<Variable<?>, Object> additions) {
        Map<Variable<?>, Object> merged = new HashMap<>(values);
        merged.putAll(additions);
        val next = Assignment.ofTrusted(merged, statistics, listener, cancellation);
        additions.forEach((variable, value) -> {
            statistics.incrementNodesExplored();
            listener.onNodeExplored(variable, value, next);
        });
        return next;
    }

    @Override
    public Assignment merge(@NonNull Assignment another) {
        val builder = toBuilder();
        another.forEach(builder::value);
        val merged = builder.build();
        merged.getStatistics().add(another.getStatistics());
        return merged;
    }

    @Override
    public Assignment.AssignmentBuilder toBuilder() {
        return Assignment.builder().values(values).statistics(statistics).listener(listener).cancellation(cancellation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof MapAssignment map) return values.equals(map.values);
        if (!(o instanceof Valuation other) || other.size() != values.size()) return false;
        for (Map.Entry<Variable<?>, Object> entry : values.entrySet()) {
            if (!entry.getValue().equals(other.getValueOrNull(entry.getKey()))) return false;
        }
        return true;
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
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Variable::getName)))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
