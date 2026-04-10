package org.jcsp.constraints.binary;

import lombok.Value;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

@Value
public class Arc {
    Variable left;
    Variable right;

    public Arc(@NonNull Variable left, @NonNull Variable right) {
        assert left != right;
        this.left = left;
        this.right = right;
    }

    public Assignment toAssignment(@NonNull Object leftValue, @NonNull Object rightValue) {
        return Assignment.builder()
                .value(left, leftValue)
                .value(right, rightValue)
                .build();
    }

    @Override
    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}
