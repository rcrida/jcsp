package io.github.rcrida.jcsp.variables;

import org.jspecify.annotations.NonNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A growable {@link Map} keyed by {@link Variable}, found through {@link Variable#getIndex()} rather
 * than by hashing, for the small maps a propagation builds up and reads back on every domain lookup.
 * <p>
 * The entries sit in two arrays in insertion order, so iteration is as deterministic as the order they
 * were put in, which a hash map's is only if its keys' hash codes are. A table of slots, addressed by
 * the variable's index and probed linearly, says which entry a key is: a lookup is the index masked,
 * one read of the table and one identity comparison, with no hash code, no {@code equals} and no node
 * to chase. Indices that are consecutive, as a problem's mostly are, never share a slot.
 * <p>
 * A key is found only if it is exactly one of the map's own, like {@link Variable} equality itself.
 * Entries can be added or replaced but not removed: these maps only ever accumulate narrowings.
 *
 * @param <V> the value type
 */
public final class VariableMap<V> extends AbstractMap<Variable<?>, V> {
    private static final int INITIAL_ENTRIES = 4;

    /** Entry number plus one for each slot, or 0 for an empty one. A power of two long, twice the entry capacity. */
    private int[] table;
    private Variable<?>[] keys;
    private Object[] values;
    private int size;

    public VariableMap() {
        this(INITIAL_ENTRIES);
    }

    /** A map holding {@code entries}, in their iteration order. */
    public VariableMap(@NonNull Map<? extends Variable<?>, ? extends V> entries) {
        this(Math.max(INITIAL_ENTRIES, Integer.highestOneBit(Math.max(1, entries.size() * 2 - 1))));
        putAll(entries);
    }

    private VariableMap(int capacity) {
        keys = new Variable<?>[capacity];
        values = new Object[capacity];
        table = new int[capacity * 2];
    }

    /** The entry number of {@code key} if it is exactly one of this map's keys, otherwise -1. */
    private int find(Object key) {
        if (key instanceof Variable<?> variable) {
            int mask = table.length - 1;
            for (int slot = variable.getIndex() & mask; ; slot = (slot + 1) & mask) {
                int entry = table[slot];
                if (entry == 0) {
                    return -1;
                }
                if (keys[entry - 1] == variable) {
                    return entry - 1;
                }
            }
        }
        return -1;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object key) {
        return find(key) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        int entry = find(key);
        return entry < 0 ? null : (V) values[entry];
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(@NonNull Variable<?> key, V value) {
        int found = find(key);
        if (found >= 0) {
            V previous = (V) values[found];
            values[found] = value;
            return previous;
        }
        if (size == keys.length) {
            grow();
        }
        keys[size] = key;
        values[size] = value;
        place(size);
        size++;
        return null;
    }

    /** Records entry number {@code entry} in the first free slot of its probe sequence. */
    private void place(int entry) {
        int mask = table.length - 1;
        int slot = keys[entry].getIndex() & mask;
        while (table[slot] != 0) {
            slot = (slot + 1) & mask;
        }
        table[slot] = entry + 1;
    }

    private void grow() {
        keys = Arrays.copyOf(keys, keys.length * 2);
        values = Arrays.copyOf(values, values.length * 2);
        table = new int[keys.length * 2];
        for (int entry = 0; entry < size; entry++) {
            place(entry);
        }
    }

    @Override
    public void putAll(@NonNull Map<? extends Variable<?>, ? extends V> entries) {
        entries.forEach(this::put);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super Variable<?>, ? super V> action) {
        for (int entry = 0; entry < size; entry++) {
            action.accept(keys[entry], (V) values[entry]);
        }
    }

    @Override
    public Set<Entry<Variable<?>, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<Variable<?>, V>> iterator() {
                return new Iterator<>() {
                    private int next;

                    @Override
                    public boolean hasNext() {
                        return next < size;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public Entry<Variable<?>, V> next() {
                        if (next >= size) {
                            throw new NoSuchElementException();
                        }
                        Entry<Variable<?>, V> entry = new SimpleImmutableEntry<>(keys[next], (V) values[next]);
                        next++;
                        return entry;
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }
        };
    }
}
