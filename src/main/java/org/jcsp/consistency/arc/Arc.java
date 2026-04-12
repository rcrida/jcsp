package org.jcsp.consistency.arc;

import lombok.Value;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

/**
 * Represents a directed edge between two variables.
 */
@Value
public class Arc {
    Variable from;
    Variable to;

    public static Arc of(@NonNull Variable from, @NonNull Variable to) {
        return new Arc(from, to);
    }

    public Arc(@NonNull Variable from, @NonNull Variable to) {
        assert from != to;
        this.from = from;
        this.to = to;
    }

    public Assignment toAssignment(@NonNull Object fromValue, @NonNull Object toValue) {
        return Assignment.builder()
                .value(from, fromValue)
                .value(to, toValue)
                .build();
    }

    @Override
    public String toString() {
        return "(" + from + " -> " + to + ")";
    }
}
