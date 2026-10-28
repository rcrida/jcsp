package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.HashSet;
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
 * <p>
 * {@link #ofDistinct} is the other way in: an array alone, with the membership index left to the
 * first {@link #contains} that needs one (or a scan, for a handful of elements). Narrowing a
 * numeric domain builds its result that way -- see {@link
 * NumericDiscreteDomain.NumericDiscreteDomainBuilder#narrowing} and
 * docs/adr/0058.
 *
 * @param <T> the domain's value type
 */
final class OrderedValueSet<T> extends AbstractSet<T> {

    /**
     * Up to this many elements, {@link #contains} scans the array rather than building {@link #membership}.
     * Package-visible so {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder} can switch from scanning
     * its recorded deletions to hashing them at the same point.
     */
    static final int SCAN_LIMIT = 8;

    /**
     * A set of {@link Integer}s no more than this many bits per element wide is tested by a bit instead of by
     * hashing: at most a long's worth of memory per element, and a lookup that is one array read where a hash
     * probe chases a table slot, a node and a boxed key.
     */
    private static final int BITS_PER_ELEMENT_LIMIT = 64;

    private final @NonNull Object[] elements;
    /** Handed in by {@link #handingOver}; otherwise built by the first {@link #contains} that needs it. */
    private volatile @Nullable Set<T> membership;
    /**
     * Decided by the first {@link #contains} that is not a scan of a small array: bits if the elements are dense
     * integers, which are then tested instead of {@link #membership} even when that was handed in, and otherwise
     * {@link IntBits#NOT_DENSE}.
     */
    private volatile @Nullable IntBits intBits;

    private OrderedValueSet(@NonNull Object[] elements, @Nullable Set<T> membership) {
        this.elements = elements;
        this.membership = membership;
    }

    /**
     * Takes ownership of {@code distinct}, which must be duplicate-free and never mutated again,
     * and builds no membership index: narrowing a domain filters the array it already holds, and
     * most narrowed domains are only ever traversed. {@link #contains} builds the index on first
     * use, or just scans a small array.
     */
    static <T> OrderedValueSet<T> ofDistinct(@NonNull Object[] distinct) {
        return new OrderedValueSet<>(distinct, null);
    }

    /** The backing array itself, for a caller that reads it once and never writes it. */
    @NonNull Object[] elements() {
        return elements;
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
    @SuppressWarnings("unchecked")
    public boolean contains(@Nullable Object value) {
        IntBits bits = intBits;
        if (bits == null) {
            if (membership == null && elements.length <= SCAN_LIMIT) {
                for (Object element : elements) {
                    if (element.equals(value)) return true;
                }
                return false;
            }
            // Idempotent, so two threads racing here each decide the same and either may win.
            bits = IntBits.of(elements);
            intBits = bits;
        }
        if (bits != IntBits.NOT_DENSE) return bits.contains(value);
        Set<T> index = membership;
        if (index == null) {
            index = HashSet.newHashSet(elements.length);
            for (Object element : elements) index.add((T) element);
            membership = index;
        }
        return index.contains(value);
    }

    /**
     * Membership of a set of {@link Integer}s as one bit per value from the smallest, for a set dense enough
     * that this is small. A scheduling domain of start times is the typical case, and {@code contains} over it
     * is the innermost call of the element constraint's support check.
     */
    private static final class IntBits {
        /** The answer for elements that are not dense integers: test them through {@link #membership} instead. */
        static final IntBits NOT_DENSE = new IntBits(0, new long[0]);

        private final int min;
        private final long[] words;

        private IntBits(int min, long[] words) {
            this.min = min;
            this.words = words;
        }

        /** The bits of {@code elements}, or {@link #NOT_DENSE} if any is not an {@link Integer} or they are too sparse. */
        static IntBits of(Object[] elements) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (Object element : elements) {
                if (!(element instanceof Integer integer)) return NOT_DENSE;
                min = Math.min(min, integer);
                max = Math.max(max, integer);
            }
            long span = (long) max - min + 1;
            if (span > (long) elements.length * BITS_PER_ELEMENT_LIMIT) return NOT_DENSE;
            long[] words = new long[(int) ((span + 63) >>> 6)];
            for (Object element : elements) {
                int offset = (Integer) element - min;
                words[offset >>> 6] |= 1L << offset;
            }
            return new IntBits(min, words);
        }

        boolean contains(@Nullable Object value) {
            if (!(value instanceof Integer integer)) return false;
            long offset = (long) integer - min;
            return offset >= 0 && offset < (long) words.length * 64 && (words[(int) (offset >>> 6)] & (1L << offset)) != 0;
        }
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
