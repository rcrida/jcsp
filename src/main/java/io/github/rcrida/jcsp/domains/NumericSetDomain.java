package io.github.rcrida.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * The generic result of {@link NumericDomain}'s default {@link NumericDomain#withBounds}: a plain
 * {@link NumericDiscreteDomain} over an arbitrary filtered {@link Set}, for callers that don't know
 * (or need to know) which specific numeric domain type produced it — the numeric analogue of {@link
 * DiscreteDomain.DiscreteDomainBuilder}'s own fallback to {@link ObjectSetDomain} for the same
 * reason.
 * <p>
 * Caches its bounds as extra record components exactly as {@link IntRangeDomain} does, so {@link
 * #getMin}/{@link #getMax} are a field read rather than a scan of every value — the O(1) bounds
 * fast path that {@link io.github.rcrida.jcsp.constraints.NumericBounds#min}/{@link
 * io.github.rcrida.jcsp.constraints.NumericBounds#max} already dispatch to for every other {@link
 * NumericDomain}. Both are {@code null} exactly when {@code values} is empty, which only the
 * canonical constructor can produce: {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder#build}
 * collapses an empty value set to {@link NumericEmptyDomain} instead. A record's canonical
 * constructor can't be declared less accessible than the record itself, so {@link
 * #NumericSetDomain(Set)} is the constructor to call when the bounds aren't already in hand, and
 * the assertion below guards the canonical one against bounds that don't match {@code values}.
 */
public record NumericSetDomain<N extends Number>(@NonNull Set<N> values, @Nullable N min, @Nullable N max)
        implements NumericDiscreteDomain<N>, DiscreteSetDomain<N> {

    public NumericSetDomain {
        assert Objects.equals(min, extremum(values, -1)) && Objects.equals(max, extremum(values, 1))
                : String.format("min (%s) and max (%s) must match the actual bounds of values %s", min, max, values);
    }

    /**
     * Scans {@code values} once for the bounds the canonical constructor requires.
     */
    public NumericSetDomain(@NonNull Set<N> values) {
        this(values, extremum(values, -1), extremum(values, 1));
    }

    /**
     * The value with the smallest ({@code direction} of {@code -1}) or largest ({@code 1}) {@link
     * Number#doubleValue}, or {@code null} when {@code values} is empty. Ties keep the first such
     * value iteration reaches, which is also what {@link
     * NumericDiscreteDomain.NumericDiscreteDomainBuilder} does when it tracks these bounds a value
     * at a time -- the two must agree, or the assertion above would reject the builder's output.
     */
    static <N extends Number> @Nullable N extremum(@NonNull Set<N> values, int direction) {
        N extreme = null;
        for (N value : values) {
            if (extreme == null
                    || direction * Double.compare(value.doubleValue(), extreme.doubleValue()) > 0) {
                extreme = value;
            }
        }
        return extreme;
    }

    @Override
    public N getMin() {
        if (min == null) throw new NoSuchElementException("an empty domain has no minimum");
        return min;
    }

    @Override
    public N getMax() {
        if (max == null) throw new NoSuchElementException("an empty domain has no maximum");
        return max;
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
