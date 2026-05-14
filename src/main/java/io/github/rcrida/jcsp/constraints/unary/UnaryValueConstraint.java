package io.github.rcrida.jcsp.constraints.unary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryValueConstraint<T> extends UnaryConstraint<T> {
    @NonNull T value;

    @Override
    protected boolean checkValue(@NonNull T value) {
        return Objects.equals(this.value, value);
    }

    @Override
    public String getRelation() {
        return "{(" + value + ")}";
    }
}
