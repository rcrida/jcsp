package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A binary constraint enforcing {@code left ⊆ right} over set variables.
 * <p>
 * Propagation narrows both sides directly via {@link SetBoundedDomain}'s own bound accessors,
 * chaining successive calls since each returns the self-typed {@code SetBoundedDomain<E>} rather
 * than the weaker {@code Domain<Set<E>>} — see that interface's own Javadoc for why. Four
 * narrowing facts follow from {@code left ⊆ right}:
 * <ul>
 *   <li>every element {@code left} is already forced to have must also be forced into {@code
 *       right} (a value {@code left} <em>must</em> take, combined with the subset requirement,
 *       means {@code right} <em>must</em> contain it too) — {@code right}'s lower bound grows;</li>
 *   <li>{@code right} must be large enough to hold at least as many elements as {@code left} is
 *       required to — {@code right}'s minimum cardinality rises to at least {@code left}'s;</li>
 *   <li>{@code left} can only ever contain elements {@code right} could possibly have — {@code
 *       left}'s upper bound shrinks to {@code right}'s;</li>
 *   <li>{@code left} can't be larger than {@code right} could possibly be — {@code left}'s
 *       maximum cardinality falls to at most {@code right}'s.</li>
 * </ul>
 * Only {@link SetBoundedDomain}-typed variable pairs are narrowed; a non-{@code SetBoundedDomain}
 * side (unexpected in practice, since this is the only domain kind meaningful for a set variable)
 * is left to {@link #isSatisfiedBy} and AC3's generic binary-arc handling, which safely no-ops for
 * non-{@link io.github.rcrida.jcsp.domains.DiscreteDomain} domains rather than crashing.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubsetConstraint<E> extends BinaryConstraint<Set<E>, Set<E>> implements Propagatable {

    public static <E> SubsetConstraint<E> of(@NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right) {
        return SubsetConstraint.<E>builder().left(left).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Set<E> leftValue, @NonNull Set<E> rightValue) {
        return rightValue.containsAll(leftValue);
    }

    @Override
    public String getRelation() {
        return getLeft() + " subsetOf " + getRight();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (!(domains.get(getLeft()) instanceof SetBoundedDomain<?>) || !(domains.get(getRight()) instanceof SetBoundedDomain<?>)) {
            return Optional.of(Map.of());
        }
        SetBoundedDomain<E> left = (SetBoundedDomain<E>) domains.get(getLeft());
        SetBoundedDomain<E> right = (SetBoundedDomain<E>) domains.get(getRight());

        SetBoundedDomain<E> newRight = right.withLowerBound(left.getLowerBound())
                .withCardinality(left.getMinCardinality(), Integer.MAX_VALUE);
        if (newRight.isEmpty()) return Optional.empty();

        SetBoundedDomain<E> newLeft = left.withUpperBound(right.getUpperBound())
                .withCardinality(0, right.getMaxCardinality());
        if (newLeft.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (!newRight.equals(right)) updated.put(getRight(), newRight);
        if (!newLeft.equals(left)) updated.put(getLeft(), newLeft);
        return Optional.of(updated);
    }

    /**
     * Two-tier explanation via {@link SetBoundsNogoodConstraint#explainViaGroundOrBounds}: a ground
     * reason when both sides are already fully resolved (singleton), else their current
     * bound/cardinality state cited verbatim.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return SetBoundsNogoodConstraint.explainViaGroundOrBounds(getVariables(), domains);
    }
}
