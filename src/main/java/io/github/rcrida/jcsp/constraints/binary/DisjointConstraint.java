package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A binary constraint enforcing {@code left ∩ right = ∅} over set variables.
 * <p>
 * Propagation is symmetric: any element already forced into one side's lower bound can never
 * appear in the other side, so it's removed from the other side's upper bound. Only {@link
 * SetBoundedDomain}-typed variable pairs are narrowed; a non-{@code SetBoundedDomain} side is left
 * to {@link #isSatisfiedBy} and AC3's generic binary-arc handling, same as {@link SubsetConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DisjointConstraint<E> extends BinaryConstraint<Set<E>, Set<E>> implements Propagatable {

    public static <E> DisjointConstraint<E> of(@NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right) {
        return DisjointConstraint.<E>builder().left(left).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Set<E> leftValue, @NonNull Set<E> rightValue) {
        return leftValue.stream().noneMatch(rightValue::contains);
    }

    @Override
    public String getRelation() {
        return getLeft() + " disjoint " + getRight();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (!(domains.get(getLeft()) instanceof SetBoundedDomain<?>) || !(domains.get(getRight()) instanceof SetBoundedDomain<?>)) {
            return Optional.of(Map.of());
        }
        SetBoundedDomain<E> left = (SetBoundedDomain<E>) domains.get(getLeft());
        SetBoundedDomain<E> right = (SetBoundedDomain<E>) domains.get(getRight());

        Set<E> leftAllowed = new HashSet<>(left.getUpperBound());
        leftAllowed.removeAll(right.getLowerBound());
        SetBoundedDomain<E> newLeft = left.withUpperBound(leftAllowed);
        if (newLeft.isEmpty()) return Optional.empty();

        Set<E> rightAllowed = new HashSet<>(right.getUpperBound());
        rightAllowed.removeAll(left.getLowerBound());
        SetBoundedDomain<E> newRight = right.withUpperBound(rightAllowed);
        if (newRight.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (!newLeft.equals(left)) updated.put(getLeft(), newLeft);
        if (!newRight.equals(right)) updated.put(getRight(), newRight);
        return Optional.of(updated);
    }

    /**
     * Sound only when both sides are already fully resolved (singleton) — see {@link
     * SubsetConstraint#explainInfeasible}'s Javadoc for the identical reasoning.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(getVariables(), domains);
        return GroundNogoodConstraint.fromReason(reason);
    }
}
