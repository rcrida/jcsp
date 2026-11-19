package io.github.rcrida.jcsp.variables;

import org.jspecify.annotations.NonNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A growable {@link Map} keyed by {@link Variable}, found through {@link Variable#getIndex()} rather
 * than by hashing, for the small maps a propagation builds up and reads back on every domain lookup,
 * and for the values of an {@link io.github.rcrida.jcsp.assignments.Assignment}.
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
        this(capacityFor(entries.size()));
        putAll(entries);
    }

    private VariableMap(int capacity) {
        keys = new Variable<?>[capacity];
        values = new Object[capacity];
        table = new int[capacity * 2];
    }

    private VariableMap(Variable<?>[] keys, Object[] values, int[] table, int size) {
        this.keys = keys;
        this.values = values;
        this.table = table;
        this.size = size;
    }

    /** An empty map that holds {@code expectedEntries} without growing. */
    public static <V> VariableMap<V> withExpectedSize(int expectedEntries) {
        return new VariableMap<>(capacityFor(expectedEntries));
    }

    /** The smallest power of two, and at least {@link #INITIAL_ENTRIES}, that holds {@code entries}. */
    private static int capacityFor(int entries) {
        return Math.max(INITIAL_ENTRIES, Integer.highestOneBit(Math.max(1, entries * 2 - 1)));
    }

    /**
     * A copy of this map, in the same order, with room for {@code extraEntries} more without growing.
     * Where this map already has that room the copy is three array copies, with no entry placed again.
     */
    public VariableMap<V> copy(int extraEntries) {
        int capacity = Math.max(keys.length, capacityFor(size + extraEntries));
        if (capacity == keys.length) {
            return new VariableMap<>(keys.clone(), values.clone(), table.clone(), size);
        }
        VariableMap<V> copy = new VariableMap<>(Arrays.copyOf(keys, capacity), Arrays.copyOf(values, capacity),
                new int[capacity * 2], size);
        copy.placeAll();
        return copy;
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
        placeAll();
    }

    /** Records every entry in an empty {@link #table}. */
    private void placeAll() {
        for (int entry = 0; entry < size; entry++) {
            place(entry);
        }
    }

    /**
     * Whether {@code other} is a {@link Map} with the same entries, as {@link Map#equals} defines it,
     * read straight from the arrays rather than through {@link #entrySet}, which makes an entry object
     * for each one.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Map<?, ?> map) || map.size() != size) return false;
        for (int entry = 0; entry < size; entry++) {
            Object value = values[entry];
            Object theirs = map.get(keys[entry]);
            if (value == null ? theirs != null || !map.containsKey(keys[entry]) : !value.equals(theirs)) {
                return false;
            }
        }
        return true;
    }

    /** {@link Map#hashCode}'s sum, read straight from the arrays as {@link #equals} is. */
    @Override
    public int hashCode() {
        int hash = 0;
        for (int entry = 0; entry < size; entry++) {
            hash += keys[entry].hashCode() ^ Objects.hashCode(values[entry]);
        }
        return hash;
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
