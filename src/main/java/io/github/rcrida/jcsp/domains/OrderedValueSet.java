package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * The immutable, insertion-ordered {@link Set} every set-backed {@link DiscreteSetDomain} holds its
 * values in: an array for traversal, plus the membership set traversal never touches.
 * <p>
 * It replaces {@code Collections.unmodifiableSet(LinkedHashSet)}, which charged a domain's values
 * twice on every read. The wrapper made {@code hasNext}/{@code next} two virtual calls instead of
 * one, at a call site megamorphic enough that neither inlines -- 11.7% of a
 * {@code Taillard-js-015-15-0} solve and 13.8% of {@code Taillard-os-04-04-0} sat in those two
 * methods alone -- and underneath it {@link java.util.LinkedHashMap}'s iterator chased a pointer
 * per element, for a further 10.1% and 8.6%. Arc consistency reads {@code D_i × D_j} value pairs
 * per revision, so domain traversal is the solver's innermost loop and it was paying for a hash
 * structure it never consulted.
 * <p>
 * Construction costs one bulk array fill, because the set handed to {@link
 * #handingOver} is kept as the membership index rather than copied: a spent {@link
 * DiscreteDomain.DiscreteDomainBuilder} never touches its backing set again (it asserts as much),
 * so the two can share it, exactly as the {@code unmodifiableSet} view used to.
 *
 * @param <T> the domain's value type
 */
final class OrderedValueSet<T> extends AbstractSet<T> {

    private final @NonNull Object[] elements;
    private final @NonNull Set<T> membership;

    private OrderedValueSet(@NonNull Object[] elements, @NonNull Set<T> membership) {
        this.elements = elements;
        this.membership = membership;
    }

    /**
     * Takes ownership of {@code values}, which the caller must never mutate again, and snapshots
     * its iteration order into an array. {@code values} must already be duplicate-free and ordered
     * as the domain should enumerate -- i.e. it is a builder's own backing set, handed over as that
     * builder is spent.
     */
    static <T> OrderedValueSet<T> handingOver(@NonNull Set<T> values) {
        return new OrderedValueSet<>(values.toArray(), values);
    }

    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Override
    public boolean contains(@Nullable Object value) {
        return membership.contains(value);
    }

    @Override
    public @NonNull Object[] toArray() {
        return elements.clone();
    }

    /**
     * An array spliterator rather than {@link AbstractSet}'s iterator-based one, so a stream over a
     * domain's values -- which is how {@link DiscreteSetDomain#stream} is read -- splits and
     * traverses without a per-element iterator call. {@link Spliterator#DISTINCT} and {@link
     * Spliterator#IMMUTABLE} are both reported because both are true of a handed-over set, and
     * {@link Spliterator#ORDERED} because this set's whole purpose is to preserve insertion order;
     * {@link java.util.Spliterators#spliterator(Object[], int, int, int)} adds {@link
     * Spliterator#SIZED}/{@link Spliterator#SUBSIZED} itself.
     */
    @Override
    public @NonNull Spliterator<T> spliterator() {
        return Spliterators.spliterator(elements, 0, elements.length,
                Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.IMMUTABLE);
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < elements.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public T next() {
                if (next >= elements.length) throw new NoSuchElementException();
                return (T) elements[next++];
            }
        };
    }
}
