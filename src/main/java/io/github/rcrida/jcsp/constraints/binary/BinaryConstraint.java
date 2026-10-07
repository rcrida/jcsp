package io.github.rcrida.jcsp.constraints.binary;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Set;

/**
 * Represents a binary constraint in a constraint satisfaction problem (CSP).
 * A binary constraint defines a condition or restriction that involves two variables.
 * It specifies the relationship between the values of the left and right variables
 * that must be satisfied in order for the constraint to hold.
 */
@Value
@NonFinal
@SuperBuilder
public abstract class BinaryConstraint<L, R> implements Constraint {
    @NonNull Variable<L> left;
    @NonNull Variable<R> right;

    @Override
    public final boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return assignment.getValue(left)
                .flatMap(leftValue -> assignment.getValue(right)
                        .map(rightValue -> isSatisfiedBy(leftValue, rightValue)))
                .orElse(true);
    }

    public Variable<?> getNeighbour(@NonNull Variable<?> variable) {
        assert variable == left || variable == right;
        return variable == left ? right : left;
    }

    public abstract boolean isSatisfiedBy(@NonNull L leftValue, @NonNull R rightValue);

    /**
     * Checks satisfaction directly from two raw values keyed by {@code arc}'s own endpoints,
     * without constructing an {@link Assignment} — {@code arc} is assumed to be one of this
     * constraint's own two arcs, i.e. {@code {arc.getFrom(), arc.getTo()}
     * == {left, right}} in some order, so which of {@code fromValue}/{@code toValue} is the left
     * vs. right value can be determined directly rather than needing an {@link Assignment} to look
     * them up by variable. Exists for {@link io.github.rcrida.jcsp.consistency.arc.AC3#revise},
     * which checks every value pair in a domain product during arc revision — profiling found
     * building a fresh {@link Assignment} (with its own {@code @Singular} map and a new {@link
     * io.github.rcrida.jcsp.assignments.Statistics} instance) per pair to be the dominant cost there.
     */
    public boolean isSatisfiedByArcValues(@NonNull Arc arc, @NonNull Object fromValue, @NonNull Object toValue) {
        return isSatisfiedByArcValues(isArcFromLeft(arc), fromValue, toValue);
    }

    /**
     * Whether {@code arc} runs from this constraint's own left variable, so that a {@code
     * (fromValue, toValue)} pair taken along it is already in left-to-right order.
     * <p>
     * Depends only on the arc, never on the values, so {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3#revise} resolves it once per arc revision and
     * hands the answer to {@link #isSatisfiedByArcValues(boolean, Object, Object)} for every pair
     * in the domain product. Re-deriving it per pair, as the {@link Arc}-taking overload above
     * does, costs a {@link io.github.rcrida.jcsp.variables.Variable} comparison -- which is a
     * comparison of their names -- inside the innermost loop of arc consistency.
     */
    public boolean isArcFromLeft(@NonNull Arc arc) {
        return arc.getFrom().equals(left);
    }

    /**
     * {@link #isSatisfiedByArcValues(Arc, Object, Object)} with the arc's orientation already
     * resolved by {@link #isArcFromLeft}.
     */
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedByArcValues(boolean arcFromLeft, @NonNull Object fromValue, @NonNull Object toValue) {
        return arcFromLeft
                ? isSatisfiedBy((L) fromValue, (R) toValue)
                : isSatisfiedBy((L) toValue, (R) fromValue);
    }

    /**
     * Whether revising an arc against {@code toValues} -- the to-side domain's values -- could
     * delete anything from the from-side domain at all. Checked once per arc revision by {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3#revise}, which skips its whole {@code O(|D_i| *
     * |D_j|)} scan when this returns {@code false}; this default returns {@code true}, so a
     * constraint type that can't decide cheaply pays nothing but one virtual call.
     * <p>
     * An override must be <em>conservative</em>: {@code false} asserts that every from-value has a
     * support in {@code toValues}, so returning it wrongly silently loses propagation. In
     * particular it must return {@code true} whenever {@code toValues} is empty, since then no
     * from-value has a support and revision wipes the from-side domain out. Overriding is only
     * worthwhile for a relation whose support condition is decidable from {@code toValues} alone
     * -- see {@link BinaryNotEqualsConstraint#mayPruneAlongArc}, the one such type here.
     * <p>
     * {@code arcFromLeft} is {@link #isArcFromLeft}'s already-resolved answer, so an asymmetric
     * relation can tell which side {@code toValues} belongs to.
     */
    public boolean mayPruneAlongArc(boolean arcFromLeft, @NonNull Collection<?> toValues) {
        return true;
    }

    @Override
    public Set<Variable<?>> getVariables() {
        return Set.of(left, right);
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + getRelation() + ">";
    }
}
