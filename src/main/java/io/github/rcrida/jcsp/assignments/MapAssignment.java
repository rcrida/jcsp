package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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

    /**
     * Straight to {@link Map#forEach}, which hands over each key and value with no entry object: {@link
     * #entries} allocates one wrapper per entry to keep them read-only. Copying through it, as {@link
     * Assignment#toBuilder} does, is what used to cost an allocation per entry here.
     */
    @Override
    public void forEach(@NonNull BiConsumer<? super Variable<?>, ? super Object> action) {
        values.forEach(action);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MapAssignment map) return values.equals(map.values);
        return Valuations.equal(this, o);
    }

    @Override
    public int hashCode() {
        return Valuations.hash(this);
    }

    @Override
    public String toString() {
        return Valuations.string(this);
    }
}
