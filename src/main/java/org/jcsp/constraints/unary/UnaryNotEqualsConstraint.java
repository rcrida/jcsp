package org.jcsp.constraints.unary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryNotEqualsConstraint extends UnaryConstraint {
    @NonNull Object value;

    @Override
    public boolean isSatisfiedBy(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        return !Objects.equals(this.value, value);
    }

    @Override
    public String getRelation() {
        return getVariable() + " != " + value;
    }
}
