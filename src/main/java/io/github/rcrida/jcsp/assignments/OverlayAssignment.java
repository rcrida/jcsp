package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * An {@link Assignment} that is one more entry laid over another, so extending a search path by a
 * variable copies nothing: the cost of {@link Assignment#withValue} is one small object instead of
 * the whole map, and sibling branches share every layer below them. A lookup walks the layers, so a
 * chain is cut off at {@link #MAX_DEPTH} layers and {@link #over} flattens it into a {@link
 * MapAssignment} there, which makes the copy every {@link #MAX_DEPTH}-th extension rather than every
 * one.
 * <p>
 * No layer's variable is assigned anywhere below it, which is what lets {@link #size} and the
 * iteration be plain sums and concatenations. The constructor is private and {@link #over} is the
 * only way to a layer, and it sends re-assigning a variable through the builder instead, so the
 * invariant holds for every instance.
 */
final class OverlayAssignment implements Assignment {
    static final int MAX_DEPTH = 8;

    /** The assignment this is laid over, or {@code null} when that is {@link #base} itself. */
    private final OverlayAssignment below;
    /** The first assignment under the layers, never itself an {@link OverlayAssignment}. */
    private final Assignment base;
    private final Variable<?> variable;
    /** {@link #variable}'s hash, so a lookup that misses a layer costs an int compare, not an {@code equals}. */
    private final int hash;
    private final Object value;
    private final int depth;
    private final int size;
    private final Statistics statistics;
    private final SolverListener listener;
    private final Cancellation cancellation;

    private OverlayAssignment(Assignment under, Variable<?> variable, Object value) {
        if (under instanceof OverlayAssignment layer) {
            below = layer;
            base = layer.base;
            depth = layer.depth + 1;
        } else {
            below = null;
            base = under;
            depth = 1;
        }
        this.variable = variable;
        this.hash = variable.hashCode();
        this.value = value;
        this.size = under.size() + 1;
        this.statistics = under.getStatistics();
        this.listener = under.listener();
        this.cancellation = under.cancellation();
    }

    /**
     * {@code under} plus {@code variable = value}: a new layer when {@code variable} is not yet
     * assigned and the chain has room, otherwise a {@link MapAssignment} built through {@code
     * under}'s builder, which replaces an existing value and flattens a chain that is {@link
     * #MAX_DEPTH} layers deep.
     */
    static Assignment over(Assignment under, Variable<?> variable, Object value) {
        if (under.isAssigned(variable) || under instanceof OverlayAssignment layer && layer.depth >= MAX_DEPTH) {
            return under.toBuilder().value(variable, value).build();
        }
        return new OverlayAssignment(under, variable, value);
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public SolverListener listener() {
        return listener;
    }

    @Override
    public Cancellation cancellation() {
        return cancellation;
    }

    @Override
    public Object getValueOrNull(@NonNull Variable<?> variable) {
        int wanted = variable.hashCode();
        for (OverlayAssignment layer = this; layer != null; layer = layer.below) {
            if (layer.hash == wanted && layer.variable.equals(variable)) return layer.value;
        }
        return base.getValueOrNull(variable);
    }

    @Override
    public boolean isAssigned(@NonNull Variable<?> variable) {
        int wanted = variable.hashCode();
        for (OverlayAssignment layer = this; layer != null; layer = layer.below) {
            if (layer.hash == wanted && layer.variable.equals(variable)) return true;
        }
        return base.isAssigned(variable);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Set<Variable<?>> variables() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public boolean contains(Object o) {
                return o instanceof Variable<?> v && isAssigned(v);
            }

            @Override
            public Iterator<Variable<?>> iterator() {
                return layersThenBase(layer -> layer.variable, base.variables().iterator());
            }
        };
    }

    /** Each layer's variable and value, then the base's, with no entry object per entry as {@link #entries} makes. */
    @Override
    public void forEach(@NonNull BiConsumer<? super Variable<?>, ? super Object> action) {
        for (OverlayAssignment layer = this; layer != null; layer = layer.below) {
            action.accept(layer.variable, layer.value);
        }
        base.forEach(action);
    }

    @Override
    public Iterable<Map.Entry<Variable<?>, Object>> entries() {
        return () -> layersThenBase(layer -> Map.<Variable<?>, Object>entry(layer.variable, layer.value),
                base.entries().iterator());
    }

    private <T> Iterator<T> layersThenBase(Function<OverlayAssignment, T> ofLayer, Iterator<T> baseIterator) {
        return new Iterator<>() {
            private OverlayAssignment layer = OverlayAssignment.this;

            @Override
            public boolean hasNext() {
                return layer != null || baseIterator.hasNext();
            }

            @Override
            public T next() {
                if (layer == null) return baseIterator.next();
                T next = ofLayer.apply(layer);
                layer = layer.below;
                return next;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
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
