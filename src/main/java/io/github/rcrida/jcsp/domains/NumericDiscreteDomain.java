package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
        /** Null while this builder only narrows {@link #source}; see {@link #materialize}. */
        private @Nullable Set<N> mutableValues;
        /**
         * The values of the domain being narrowed, untouched: deletions are recorded in {@link
         * #removed} and applied to its array by {@link #build}, so narrowing rehashes nothing.
         */
        private @Nullable OrderedValueSet<N> source;
        private @Nullable List<Object> removed;
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
         * A builder for narrowing the domain holding {@code values}, whose bounds are {@code min}
         * and {@code max}. Starts from {@code values} itself when it is an {@link OrderedValueSet}
         * -- what every built domain holds -- and from a copy otherwise.
         */
        static <N extends Number> NumericDiscreteDomainBuilder<N> narrowing(
                Set<N> values, @Nullable N min, @Nullable N max) {
            if (!(values instanceof OrderedValueSet<N> ordered)) return new NumericDiscreteDomainBuilder<>(values);
            NumericDiscreteDomainBuilder<N> builder = new NumericDiscreteDomainBuilder<>(0);
            builder.mutableValues = null;
            builder.source = ordered;
            builder.min = min;
            builder.max = max;
            return builder;
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
            if (source != null) materialize();
            return mutableValues;
        }

        /** Applies the recorded deletions to a hash set, for the rare builder that also adds values. */
        @SuppressWarnings("unchecked")
        private void materialize() {
            mutableValues = LinkedHashSet.newLinkedHashSet(source.size());
            for (Object value : source.elements()) {
                if (removed == null || !removed.contains(value)) mutableValues.add((N) value);
            }
            source = null;
            removed = null;
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
            if (source != null) {
                assert !built : "a builder cannot be reused after build()";
                if (removed == null) removed = new ArrayList<>();
                removed.add(value);
                if (value.equals(min) || value.equals(max)) boundsStale = true;
                return this;
            }
            if (mutable().remove(value) && (value.equals(min) || value.equals(max))) {
                boundsStale = true;
            }
            return this;
        }

        /**
         * Filters {@link #source}'s array by {@link #removed}, in one pass with one equality test
         * per recorded deletion per value, and collapses as {@link #build} does.
         */
        @SuppressWarnings("unchecked")
        private NumericDiscreteDomain<N> buildNarrowed() {
            OrderedValueSet<N> from = source;
            if (removed == null) return unchanged(from);
            Object[] elements = from.elements();
            Object[] kept = new Object[elements.length];
            int count = 0;
            for (Object element : elements) {
                if (!removed.contains(element)) kept[count++] = element;
            }
            if (count == elements.length) return unchanged(from);
            if (count == 0) return NumericEmptyDomain.instance();
            if (count == 1) return new NumericSingletonDomain<>((N) kept[0]);
            if (boundsStale) {
                min = (N) extremum(kept, count, -1);
                max = (N) extremum(kept, count, 1);
                boundsStale = false;
            }
            built = true;
            return new NumericSetDomain<>(OrderedValueSet.ofDistinct(Arrays.copyOf(kept, count)), min, max);
        }

        /** {@code values} as the domain it already is, with this builder's bounds. */
        private NumericDiscreteDomain<N> unchanged(OrderedValueSet<N> values) {
            if (values.isEmpty()) return NumericEmptyDomain.instance();
            if (values.size() == 1) return new NumericSingletonDomain<>(values.iterator().next());
            built = true;
            return new NumericSetDomain<>(values, min, max);
        }

        /** As {@link NumericSetDomain#extremum}, over the first {@code count} of {@code elements}. */
        private static Number extremum(Object[] elements, int count, int direction) {
            Number extreme = (Number) elements[0];
            for (int i = 1; i < count; i++) {
                Number value = (Number) elements[i];
                if (direction * Double.compare(value.doubleValue(), extreme.doubleValue()) > 0) extreme = value;
            }
            return extreme;
        }

        @Override
        public NumericDiscreteDomain<N> build() {
            if (source != null) return buildNarrowed();
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
