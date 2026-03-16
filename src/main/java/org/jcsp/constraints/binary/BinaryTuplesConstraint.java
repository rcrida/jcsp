package org.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryTuplesConstraint extends BinaryConstraint {
    @Singular
    Set<BinaryTuple> binaryTuples;

    @Override
    public boolean isSatisfiedBy(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return true;
        }
        return binaryTuples.contains(new BinaryTuple(left, right));
    }

    @Override
    public String getRelation() {
        return "{" + binaryTuples.stream().map(BinaryTuple::toString).collect(Collectors.joining(", ")) + "}";
    }
}
