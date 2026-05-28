package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryNotEqualsConstraint<T> extends UnaryConstraint<T> {
    @NonNull T value;

    public static <T> UnaryNotEqualsConstraint<T> of(@NonNull Variable<T> variable, @NonNull T value) {
        return UnaryNotEqualsConstraint.<T>builder().variable(variable).value(value).build();
    }

    @Override
    protected boolean checkValue(@NonNull T value) {
        return !Objects.equals(this.value, value);
    }

    @Override
    public String getRelation() {
        return getVariable() + " != " + value;
    }
}
