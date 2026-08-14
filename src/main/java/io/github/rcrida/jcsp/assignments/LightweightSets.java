package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import org.jspecify.annotations.NonNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Two allocation-cheap {@link Set} constructions that deliberately avoid {@code hashCode}/{@code
 * equals} calls on their elements, unlike {@link Set#copyOf}/{@code new HashSet<>(...)} which both
 * rehash every element to build/verify a hash table. Extracted for {@link
 * ConstraintSatisfactionProblem#mergedWithNogoods} and {@link NogoodStore#apply} specifically: both
 * were found via JFR profiling of a hard XCSP3 BinPacking instance to spend the large majority of
 * search time re-hashing {@link io.github.rcrida.jcsp.constraints.nary.NogoodConstraint}s (whose
 * {@code hashCode}/{@code equals} recursively walk a {@code Set<Variable<?>>}) on every node that
 * had just learned a nogood -- a nearly-every-node event once search is deep enough into a hard
 * region, since the merge/snapshot cache these two callers already maintain is keyed on reference
 * identity of the (constantly-changing) nogood set, not its content.
 */
public final class LightweightSets {

    private LightweightSets() {
    }

    /**
     * A {@link Set} view over two already-disjoint, already-deduplicated sets, united without
     * copying either one. Safe here specifically because {@code first} (a CSP's structural
     * constraints) and {@code second} (its learned {@link
     * io.github.rcrida.jcsp.constraints.nary.NogoodConstraint}s) can never contain an element in
     * common: every concrete {@link io.github.rcrida.jcsp.constraints.Constraint} subclass's
     * Lombok-generated {@code equals} rejects a different concrete subclass outright (via {@code
     * canEqual}), and structural constraints and nogoods are always disjoint concrete types.
     */
    public static <E> Set<E> unionView(@NonNull Set<? extends E> first, @NonNull Set<? extends E> second) {
        return new DisjointUnionSet<>(first, second);
    }

    /**
     * A {@link Set} snapshot of {@code alreadyUnique} (a collection already known to contain no
     * duplicates, e.g. backed by a real {@link Set}) that copies element references into a plain
     * array instead of {@link Set#copyOf}'s hash-table construction -- skipping the redundant
     * re-dedup {@code copyOf} would otherwise perform via each element's {@code hashCode}/{@code
     * equals}.
     */
    public static <E> Set<E> snapshot(@NonNull Collection<? extends E> alreadyUnique) {
        return new ArraySnapshotSet<>(alreadyUnique);
    }

    private static final class DisjointUnionSet<E> extends AbstractSet<E> {
        private final Set<? extends E> first;
        private final Set<? extends E> second;

        DisjointUnionSet(Set<? extends E> first, Set<? extends E> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int size() {
            return first.size() + second.size();
        }

        @Override
        public boolean isEmpty() {
            return first.isEmpty() && second.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return first.contains(o) || second.contains(o);
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<>() {
                private final Iterator<? extends E> firstIt = first.iterator();
                private final Iterator<? extends E> secondIt = second.iterator();

                @Override
                public boolean hasNext() {
                    return firstIt.hasNext() || secondIt.hasNext();
                }

                @Override
                public E next() {
                    if (firstIt.hasNext()) return firstIt.next();
                    if (secondIt.hasNext()) return secondIt.next();
                    throw new NoSuchElementException();
                }
            };
        }
    }

    private static final class ArraySnapshotSet<E> extends AbstractSet<E> {
        private final Object[] elements;

        ArraySnapshotSet(Collection<? extends E> source) {
            this.elements = source.toArray();
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < elements.length;
                }

                @SuppressWarnings("unchecked")
                @Override
                public E next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return (E) elements[index++];
                }
            };
        }
    }
}
