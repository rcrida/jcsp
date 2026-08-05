package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

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
     */
    final class NumericDiscreteDomainBuilder<N extends Number> implements DiscreteDomain.Builder<N> {
        private final Set<N> mutableValues = new LinkedHashSet<>();

        NumericDiscreteDomainBuilder(Set<N> initial) {
            mutableValues.addAll(initial);
        }

        public NumericDiscreteDomainBuilder<N> value(N value) {
            mutableValues.add(value);
            return this;
        }

        public NumericDiscreteDomainBuilder<N> values(Collection<? extends N> values) {
            mutableValues.addAll(values);
            return this;
        }

        @Override
        public NumericDiscreteDomainBuilder<N> delete(@NonNull Object value) {
            mutableValues.remove(value);
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
            return new NumericSetDomain<>(Collections.unmodifiableSet(new LinkedHashSet<>(mutableValues)));
        }
    }
}
