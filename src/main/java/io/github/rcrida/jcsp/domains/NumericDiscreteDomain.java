package io.github.rcrida.jcsp.domains;

import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The generic result of {@link NumericDomain}'s default {@link NumericDomain#withBounds}: a plain
 * {@link NumericDomain} over an arbitrary filtered {@link Set}, for callers that don't know (or
 * need to know) which specific numeric domain type produced it — the numeric analogue of {@link
 * SetDomain.DefaultBuilder}'s own fallback to {@link DomainObjectSet} for the same reason. Uses the
 * same {@code @Builder}/{@code @Singular} pattern as {@link DomainObjectSet} rather than a
 * hand-written compact constructor, for the same defensive-copy-plus-insertion-order guarantee
 * Lombok's generated builder already gives that class (backed by a {@code LinkedHashSet}
 * internally — confirmed by disassembling the generated builder, not merely assumed).
 */
@Builder(toBuilder = true)
public record NumericDiscreteDomain<N extends Number>(@Singular Set<N> values) implements NumericDomain<N>, SetDomain<N> {

    @SafeVarargs
    public static <N extends Number> NumericDiscreteDomain<N> of(N... values) {
        return NumericDiscreteDomain.<N>builder().values(List.of(values)).build();
    }

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
     * DiscreteDomain.Builder<N>} covariantly — otherwise the generated {@code toBuilder()}
     * (returning this class) and {@code SetDomain}'s own default clash on return type. Same trick
     * {@link DomainObjectSet}'s own nested builder subclass already relies on.
     */
    public static class NumericDiscreteDomainBuilder<N extends Number> implements DiscreteDomain.Builder<N> {
        @Override
        public DiscreteDomain.Builder<N> delete(@NonNull Object value) {
            this.values.remove(value);
            return this;
        }
    }
}
