package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code left == right} over any type {@code T} (not restricted to {@link Number}/{@link
 * Comparable} the way {@link BinaryComparatorConstraint} is) — backs {@code
 * ConstraintSatisfactionProblem.Builder#equalsConstraint(Variable, Variable)} directly. Not in
 * {@code ConstraintSatisfactionProblem#CONTINUOUS_COMPATIBLE_CONSTRAINTS}, so {@link #propagate}
 * only ever needs to handle {@link DiscreteDomain} sides — a {@link
 * io.github.rcrida.jcsp.domains.BoundedDomain} pairing can't occur.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryEqualsConstraint<T> extends SymmetricBinaryConstraint<T> implements Propagatable {

    public static <T> BinaryEqualsConstraint<T> of(@NonNull Variable<T> left, @NonNull Variable<T> right) {
        return BinaryEqualsConstraint.<T>builder().left(left).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T left, @NonNull T right) {
        return Objects.equals(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " == " + getRight();
    }

    /**
     * Narrows both sides to their exact value-set intersection — full generalized arc consistency
     * via a direct {@code O(min(|D1|,|D2|))} set intersection, strictly cheaper than (and no less
     * tight than) letting generic {@link io.github.rcrida.jcsp.consistency.arc.AC3} arrive at the
     * same fixpoint via an {@code O(|D1|*|D2|)} pairwise {@code isSatisfiedByArcValues} scan — the
     * same shape {@link BinaryNotEqualsConstraint#propagate} uses for its own {@code O(1)}
     * shortcut. Unlike {@link BinaryComparatorConstraint}'s own {@code EQ} case (bounds-only
     * interval intersection via {@link io.github.rcrida.jcsp.constraints.NumericBounds}), this is
     * a genuine value-level intersection, so it doesn't under-prune a gapped discrete pair (e.g.
     * {@code x1∈{1,4}, x2∈{2,4}}: bounds intersection narrows {@code x1} to {@code {4}} correctly
     * but leaves {@code x2} at {@code {2,4}}, retaining {@code 2} even though it now has no
     * support — this method narrows both to {@code {4}} directly).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<T> lDomain = (DiscreteDomain<T>) domains.get(getLeft());
        DiscreteDomain<T> rDomain = (DiscreteDomain<T>) domains.get(getRight());
        Set<T> lValues = new HashSet<>(lDomain.toList());
        Set<T> rValues = new HashSet<>(rDomain.toList());
        Set<T> intersection = new HashSet<>(lValues);
        intersection.retainAll(rValues);
        if (intersection.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        narrowToIntersection(lDomain, lValues, intersection).ifPresent(d -> updated.put(getLeft(), d));
        narrowToIntersection(rDomain, rValues, intersection).ifPresent(d -> updated.put(getRight(), d));
        return Optional.of(updated);
    }

    /**
     * Narrows {@code domain} down to {@code keep} (a non-empty subset of {@code currentValues},
     * guaranteed by {@link #propagate}'s own {@code intersection.isEmpty()} check before either
     * call), returning {@link Optional#empty()} when nothing actually changed. The narrowed result
     * can never itself come back empty here — {@code keep} is already checked non-empty by the
     * caller — so, unlike {@link BinaryNotEqualsConstraint}'s own singleton-forcing helper, there's
     * deliberately no post-hoc empty check to make (it would be provably dead code).
     */
    private static <T> Optional<DiscreteDomain<T>> narrowToIntersection(
            DiscreteDomain<T> domain, Set<T> currentValues, Set<T> keep) {
        if (keep.size() == currentValues.size()) return Optional.empty();
        DiscreteDomain.Builder<T> builder = domain.toBuilder();
        for (T value : currentValues) {
            if (!keep.contains(value)) builder.delete(value);
        }
        return Optional.of(builder.build());
    }

    /**
     * Cites both sides' exact current value sets via {@link ValueSetNogoodConstraint#fromCurrentState}
     * — the same pattern {@link BinaryComparatorConstraint#explainInfeasible} already uses, sound
     * for any {@code T} (not restricted to numeric/gapless types the way {@link
     * io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint} would require).
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return ValueSetNogoodConstraint.fromCurrentState(Set.of(getLeft(), getRight()), domains);
    }
}
