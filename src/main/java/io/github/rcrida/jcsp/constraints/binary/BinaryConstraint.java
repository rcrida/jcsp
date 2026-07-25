package io.github.rcrida.jcsp.constraints.binary;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.stream.Stream;

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
     * constraint's own two arcs (see {@link #getArcs}), i.e. {@code {arc.getFrom(), arc.getTo()}
     * == {left, right}} in some order, so which of {@code fromValue}/{@code toValue} is the left
     * vs. right value can be determined directly rather than needing an {@code Assignment} to look
     * them up by variable. Exists for {@link io.github.rcrida.jcsp.consistency.arc.AC3#revise},
     * which checks every value pair in a domain product during arc revision — profiling found
     * building a fresh {@code Assignment} (with its own {@code @Singular} map and a new {@code
     * Statistics} instance) per pair to be the dominant cost there.
     */
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedByArcValues(@NonNull Arc arc, @NonNull Object fromValue, @NonNull Object toValue) {
        return arc.getFrom().equals(left)
                ? isSatisfiedBy((L) fromValue, (R) toValue)
                : isSatisfiedBy((L) toValue, (R) fromValue);
    }

    @Override
    public Set<Variable<?>> getVariables() {
        return Set.of(left, right);
    }

    public Stream<Arc> getArcs() {
        return Stream.of(Arc.of(left, right), Arc.of(right, left));
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + getRelation() + ">";
    }
}
