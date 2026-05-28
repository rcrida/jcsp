package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Binary form of a fully reified unary constraint: {@code indicator <-> body(variable)}.
 * Enables AC3 arc propagation between the indicator and the body's variable — e.g.
 * fixing {@code variable} to the body's accepted value forces {@code indicator = true},
 * and fixing {@code indicator = false} removes that value from {@code variable}'s domain.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryReifiedUnaryConstraint<T> extends BinaryConstraint<Boolean, T> {
    @NonNull UnaryConstraint<T> body;

    public static <T> BinaryReifiedUnaryConstraint<T> of(@NonNull Variable<Boolean> indicator, @NonNull UnaryConstraint<T> body) {
        return BinaryReifiedUnaryConstraint.<T>builder().left(indicator).right(body.getVariable()).body(body).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Boolean indicator, @NonNull T value) {
        return indicator == body.isSatisfiedByValue(value);
    }

    @Override
    public String getRelation() {
        return getLeft() + " <-> (" + body.getRelation() + ")";
    }
}
