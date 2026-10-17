package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A problem's variables and their current domains: an immutable {@link Map} that iterates in the
 * order the variables were declared, held as an array of domains over a {@link Positions} naming
 * where each variable sits.
 * <p>
 * Narrowing a domain is the most frequent thing a search does, and each narrowing makes a new map.
 * Copying a {@link LinkedHashMap} of 28,000 entries allocates and rehashes every one of them, about
 * 190 microseconds and 1.4MB that the search's stack then keeps alive per level; cloning an array of
 * the same 28,000 references takes about 2.5 microseconds and 220KB. The {@link Positions} are
 * shared by every map derived from this one, so only the array is copied. Lookups and iteration are
 * faster than the {@link LinkedHashMap}'s.
 * <p>
 * Never modified after construction, and the array never leaves this class.
 */
final class DomainMap extends AbstractMap<Variable<?>, Domain<?>> {

    /** The variables in declaration order, and the index of each in that order. Never modified. */
    private record Positions(Variable<?>[] variables, Map<Variable<?>, Integer> indexOf) {
    }

    private final Positions positions;
    private final Domain<?>[] domains;

    private DomainMap(Positions positions, Domain<?>[] domains) {
        this.positions = positions;
        this.domains = domains;
    }

    /** {@code source}'s entries, in its iteration order; {@code source} itself if it is already one of these. */
    static DomainMap of(@NonNull Map<Variable<?>, Domain<?>> source) {
        if (source instanceof DomainMap domainMap) {
            return domainMap;
        }
        Variable<?>[] variables = new Variable<?>[source.size()];
        Domain<?>[] domains = new Domain<?>[source.size()];
        Map<Variable<?>, Integer> indexOf = HashMap.newHashMap(source.size());
        int index = 0;
        for (Map.Entry<Variable<?>, Domain<?>> entry : source.entrySet()) {
            variables[index] = entry.getKey();
            domains[index] = entry.getValue();
            indexOf.put(entry.getKey(), index);
            index++;
        }
        return new DomainMap(new Positions(variables, indexOf), domains);
    }

    /**
     * This map with {@code variable}'s domain replaced by {@code domain}. A variable not already here
     * is added after the others, as {@link LinkedHashMap#put} would.
     */
    DomainMap with(@NonNull Variable<?> variable, @NonNull Domain<?> domain) {
        Integer index = positions.indexOf().get(variable);
        if (index == null) {
            return withNewVariables(Map.of(variable, domain));
        }
        Domain<?>[] replaced = domains.clone();
        replaced[index] = domain;
        return new DomainMap(positions, replaced);
    }

    /** As {@link #with}, for every entry of {@code updates} in one pass. */
    DomainMap withAll(@NonNull Map<Variable<?>, Domain<?>> updates) {
        Domain<?>[] replaced = domains.clone();
        for (Map.Entry<Variable<?>, Domain<?>> update : updates.entrySet()) {
            Integer index = positions.indexOf().get(update.getKey());
            if (index == null) {
                return withNewVariables(updates);
            }
            replaced[index] = update.getValue();
        }
        return new DomainMap(positions, replaced);
    }

    /** The rare case of an update naming a variable this map does not have: a map built afresh. */
    private DomainMap withNewVariables(Map<Variable<?>, Domain<?>> updates) {
        Map<Variable<?>, Domain<?>> merged = new LinkedHashMap<>(this);
        merged.putAll(updates);
        return of(merged);
    }

    @Override
    public int size() {
        return domains.length;
    }

    @Override
    public boolean containsKey(Object key) {
        return positions.indexOf().containsKey(key);
    }

    @Override
    public Domain<?> get(Object key) {
        Integer index = positions.indexOf().get(key);
        return index == null ? null : domains[index];
    }

    @Override
    public Set<Variable<?>> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Variable<?>> iterator() {
                return new Cursor<>() {
                    @Override
                    Variable<?> at(int index) {
                        return positions.variables()[index];
                    }
                };
            }

            @Override
            public int size() {
                return domains.length;
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
                return domains.length;
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
                        return new SimpleImmutableEntry<>(positions.variables()[index], domains[index]);
                    }
                };
            }

            @Override
            public int size() {
                return domains.length;
            }
        };
    }

    @Override
    public void forEach(BiConsumer<? super Variable<?>, ? super Domain<?>> action) {
        for (int index = 0; index < domains.length; index++) {
            action.accept(positions.variables()[index], domains[index]);
        }
    }

    /** Equal to any map with the same entries; two of these over the same positions compare their arrays. */
    @Override
    public boolean equals(Object other) {
        if (other instanceof DomainMap that && that.positions == positions) {
            return Arrays.equals(domains, that.domains);
        }
        return super.equals(other);
    }

    /** The sum of the entries' hash codes, as {@link Map#hashCode} defines it, without building the entries. */
    @Override
    public int hashCode() {
        int hash = 0;
        for (int index = 0; index < domains.length; index++) {
            hash += positions.variables()[index].hashCode() ^ domains[index].hashCode();
        }
        return hash;
    }

    /** Walks the array front to back, producing {@link #at} of each index. Read-only. */
    private abstract class Cursor<T> implements Iterator<T> {
        private int next;

        abstract T at(int index);

        @Override
        public boolean hasNext() {
            return next < domains.length;
        }

        @Override
        public T next() {
            if (next >= domains.length) {
                throw new NoSuchElementException();
            }
            return at(next++);
        }
    }
}
