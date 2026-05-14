package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Abstract base for n-ary constraints where all variables share the same value type {@code T}.
 * Provides a typed {@link #isSatisfiedByValues(Collection)} hook, confining unchecked casts to a
 * single bridge method and freeing subclass implementations from working with raw {@link Object}.
 * <p>
 * Contrast with {@link NaryConstraint}, which is for heterogeneous constraints such as
 * {@link PredicateConstraint} whose variables may carry different value types.
 */
@SuperBuilder
public abstract class UniformNaryConstraint<T> extends NaryConstraint {

    @Override
    @SuppressWarnings("unchecked")
    public final boolean isSatisfiedBy(@NonNull Assignment assignment) {
        Collection<T> values = (Collection<T>) (Collection<?>)
                assignment.extractPartialAssignment(getVariables()).getValues().values();
        return isSatisfiedByValues(values);
    }

    /**
     * Checks whether the constraint is satisfied given the typed values of all assigned variables.
     * Variables not yet present in the assignment are omitted from the collection.
     */
    protected abstract boolean isSatisfiedByValues(@NonNull Collection<T> values);
}
