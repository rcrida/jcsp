package io.github.rcrida.jcsp.constraints.unary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/**
 * Represents a unary constraint that evaluates a typed {@link Predicate} against the value
 * of a single variable. Suitable for use with {@link io.github.rcrida.jcsp.domains.Domain}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryPredicateConstraint<T> extends UnaryConstraint<T> {
    @NonNull Predicate<T> predicate;

    @Override
    protected boolean checkValue(@NonNull T value) {
        return predicate.test(value);
    }

    @Override
    public String getRelation() {
        return predicate.toString();
    }
}
