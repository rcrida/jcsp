package io.github.rcrida.jcsp.domains;

import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a set-based implementation of the {@link SetDomain} interface.
 */
@Builder(toBuilder = true)
public record DomainObjectSet<T>(@Singular Set<T> values) implements SetDomain<T> {

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    public static class DomainObjectSetBuilder<T> implements DiscreteDomain.Builder<T> {
        @Override
        public DiscreteDomain.Builder<T> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }

        /**
         * Overrides Lombok's generated {@code build()} (which would always construct a full
         * {@link DomainObjectSet} even when narrowed to one value) with {@link
         * SetDomain.DefaultBuilder#build}'s SingletonDomain optimization -- this builder's own
         * {@code toBuilder()}/{@code build()} otherwise shadow {@link SetDomain}'s default entirely,
         * so without this override narrowing a {@link DomainObjectSet} down to one value (e.g. via
         * {@link io.github.rcrida.jcsp.constraints.NumericBounds#narrow}) never reaches {@link
         * SingletonDomain}.
         */
        @Override
        @SuppressWarnings("unchecked")
        public DiscreteDomain<T> build() {
            // Lombok's @Singular field is left null until the first value/values() call, rather
            // than eagerly allocated -- build() must tolerate that on a still-empty builder.
            if (this.values == null) {
                return new DomainObjectSet<>(Set.of());
            }
            if (this.values.size() == 1) {
                return (DiscreteDomain<T>) new SingletonDomain(this.values.get(0));
            }
            return new DomainObjectSet<>(Collections.unmodifiableSet(new LinkedHashSet<>(this.values)));
        }
    }
}
