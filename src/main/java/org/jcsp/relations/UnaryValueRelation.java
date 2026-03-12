package org.jcsp.relations;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuperBuilder
public class UnaryValueRelation extends UnaryRelation {
    @NonNull
    Object value;

    @Override
    public boolean isSatisfied(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        return Objects.equals(this.value, value);
    }

    @Override
    public String toString() {
        return "{(" + value + ")}";
    }
}
