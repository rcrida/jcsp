package io.github.rcrida.jcsp.constraints.unary;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Abstract base class for unary constraints in a constraint satisfaction problem (CSP).
 * A unary constraint applies to a single variable and defines a condition that must be met
 * for an assignment to satisfy the constraint.
 */
@Value
@NonFinal
@SuperBuilder
public abstract class UnaryConstraint<T> implements Constraint {
    @NonNull
    Variable variable;

    @Override
    @SuppressWarnings("unchecked")
    public final boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return assignment.getValue(variable)
                .map(value -> checkValue((T) value))
                .orElse(true);
    }

    /**
     * Checks this constraint against a raw domain value without requiring an intermediate
     * {@link Assignment}. The unchecked cast from {@link Object} to {@code T} is confined here.
     */
    @SuppressWarnings("unchecked")
    public final boolean isSatisfiedByValue(@Nullable Object value) {
        if (value == null) return true;
        return checkValue((T) value);
    }

    protected abstract boolean checkValue(@NonNull T value);

    @Override
    public Set<Variable> getVariables() {
        return Set.of(variable);
    }

    @Override
    public String toString() {
        return "<(" + variable + "), " + getRelation() + ">";
    }
}
