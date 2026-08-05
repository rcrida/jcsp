package io.github.rcrida.jcsp.domains;

import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The generic result of {@link NumericDomain}'s default {@link NumericDomain#withBounds}: a plain
 * {@link NumericDiscreteDomain} over an arbitrary filtered {@link Set}, for callers that don't know
 * (or need to know) which specific numeric domain type produced it — the numeric analogue of {@link
 * SetDomain.DefaultBuilder}'s own fallback to {@link DomainObjectSet} for the same reason. Uses the
 * same {@code @Builder}/{@code @Singular} pattern as {@link DomainObjectSet} rather than a
 * hand-written compact constructor, for the same defensive-copy-plus-insertion-order guarantee
 * Lombok's generated builder already gives that class (backed by a {@link java.util.LinkedHashSet}
 * internally — confirmed by disassembling the generated builder, not merely assumed).
 */
@Builder(toBuilder = true)
public record NumericSetDomain<N extends Number>(@Singular Set<N> values) implements NumericDiscreteDomain<N>, SetDomain<N> {

    @Override
    public N getMin() {
        return values.stream().min(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    @Override
    public N getMax() {
        return values.stream().max(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    /**
     * Makes Lombok's generated builder satisfy {@link SetDomain}'s abstract {@code toBuilder():
     * DiscreteDomain.Builder<N>} covariantly — otherwise the generated {@link #toBuilder}
     * (returning this class) and {@link SetDomain}'s own default clash on return type. Same trick
     * {@link DomainObjectSet}'s own nested builder subclass already relies on.
     */
    public static class NumericSetDomainBuilder<N extends Number> implements DiscreteDomain.Builder<N> {
        @Override
        public DiscreteDomain.Builder<N> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }

        /**
         * Overrides Lombok's generated {@code build()} (which would always construct a full {@link
         * NumericSetDomain} even when narrowed to one value) with {@link
         * SetDomain.DefaultBuilder#build}'s singleton optimization -- this builder's own {@code
         * toBuilder()}/{@code build()} otherwise shadow {@link SetDomain}'s default entirely, so
         * without this override narrowing a {@link NumericSetDomain} down to one value (e.g. via
         * {@link io.github.rcrida.jcsp.consistency.arc.AC3} deleting individual unsupported values
         * one at a time, or {@link NumericDiscreteDomain#of}) never reaches a singleton domain.
         * Returns {@link NumericSingletonDomain} rather than plain {@link SingletonDomain} so the
         * result still satisfies {@link NumericDomain}, unlike {@link
         * DomainObjectSet.DomainObjectSetBuilder#build}'s equivalent override. Declared to return
         * {@link NumericDiscreteDomain} rather than the plain {@link DiscreteDomain} the overridden
         * interface method promises, since both branches this can produce satisfy it -- lets callers
         * like {@link NumericDomain#withBounds} skip an extra cast.
         */
        @Override
        public NumericDiscreteDomain<N> build() {
            // Lombok's @Singular field is left null until the first value/values() call, rather
            // than eagerly allocated -- build() must tolerate that on a still-empty builder.
            if (this.values == null) {
                return new NumericSetDomain<>(Set.of());
            }
            if (this.values.size() == 1) {
                return new NumericSingletonDomain<>(this.values.get(0));
            }
            return new NumericSetDomain<>(Collections.unmodifiableSet(new LinkedHashSet<>(this.values)));
        }
    }
}
