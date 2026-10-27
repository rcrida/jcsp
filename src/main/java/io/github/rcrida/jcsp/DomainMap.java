package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A problem's variables and their current domains: an immutable {@link Map} that iterates in
 * {@link Variable#getIndex()} order, which is the order their {@link Variable.Factory} issued them.
 * Each variable's domain sits at its own index in an array of domains, beside an array of the
 * variables themselves, so a lookup is an array read rather than a hash.
 * <p>
 * Narrowing a domain is the most frequent thing a search does, and each narrowing makes a new map.
 * Copying a {@link LinkedHashMap} of 28,000 entries allocates and rehashes every one of them, about
 * 190 microseconds and 1.4MB that the search's stack then keeps alive per level; cloning an array of
 * the same 28,000 references takes about 2.5 microseconds and 220KB. The array of variables is
 * shared by every map derived from this one, so only the array of domains is copied. Lookups and
 * iteration are faster than the {@link LinkedHashMap}'s.
 * <p>
 * Every variable must have been issued by a {@link Variable.Factory}, and the factories must lie on
 * one lineage: there is a {@link #leaf} factory, the most-derived of them, and every other is the
 * factory it was forked from or one of that one's own ancestors. A variable from a different branch
 * of the family, or from an unrelated factory, is therefore rejected whatever its index. No two
 * variables may share an index, which only an ancestor that went on issuing after it was forked can
 * still cause. A variable is found only if it is exactly one of the map's own, so one from another
 * factory whose index happens to be in range is absent. The arrays are as long as the highest index
 * plus one, so a problem over a sparse subset of a larger index space has gaps.
 * <p>
 * Never modified after construction, and the arrays never leave this class.
 */
final class DomainMap extends AbstractMap<Variable<?>, Domain<?>> {

    /** Indexed by {@link Variable#getIndex()}, null where this map has no such variable. Shared, never modified. */
    private final Variable<?>[] variables;
    /** Indexed like {@link #variables}. */
    private final Domain<?>[] domains;
    private final int size;
    /** The most-derived factory that issued any of the variables, or null for no variables. */
    private final Variable.@Nullable Factory leaf;

    private DomainMap(Variable<?>[] variables, Domain<?>[] domains, int size, Variable.@Nullable Factory leaf) {
        this.variables = variables;
        this.domains = domains;
        this.size = size;
        this.leaf = leaf;
    }

    /**
     * {@code source}'s entries; {@code source} itself if it is already one of these.
     *
     * @throws IllegalArgumentException if a variable was not issued by a factory, if two were issued by
     *         factories that are not on one lineage, or if two share an index
     */
    static DomainMap of(@NonNull Map<Variable<?>, Domain<?>> source) {
        if (source instanceof DomainMap domainMap) {
            return domainMap;
        }
        int highest = -1;
        Variable<?> leafVariable = null;
        Variable.Factory leaf = null;
        Set<Variable.Factory> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Variable<?> variable : source.keySet()) {
            Variable.Factory factory = variable.getFactory();
            if (factory == null) {
                throw new IllegalArgumentException("Variable was not issued by a Variable.Factory: " + variable);
            }
            if (checked.add(factory)) {
                if (leaf == null || factory.isSameOrForkOf(leaf)) {
                    leaf = factory;
                    leafVariable = variable;
                } else if (!leaf.isSameOrForkOf(factory)) {
                    throw new IllegalArgumentException("Variables " + leafVariable + " and " + variable
                            + " were issued by factories that do not share one lineage");
                }
            }
            highest = Math.max(highest, variable.getIndex());
        }
        Variable<?>[] variables = new Variable<?>[highest + 1];
        Domain<?>[] domains = new Domain<?>[highest + 1];
        for (Map.Entry<Variable<?>, Domain<?>> entry : source.entrySet()) {
            int index = entry.getKey().getIndex();
            if (variables[index] != null) {
                throw new IllegalArgumentException("Variables " + variables[index] + " and " + entry.getKey()
                        + " share index " + index + ": issued by different factories");
            }
            variables[index] = entry.getKey();
            domains[index] = entry.getValue();
        }
        return new DomainMap(variables, domains, source.size(), leaf);
    }

    /**
     * The factory a problem over these variables should issue further variables from: {@code own} if it
     * is the {@link #leaf} or a fork of it, and otherwise the leaf, so a problem built from variables
     * made elsewhere continues their numbering rather than a fresh factory's.
     */
    Variable.Factory continuing(Variable.@NonNull Factory own) {
        return leaf == null || own.isSameOrForkOf(leaf) ? own : leaf;
    }

    /** The index of {@code key} if it is exactly one of this map's variables, otherwise -1. */
    private int indexOf(Object key) {
        if (key instanceof Variable<?> variable) {
            int index = variable.getIndex();
            if (index >= 0 && index < variables.length && variables[index] == key) {
                return index;
            }
        }
        return -1;
    }

    /** The first index at or after {@code from} that holds a variable, or the array's length if none does. */
    private int nextPresent(int from) {
        int index = from;
        while (index < variables.length && variables[index] == null) {
            index++;
        }
        return index;
    }

    /**
     * This map with {@code variable}'s domain replaced by {@code domain}. A variable not already here
     * is added, taking its place by index.
     */
    DomainMap with(@NonNull Variable<?> variable, @NonNull Domain<?> domain) {
        int index = indexOf(variable);
        if (index < 0) {
            return withNewVariables(Map.of(variable, domain));
        }
        Domain<?>[] replaced = domains.clone();
        replaced[index] = domain;
        return new DomainMap(variables, replaced, size, leaf);
    }

    /** As {@link #with}, for every entry of {@code updates} in one pass. */
    DomainMap withAll(@NonNull Map<Variable<?>, Domain<?>> updates) {
        Domain<?>[] replaced = domains.clone();
        for (Map.Entry<Variable<?>, Domain<?>> update : updates.entrySet()) {
            int index = indexOf(update.getKey());
            if (index < 0) {
                return withNewVariables(updates);
            }
            replaced[index] = update.getValue();
        }
        return new DomainMap(variables, replaced, size, leaf);
    }

    /** The rare case of an update naming a variable this map does not have: a map built afresh. */
    private DomainMap withNewVariables(Map<Variable<?>, Domain<?>> updates) {
        Map<Variable<?>, Domain<?>> merged = new LinkedHashMap<>(this);
        merged.putAll(updates);
        return of(merged);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public Domain<?> get(Object key) {
        int index = indexOf(key);
        return index < 0 ? null : domains[index];
    }

    @Override
    public Set<Variable<?>> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Variable<?>> iterator() {
                return new Cursor<>() {
                    @Override
                    Variable<?> at(int index) {
                        return variables[index];
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }

            @Override
            public boolean contains(Object variable) {
                return containsKey(variable);
            }
        };
    }

    @Override
    public Collection<Domain<?>> values() {
        return new AbstractCollection<>() {
            @Override
            public Iterator<Domain<?>> iterator() {
                return new Cursor<>() {
                    @Override
                    Domain<?> at(int index) {
                        return domains[index];
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    @Override
    public Set<Entry<Variable<?>, Domain<?>>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<Variable<?>, Domain<?>>> iterator() {
                return new Cursor<>() {
                    @Override
                    Entry<Variable<?>, Domain<?>> at(int index) {
                        return new SimpleImmutableEntry<>(variables[index], domains[index]);
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    @Override
    public void forEach(BiConsumer<? super Variable<?>, ? super Domain<?>> action) {
        for (int index = nextPresent(0); index < variables.length; index = nextPresent(index + 1)) {
            action.accept(variables[index], domains[index]);
        }
    }

    /** Equal to any map with the same entries; two of these over the same variables compare their arrays. */
    @Override
    public boolean equals(Object other) {
        if (other instanceof DomainMap that && that.variables == variables) {
            return Arrays.equals(domains, that.domains);
        }
        return super.equals(other);
    }

    /** The sum of the entries' hash codes, as {@link Map#hashCode} defines it, without building the entries. */
    @Override
    public int hashCode() {
        int hash = 0;
        for (int index = nextPresent(0); index < variables.length; index = nextPresent(index + 1)) {
            hash += variables[index].hashCode() ^ domains[index].hashCode();
        }
        return hash;
    }

    /** Walks the arrays front to back over the indices that hold a variable, producing {@link #at} of each. Read-only. */
    private abstract class Cursor<T> implements Iterator<T> {
        private int next = nextPresent(0);

        abstract T at(int index);

        @Override
        public boolean hasNext() {
            return next < variables.length;
        }

        @Override
        public T next() {
            if (next >= variables.length) {
                throw new NoSuchElementException();
            }
            T result = at(next);
            next = nextPresent(next + 1);
            return result;
        }
    }
}
