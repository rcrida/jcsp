package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
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
 * A binary constraint enforcing {@code |left ∩ right| op bound} over set variables — e.g. CSPLib's
 * Social Golfers ("no two groups share more than one player across weeks") and Progressive Party
 * ("no two guest boats meet more than once") both reduce to {@code |A ∩ B| <= 1} between every pair
 * of groups/visits drawn from different rounds.
 * <p>
 * Propagation only narrows for {@link Operator#LEQ}/{@link Operator#LT} — the only directions
 * needed by any currently-identified use case, and the only ones with an efficient bounds-consistency
 * algorithm here: once the count of elements already forced common (in both lower bounds) reaches
 * the limit, any further candidate that's already forced into <em>one</em> side must be excluded
 * from the other, since letting it join both would push the count over. A candidate not yet forced
 * into either side can't be soundly resolved this way — which side (if either) it ends up on is a
 * genuine disjunctive choice, not a narrowing — the same bounds-consistency-not-GAC ceiling {@code
 * NValueConstraint} documents for the analogous reason. {@link Operator#EQ}/{@link Operator#GEQ}/
 * {@link Operator#GT}/{@link Operator#NEQ} are left to {@link #isSatisfiedBy} and AC3's generic
 * handling, same as {@link io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint}
 * skips {@code NEQ} — extending this to those operators (e.g. BIBD's {@code EQ} requirement) is
 * deferred until a real instance needs it, rather than built speculatively now.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class IntersectionCardinalityConstraint<E> extends BinaryConstraint<Set<E>, Set<E>> implements Propagatable {
    int bound;
    @NonNull Operator operator;

    public static <E> IntersectionCardinalityConstraint<E> of(@NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right,
                                                                @NonNull Operator operator, int bound) {
        return IntersectionCardinalityConstraint.<E>builder().left(left).right(right).operator(operator).bound(bound).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Set<E> leftValue, @NonNull Set<E> rightValue) {
        long intersectionSize = leftValue.stream().filter(rightValue::contains).count();
        return operator.compare(intersectionSize, (long) bound);
    }

    @Override
    public String getRelation() {
        return "|" + getLeft() + " ∩ " + getRight() + "| " + operator.symbol + " " + bound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.LEQ && operator != Operator.LT) return Optional.of(Map.of());
        if (!(domains.get(getLeft()) instanceof SetBoundedDomain<?>) || !(domains.get(getRight()) instanceof SetBoundedDomain<?>)) {
            return Optional.of(Map.of());
        }
        SetBoundedDomain<E> left = (SetBoundedDomain<E>) domains.get(getLeft());
        SetBoundedDomain<E> right = (SetBoundedDomain<E>) domains.get(getRight());

        Set<E> definiteCommon = new HashSet<>(left.getLowerBound());
        definiteCommon.retainAll(right.getLowerBound());
        int maxAllowed = operator == Operator.LT ? bound - 1 : bound;
        if (definiteCommon.size() > maxAllowed) return Optional.empty();
        if (definiteCommon.size() < maxAllowed) return Optional.of(Map.of());

        // At capacity: no further element may join both sides. Only candidates already forced
        // into exactly one side can be soundly excluded from the other; a candidate still
        // undetermined on both sides is a genuine disjunctive choice, left unresolved.
        Set<E> undeterminedCommon = new HashSet<>(left.getUpperBound());
        undeterminedCommon.retainAll(right.getUpperBound());
        undeterminedCommon.removeAll(definiteCommon);

        Set<E> rightAllowed = new HashSet<>(right.getUpperBound());
        Set<E> leftAllowed = new HashSet<>(left.getUpperBound());
        for (E e : undeterminedCommon) {
            if (left.getLowerBound().contains(e)) rightAllowed.remove(e);
            if (right.getLowerBound().contains(e)) leftAllowed.remove(e);
        }

        SetBoundedDomain<E> newRight = right.withUpperBound(rightAllowed);
        if (newRight.isEmpty()) return Optional.empty();
        SetBoundedDomain<E> newLeft = left.withUpperBound(leftAllowed);
        if (newLeft.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (!newRight.equals(right)) updated.put(getRight(), newRight);
        if (!newLeft.equals(left)) updated.put(getLeft(), newLeft);
        return Optional.of(updated);
    }

    /**
     * Two-tier explanation via {@link SetBoundsNogoodConstraint#explainViaGroundOrBounds} — see
     * {@link SubsetConstraint#explainInfeasible}'s Javadoc for the identical reasoning.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return SetBoundsNogoodConstraint.explainViaGroundOrBounds(getVariables(), domains);
    }
}
