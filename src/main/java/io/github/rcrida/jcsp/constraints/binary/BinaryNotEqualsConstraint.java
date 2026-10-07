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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code left != right} over any type {@code T} (not restricted to {@link Number}/{@link
 * Comparable}) — backs {@code ConstraintSatisfactionProblem.Builder#notEqualsConstraint(Variable,
 * Variable)} directly, and is also {@link
 * io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint}'s own pairwise {@code
 * BinaryDecomposable} decomposition target. Not in {@code
 * ConstraintSatisfactionProblem#CONTINUOUS_COMPATIBLE_CONSTRAINTS}, so {@link #propagate} only
 * ever needs to handle {@link DiscreteDomain} sides — a {@link
 * io.github.rcrida.jcsp.domains.BoundedDomain} pairing can't occur.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryNotEqualsConstraint<T> extends SymmetricBinaryConstraint<T> implements Propagatable {

    public static <T> BinaryNotEqualsConstraint<T> of(@NonNull Variable<T> left, @NonNull Variable<T> right) {
        return BinaryNotEqualsConstraint.<T>builder().left(left).right(right).build();
    }

    /**
     * A from-value loses support along a disequality arc exactly when {@code toValues} is the
     * singleton holding that same value, so two or more to-values support every from-value and
     * revision can delete nothing. {@code <= 1} rather than {@code == 1} because an empty {@code
     * toValues} supports nothing and must still revise, per {@link
     * BinaryConstraint#mayPruneAlongArc}'s contract.
     * <p>
     * This is what keeps {@link io.github.rcrida.jcsp.consistency.arc.AC3} affordable on a CSP
     * whose binary constraints are mostly disequalities -- an {@link
     * io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint}'s binary decomposition, or any
     * parser-produced {@code ne(x,y)} -- where the scan it replaces re-derived "supported" by
     * walking the whole to-side domain. {@link #propagate} is unaffected and remains this
     * constraint's own arc-consistent propagator; see
     * {@code docs/adr/0045-cheap-arc-prechecks-over-excluding-types-from-ac3.md}.
     */
    @Override
    public boolean mayPruneAlongArc(boolean arcFromLeft, @NonNull Collection<?> toValues) {
        return toValues.size() <= 1;
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T left, @NonNull T right) {
        return !Objects.equals(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " != " + getRight();
    }

    /**
     * The standard {@code !=} arc-consistency shortcut: a value can only ever be excluded from one
     * side by the <em>other</em> side being an exact singleton equal to it — anything less doesn't
     * rule it out, since some other value on the other side could still support it. An {@code
     * O(1)} check per side (plus one {@link DiscreteDomain.Builder#delete} when it fires), the same
     * fixpoint generic {@link io.github.rcrida.jcsp.consistency.arc.AC3} would otherwise reach via
     * an {@code O(|D1|*|D2|)} pairwise scan. Both sides can be singleton and equal at once (the
     * classic {@code x != x} conflict) — the first {@code if} block's own deletion attempt (on
     * {@code rDomain}, using {@code lDomain}'s value) empties {@code rDomain} and returns
     * immediately in that case, so the second block's own analogous empty check would be provably
     * dead: reaching it with something to delete requires {@code lDomain} to already equal
     * exactly {@code rDomain}'s singleton value too, which the first block already caught.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<T> lDomain = (DiscreteDomain<T>) domains.get(getLeft());
        DiscreteDomain<T> rDomain = (DiscreteDomain<T>) domains.get(getRight());
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (lDomain.isSingleton()) {
            Optional<DiscreteDomain<T>> pruned = deleteIfPresent(rDomain, lDomain.singleValue().orElseThrow());
            if (pruned.isPresent()) {
                if (pruned.get().isEmpty()) return Optional.empty();
                updated.put(getRight(), pruned.get());
            }
        }
        if (rDomain.isSingleton()) {
            deleteIfPresent(lDomain, rDomain.singleValue().orElseThrow()).ifPresent(d -> updated.put(getLeft(), d));
        }
        return Optional.of(updated);
    }

    private static <T> Optional<DiscreteDomain<T>> deleteIfPresent(DiscreteDomain<T> domain, T value) {
        if (!domain.contains(value)) return Optional.empty();
        return Optional.of(domain.toBuilder().delete(value).build());
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
