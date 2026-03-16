package org.jcsp.constraints.unary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryValueConstraint extends UnaryConstraint {
    public static UnaryValueConstraint of(@NonNull Variable variable, @NonNull Object value) {
        return builder().variable(variable).value(value).build();
    }

    @NonNull
    Object value;

    @Override
    public boolean isSatisfiedBy(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        return Objects.equals(this.value, value);
    }

    @Override
    public String getRelation() {
        return "{(" + value + ")}";
    }
}
