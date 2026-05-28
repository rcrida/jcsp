package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.function.BiPredicate;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a pair of variables and evaluates a given predicate to determine satisfaction.
 * This allows for defining complex relationships and dependencies between variables.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryPredicateConstraint<L, R> extends BinaryConstraint<L, R> {
    @NonNull BiPredicate<L, R> biPredicate;

    public static <L, R> BinaryPredicateConstraint<L, R> of(@NonNull Variable<L> left, @NonNull Variable<R> right,
                                                            @NonNull BiPredicate<L, R> predicate) {
        return BinaryPredicateConstraint.<L, R>builder().left(left).right(right).biPredicate(predicate).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull L leftValue, @NonNull R rightValue) {
        return biPredicate.test(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return biPredicate.toString();
    }
}
