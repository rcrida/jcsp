package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Set;

/**
 * The generic result of {@link NumericDomain}'s default {@link NumericDomain#withBounds}: a plain
 * {@link NumericDiscreteDomain} over an arbitrary filtered {@link Set}, for callers that don't know
 * (or need to know) which specific numeric domain type produced it — the numeric analogue of {@link
 * DiscreteDomain.DiscreteDomainBuilder}'s own fallback to {@link ObjectSetDomain} for the same
 * reason.
 */
public record NumericSetDomain<N extends Number>(@NonNull Set<N> values) implements NumericDiscreteDomain<N>, DiscreteSetDomain<N> {

    @Override
    public N getMin() {
        return values.stream().min(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    @Override
    public N getMax() {
        return values.stream().max(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    @Override
    public boolean equals(Object o) { return DiscreteSetDomain.domainEquals(this, o); }

    @Override
    public int hashCode() { return DiscreteSetDomain.domainHashCode(this); }

    @Override
    public String toString() { return DiscreteSetDomain.domainToString(this); }

    /**
     * Overrides {@link DiscreteSetDomain}'s default, which would route through {@link
     * DiscreteDomain.DiscreteDomainBuilder} and collapse to the plain {@link ObjectSingletonDomain}/
     * {@link ObjectEmptyDomain} on narrowing -- losing {@link NumericDomain}-ness. Routes through
     * {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder} instead, so narrowing a {@link
     * NumericSetDomain} down to zero or one value (e.g. via {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3} deleting individual unsupported values one at a
     * time) still produces a {@link NumericSingletonDomain}/{@link NumericEmptyDomain}. Declared to
     * return the concrete builder rather than the plain {@link DiscreteDomain.Builder} the
     * overridden method promises, so a caller holding a {@link NumericSetDomain} reference gets the
     * full add/delete/build API back, not just delete/build.
     */
    @Override
    public NumericDiscreteDomain.NumericDiscreteDomainBuilder<N> toBuilder() {
        return new NumericDiscreteDomain.NumericDiscreteDomainBuilder<>(values);
    }
}
