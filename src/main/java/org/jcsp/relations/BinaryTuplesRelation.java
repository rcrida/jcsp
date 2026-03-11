package org.jcsp.relations;

import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
public class BinaryTuplesRelation extends BinaryRelation {
    @Singular
    Set<BinaryTuple> binaryTuples;

    @Override
    public boolean isSatisfied(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return false;
        }
        return binaryTuples.contains(new BinaryTuple(left, right));
    }

    @Override
    public String toString() {
        return "{" + binaryTuples.stream().map(BinaryTuple::toString).collect(Collectors.joining(", ")) + "}";
    }
}
