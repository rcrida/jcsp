package io.github.rcrida.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a binary constraint defined by a set of permitted binary tuples.
 * This constraint specifies a set of allowed (left, right) value pairs, and it is
 * satisfied if the values of the variables associated with the left and right sides
 * of the constraint match one of these predefined pairs.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryTuplesConstraint extends BinaryConstraint<Object, Object> {
    @Singular
    Set<BinaryTuple> binaryTuples;

    @Override
    public boolean isSatisfiedBy(@NonNull Object left, @NonNull Object right) {
        return binaryTuples.contains(new BinaryTuple(left, right));
    }

    @Override
    public String getRelation() {
        return "{" + binaryTuples.stream().map(BinaryTuple::toString).collect(Collectors.joining(", ")) + "}";
    }
}
