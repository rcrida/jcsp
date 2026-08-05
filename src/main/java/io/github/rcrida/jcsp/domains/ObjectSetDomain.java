package io.github.rcrida.jcsp.domains;

import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a set-based implementation of the {@link SetDomain} interface.
 */
@Builder(toBuilder = true)
public record ObjectSetDomain<T>(@Singular Set<T> values) implements SetDomain<T> {

    @Override
    public boolean equals(Object o) { return SetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return SetDomain.domainHashCode(this); }

    @Override
    public String toString() {
        return values.stream().map(Object::toString).collect(Collectors.joining(", ", "{", "}"));
    }

    public static class ObjectSetDomainBuilder<T> implements DiscreteDomain.Builder<T> {
        @Override
        public DiscreteDomain.Builder<T> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }

        /**
         * Overrides Lombok's generated {@code build()} (which would always construct a full
         * {@link ObjectSetDomain} even when narrowed to zero or one value) with {@link
         * SetDomain.DefaultBuilder#build}'s {@link ObjectSingletonDomain}/{@link
         * ObjectEmptyDomain} optimizations -- this builder's own {@code toBuilder()}/{@code
         * build()} otherwise shadow {@link SetDomain}'s default entirely, so without this override
         * narrowing an {@link ObjectSetDomain} down to zero or one value (e.g. via {@link
         * io.github.rcrida.jcsp.constraints.NumericBounds#narrow}) never reaches either.
         */
        @Override
        @SuppressWarnings("unchecked")
        public DiscreteDomain<T> build() {
            // Lombok's @Singular field is left null until the first value/values() call, rather
            // than eagerly allocated -- build() must tolerate that on a still-empty builder, same
            // as a builder narrowed down to zero values by delete().
            if (this.values == null || this.values.isEmpty()) {
                return ObjectEmptyDomain.instance();
            }
            if (this.values.size() == 1) {
                return (DiscreteDomain<T>) new ObjectSingletonDomain(this.values.get(0));
            }
            return new ObjectSetDomain<>(Collections.unmodifiableSet(new LinkedHashSet<>(this.values)));
        }
    }
}
