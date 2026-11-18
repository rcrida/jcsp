package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The factory for a domain of the integers in an inclusive range. It is no longer a domain type of
 * its own: {@link #of} returns a {@link NumericSetDomain} whose values are a range, which iterates
 * as an array, tests membership by its bounds, narrows by slicing that array, and prints as {@code
 * [min..max]}. See docs/adr/0069, docs/adr/0070 and docs/adr/0071.
 */
public final class IntRangeDomain {

    /**
     * The domain last built for each range, held weakly: while anything still holds it, {@link #of}
     * hands back the same one, so thousands of variables declared over one wide range share one
     * array, and once nothing does, it is collected. Keyed by {@link #key}.
     */
    private static final ConcurrentHashMap<Long, Entry> CACHE = new ConcurrentHashMap<>();
    /** Where collected domains' entries arrive, to be removed from {@link #CACHE} by the next {@link #of}. */
    private static final ReferenceQueue<NumericSetDomain<Integer>> COLLECTED = new ReferenceQueue<>();

    /** A cached domain, and the key its entry is removed by once it has been collected. */
    static final class Entry extends WeakReference<NumericSetDomain<Integer>> {
        private final long key;

        private Entry(long key, NumericSetDomain<Integer> domain) {
            super(domain, COLLECTED);
            this.key = key;
        }
    }

    private IntRangeDomain() {
    }

    /**
     * The integers {@code minInclusive..maxInclusive}, boxed once into an array that every narrowing of
     * it shares -- and the same domain as any earlier call for that range returned, while that one is
     * still held. Two threads asking at once may each build one; both are equal, and the later is kept.
     */
    public static NumericSetDomain<Integer> of(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        for (Reference<?> collected; (collected = COLLECTED.poll()) != null; ) {
            // Conditional, so an entry already replaced by a newer domain for the same range stays.
            CACHE.remove(((Entry) collected).key, collected);
        }
        long key = key(minInclusive, maxInclusive);
        Entry entry = CACHE.get(key);
        NumericSetDomain<Integer> domain = entry == null ? null : entry.get();
        if (domain == null) {
            domain = new NumericSetDomain<>(OrderedValueSet.range(minInclusive, maxInclusive), minInclusive, maxInclusive);
            CACHE.put(key, new Entry(key, domain));
        }
        return domain;
    }

    /** Both bounds in one {@code long}, so a lookup boxes one key rather than building a record. */
    private static long key(int minInclusive, int maxInclusive) {
        return ((long) minInclusive << 32) | (maxInclusive & 0xFFFF_FFFFL);
    }

    /**
     * The cache entry for a range, or {@code null}: package-visible only so a test can clear and enqueue it,
     * standing in for the garbage collector, which a test can't make collect a domain on demand.
     */
    static @Nullable Entry cachedEntry(int minInclusive, int maxInclusive) {
        return CACHE.get(key(minInclusive, maxInclusive));
    }
}
