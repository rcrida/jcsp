package io.github.rcrida.jcsp.domains;

/**
 * The factory for a domain of the integers in an inclusive range. It is no longer a domain type of
 * its own: {@link #of} returns a {@link NumericSetDomain} whose values are a range, which iterates
 * as an array, tests membership by its bounds, narrows by slicing that array, and prints as {@code
 * [min..max]}. See docs/adr/0068 and docs/adr/0069.
 */
public final class IntRangeDomain {

    private IntRangeDomain() {
    }

    /** The integers {@code minInclusive..maxInclusive}, boxed once into an array that every narrowing of it shares. */
    public static NumericSetDomain<Integer> of(int minInclusive, int maxInclusive) {
        assert minInclusive <= maxInclusive : String.format("minInclusive (%d) must be less than or equal to maxInclusive (%d)", minInclusive, maxInclusive);
        return new NumericSetDomain<>(OrderedValueSet.range(minInclusive, maxInclusive), minInclusive, maxInclusive);
    }
}
