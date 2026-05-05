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
public abstract class BinaryConstraint implements Constraint {
    @NonNull Variable left;
    @NonNull Variable right;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return assignment.getValue(left)
                .flatMap(leftValue -> assignment.getValue(right)
                        .map(rightValue -> isSatisfiedBy(leftValue, rightValue)))
                .orElse(true);
    }

    public Variable getNeighbour(@NonNull Variable variable) {
        assert variable == left || variable == right;
        return variable == left ? right : left;
    }

    public abstract boolean isSatisfiedBy(@NonNull Object leftValue, @NonNull Object rightValue);

    @Override
    public Set<Variable> getVariables() {
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
