package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Arrays;
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
 * <p>
 * {@link #range} is the third: consecutive integers. A set's order is its insertion order, not a
 * sort, but when its elements happen to ascend -- a range's always do -- {@link #between} narrows it
 * by bounds into a view of the same array cut at both ends, so {@link NumericDomain#withBounds}
 * copies nothing -- see docs/adr/0070.
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

    /** How a set's elements are ordered, which decides whether {@link #between} can slice it. */
    private enum Shape {
        /**
         * The consecutive ascending {@link Integer}s from {@link #rangeMin}: built by {@link #range}, kept by
         * {@link #between}, and compared, hashed, printed and tested for membership by its bounds alone.
         */
        RANGE,
        /** {@link Number}s strictly ascending by {@link Number#doubleValue}, with gaps. */
        ASCENDING,
        /** Not known to ascend: filtered, never sliced. */
        UNORDERED
    }

    private final @NonNull Object[] elements;
    /** Where this set's elements start in {@link #elements}; non-zero only for a slice taken by {@link #between}. */
    private final int from;
    private final int size;
    private final Shape shape;
    /** The first element of a {@link Shape#RANGE}; unused otherwise. */
    private final int rangeMin;
    /**
     * The set owning {@link #elements} -- {@code this}, unless this is a slice of it. A slice tests membership
     * against its bounds and then the root's index, so narrowing never builds an index of its own.
     */
    private final OrderedValueSet<T> root;
    /** Handed in by {@link #handingOver}; otherwise built by the first {@link #contains} that needs it. */
    private volatile @Nullable Set<T> membership;
    /**
     * Decided by the first {@link #contains} that is not a scan of a small array: bits if the elements are dense
     * integers, which are then tested instead of {@link #membership} even when that was handed in, and otherwise
     * {@link IntBits#NOT_DENSE}.
     */
    private volatile @Nullable IntBits intBits;

    private OrderedValueSet(@NonNull Object[] elements, int from, int size, Shape shape, int rangeMin,
                            @Nullable OrderedValueSet<T> root, @Nullable Set<T> membership) {
        this.elements = elements;
        this.from = from;
        this.size = size;
        this.shape = shape;
        this.rangeMin = rangeMin;
        this.root = root == null ? this : root;
        this.membership = membership;
    }

    /** The integers {@code min..max} in ascending order, boxed once into an array every slice of it shares. */
    static OrderedValueSet<Integer> range(int min, int max) {
        Integer[] boxed = new Integer[max - min + 1];
        for (int i = 0; i < boxed.length; i++) boxed[i] = min + i;
        return new OrderedValueSet<>(boxed, 0, boxed.length, Shape.RANGE, min, null, null);
    }

    /** Whether this set is the consecutive integers of a {@link #range} or a slice of one. */
    boolean isRange() {
        return shape == Shape.RANGE;
    }

    /**
     * Whether this set's elements are {@link Number}s strictly ascending by {@link Number#doubleValue}, so
     * that {@link #between} can slice it: every range, and any other set found or known to ascend when built.
     */
    boolean isAscending() {
        return shape != Shape.UNORDERED;
    }

    /** {@link Shape#ASCENDING} if the first {@code size} of {@code elements} strictly ascend, else {@link Shape#UNORDERED}. */
    private static Shape shapeOf(@NonNull Object[] elements, int size) {
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            if (!(elements[i] instanceof Number number) || !(number.doubleValue() > previous)) return Shape.UNORDERED;
            previous = number.doubleValue();
        }
        return Shape.ASCENDING;
    }

    /**
     * The elements between {@code lo} and {@code hi} inclusive, of this {@link #isAscending} set, as a
     * view of the same array: they are one contiguous run of it, so narrowing by bounds costs two binary
     * searches and one small object, however many values survive. Empty if none do.
     */
    OrderedValueSet<T> between(double lo, double hi) {
        assert isAscending() : "only an ascending set is sliced by its bounds";
        int start = firstIndex(lo, true);
        int end = Math.max(start, firstIndex(hi, false));
        return new OrderedValueSet<>(elements, from + start, end - start, shape,
                shape == Shape.RANGE ? rangeMin + start : 0, root, null);
    }

    /** The position of the first element at least ({@code inclusive}) or above {@code bound}. */
    private int firstIndex(double bound, boolean inclusive) {
        int lo = 0;
        int hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            double value = ((Number) elements[from + mid]).doubleValue();
            if (inclusive ? value < bound : value <= bound) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** The {@code index}th element, for a caller walking this set by position. */
    @SuppressWarnings("unchecked")
    T get(int index) {
        return (T) elements[from + index];
    }

    /**
     * Takes ownership of {@code distinct}, which must be duplicate-free and never mutated again,
     * and builds no membership index: narrowing a domain filters the array it already holds, and
     * most narrowed domains are only ever traversed. {@link #contains} builds the index on first
     * use, or just scans a small array.
     */
    static <T> OrderedValueSet<T> ofDistinct(@NonNull Object[] distinct) {
        return new OrderedValueSet<>(distinct, 0, distinct.length, shapeOf(distinct, distinct.length), 0, null, null);
    }

    /**
     * As {@link #ofDistinct(Object[])}, over only the first {@code size} of {@code distinct}: a
     * caller filtering into an array sized for the worst case keeps it rather than trimming a copy.
     * {@code ascending} is what the caller knows of the set it filtered -- filtering keeps an
     * ascending set ascending -- and is trusted rather than checked.
     */
    static <T> OrderedValueSet<T> ofDistinct(@NonNull Object[] distinct, int size, boolean ascending) {
        return new OrderedValueSet<>(distinct, 0, size, ascending ? Shape.ASCENDING : Shape.UNORDERED, 0, null, null);
    }

    /**
     * Takes ownership of {@code values}, which the caller must never mutate again, and snapshots
     * its iteration order into an array. {@code values} must already be duplicate-free and ordered
     * as the domain should enumerate -- i.e. it is a builder's own backing set, handed over as that
     * builder is spent.
     */
    static <T> OrderedValueSet<T> handingOver(@NonNull Set<T> values) {
        Object[] elements = values.toArray();
        return new OrderedValueSet<>(elements, 0, elements.length, shapeOf(elements, elements.length), 0, null, values);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /** Two ranges compare by their bounds alone; anything else as {@link AbstractSet} does. */
    @Override
    public boolean equals(Object o) {
        if (isRange() && o instanceof OrderedValueSet<?> other && other.isRange()) {
            return rangeMin == other.rangeMin && size == other.size;
        }
        return super.equals(o);
    }

    /** A range as {@code [min..max]}; anything else as {@link AbstractSet} prints it. */
    @Override
    public String toString() {
        return isRange() ? "[" + rangeMin + ".." + (rangeMin + size - 1) + "]" : super.toString();
    }

    /** A range's hash in closed form: {@link AbstractSet}'s sum of {@link Integer#hashCode}, wrapped as it would. */
    @Override
    public int hashCode() {
        if (!isRange()) return super.hashCode();
        return (int) (((long) rangeMin + rangeMin + size - 1) * size / 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(@Nullable Object value) {
        if (isRange()) {
            return value instanceof Integer integer && integer >= rangeMin && (long) integer - rangeMin < size;
        }
        IntBits bits = intBits;
        if (bits == null) {
            int end = from + size;
            if (membership == null && size <= SCAN_LIMIT) {
                for (int i = from; i < end; i++) {
                    if (elements[i].equals(value)) return true;
                }
                return false;
            }
            if (root != this) {
                return value instanceof Number number
                        && number.doubleValue() >= ((Number) elements[from]).doubleValue()
                        && number.doubleValue() <= ((Number) elements[end - 1]).doubleValue()
                        && root.contains(value);
            }
            // Idempotent, so two threads racing here each decide the same and either may win.
            bits = IntBits.of(elements, from, end);
            intBits = bits;
        }
        if (bits != IntBits.NOT_DENSE) return bits.contains(value);
        Set<T> index = membership;
        if (index == null) {
            index = HashSet.newHashSet(size);
            for (int i = from, end = from + size; i < end; i++) index.add((T) elements[i]);
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

        /**
         * The bits of {@code elements[from..end)}, or {@link #NOT_DENSE} if any is not an {@link Integer} or they
         * are too sparse.
         */
        static IntBits of(Object[] elements, int from, int end) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = from; i < end; i++) {
                if (!(elements[i] instanceof Integer integer)) return NOT_DENSE;
                min = Math.min(min, integer);
                max = Math.max(max, integer);
            }
            long span = (long) max - min + 1;
            if (span > (long) (end - from) * BITS_PER_ELEMENT_LIMIT) return NOT_DENSE;
            long[] words = new long[(int) ((span + 63) >>> 6)];
            for (int i = from; i < end; i++) {
                int offset = (Integer) elements[i] - min;
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
        return Arrays.copyOfRange(elements, from, from + size);
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
        return Spliterators.spliterator(elements, from, from + size,
                Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.IMMUTABLE);
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return new Iterator<>() {
            private int next = from;
            private final int end = from + size;

            @Override
            public boolean hasNext() {
                return next < end;
            }

            @Override
            @SuppressWarnings("unchecked")
            public T next() {
                if (next >= end) throw new NoSuchElementException();
                return (T) elements[next++];
            }
        };
    }
}
