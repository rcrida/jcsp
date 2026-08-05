package io.github.rcrida.jcsp.domains;

import java.util.List;

/**
 * A domain that is both {@link NumericDomain} and {@link DiscreteDomain} -- the capability shared
 * by every enumerable numeric domain in this library ({@link NumericSetDomain}, {@link
 * NumericSingletonDomain}, {@link IntRangeDomain}), as distinct from a continuous {@link
 * BoundedDomain}, which is {@link NumericDomain} but not enumerable. Exists so code that needs both
 * capabilities together -- most directly, {@link #of}'s own return type -- has a real name for that
 * combination instead of falling back to a Java intersection type or picking just one interface and
 * losing the other's methods.
 */
public interface NumericDiscreteDomain<N extends Number> extends NumericDomain<N>, DiscreteDomain<N> {

    /**
     * Builds a numeric discrete domain from explicit values, collapsing to a {@link
     * NumericSingletonDomain} for exactly one value or a {@link NumericSetDomain} otherwise -- see
     * {@link NumericSetDomain.NumericSetDomainBuilder#build}.
     */
    @SafeVarargs
    static <N extends Number> NumericDiscreteDomain<N> of(N... values) {
        return NumericSetDomain.<N>builder().values(List.of(values)).build();
    }
}
