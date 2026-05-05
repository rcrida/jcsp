package io.github.rcrida.jcsp.constraints.binary;

import org.jspecify.annotations.NonNull;

public record BinaryTuple(@NonNull Object left, @NonNull Object right) {
    public static BinaryTuple of(@NonNull Object left, @NonNull Object right) {
        return new BinaryTuple(left, right);
    }

    @Override
    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}
