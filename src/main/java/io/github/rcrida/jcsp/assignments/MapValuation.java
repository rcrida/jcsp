package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.variables.VariableMap;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A {@link Valuation} and nothing else, backed by a {@link VariableMap} that never leaves this class:
 * what a table tuple or a nogood holds, with none of an {@link Assignment}'s solve state.
 */
record MapValuation(VariableMap<Object> values) implements Valuation {

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
    public void forEach(@NonNull BiConsumer<? super Variable<?>, ? super Object> action) {
        values.forEach(action);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MapValuation map) return values.equals(map.values);
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
