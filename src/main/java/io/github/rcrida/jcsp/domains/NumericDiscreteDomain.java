package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * {@link NumericDiscreteDomainBuilder#build}.
     */
    @SafeVarargs
    static <N extends Number> NumericDiscreteDomain<N> of(N... values) {
        return NumericDiscreteDomain.<N>builder().values(List.of(values)).build();
    }

    /**
     * A fresh, empty builder — the numeric analogue of {@link DiscreteDomain#builder()}.
     */
    static <N extends Number> NumericDiscreteDomainBuilder<N> builder() {
        return new NumericDiscreteDomainBuilder<>(Set.of());
    }

    /**
     * The single hand-written builder shared by {@link NumericSetDomain}, {@link
     * NumericSingletonDomain}, and {@link NumericEmptyDomain} -- the numeric analogue of {@link
     * DiscreteDomain.DiscreteDomainBuilder}, kept as a separate class (rather than reusing that
     * one) because {@link #build} must collapse to {@link NumericSingletonDomain}/{@link
     * NumericEmptyDomain} rather than the plain {@link ObjectSingletonDomain}/{@link
     * ObjectEmptyDomain}, to keep satisfying {@link NumericDomain}.
     * <p>
     * Tracks the bounds {@link NumericSetDomain} caches as it goes, so building a domain stays the
     * single pass over the values it always was: {@link #value} widens them in constant time, and
     * only {@link #delete} of a value that is itself a bound forces {@link #build} to rescan.
     */
    final class NumericDiscreteDomainBuilder<N extends Number> implements DiscreteDomain.Builder<N> {
        private final Set<N> mutableValues;
        private @Nullable N min;
        private @Nullable N max;
        /** Set by {@link #delete} when the removed value was itself a bound; see {@link #build}. */
        private boolean boundsStale;
        /** Set by {@link #build} once {@link #mutableValues} belongs to a domain; see {@link #mutable}. */
        private boolean built;

        NumericDiscreteDomainBuilder(Set<N> initial) {
            this(initial.size());
            values(initial);
        }

        /**
         * Sized for a caller that will add {@code expectedValues} of them, so growing the backing
         * set never rehashes -- {@link NumericDomain#withBounds} filters a whole domain into a
         * builder it starts empty, where the default capacity would rehash all the way up.
         */
        NumericDiscreteDomainBuilder(int expectedValues) {
            mutableValues = LinkedHashSet.newLinkedHashSet(expectedValues);
        }

        /**
         * The set to mutate. {@link #build} hands this set to the domain it returns rather than
         * copying it, so mutating a builder afterwards would mutate that domain -- a builder is
         * spent once built.
         */
        private Set<N> mutable() {
            assert !built : "a builder cannot be reused after build()";
            return mutableValues;
        }

        /**
         * Widens the tracked bounds unconditionally rather than only when the value is new: a
         * value already present can't lie outside bounds it was itself folded into, so the strict
         * comparisons below are a no-op for it. Ties keep the first value added, matching {@link
         * NumericSetDomain#extremum} over the insertion-ordered set this builds.
         */
        public NumericDiscreteDomainBuilder<N> value(N value) {
            mutable().add(value);
            if (min == null || value.doubleValue() < min.doubleValue()) min = value;
            if (max == null || value.doubleValue() > max.doubleValue()) max = value;
            return this;
        }

        public NumericDiscreteDomainBuilder<N> values(Collection<? extends N> values) {
            for (N value : values) value(value);
            return this;
        }

        @Override
        public NumericDiscreteDomainBuilder<N> delete(@NonNull Object value) {
            if (mutable().remove(value) && (value.equals(min) || value.equals(max))) {
                boundsStale = true;
            }
            return this;
        }

        @Override
        public NumericDiscreteDomain<N> build() {
            if (mutableValues.isEmpty()) {
                return NumericEmptyDomain.instance();
            }
            if (mutableValues.size() == 1) {
                return new NumericSingletonDomain<>(mutableValues.iterator().next());
            }
            if (boundsStale) {
                min = NumericSetDomain.extremum(mutableValues, -1);
                max = NumericSetDomain.extremum(mutableValues, 1);
                boundsStale = false;
            }
            built = true;
            return new NumericSetDomain<>(OrderedValueSet.handingOver(mutableValues), min, max);
        }
    }
}
