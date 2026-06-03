package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An n-ary constraint that enforces a non-increasing order on a sequence of variables:
 * {@code vars[0] >= vars[1] >= ... >= vars[n-1]}.
 * <p>
 * Only fully assigned consecutive pairs are checked — partially assigned sequences
 * are optimistically satisfied. Equivalent to MiniZinc's {@code decreasing(vars)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DecreasingConstraint<T extends Comparable<T>> extends NaryConstraint implements BinaryDecomposable {
    @NonNull private final List<Variable<T>> orderedVariables;

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> DecreasingConstraint<T> of(
            @NonNull List<? extends Variable<T>> variables) {
        return DecreasingConstraint.<T>builder()
                .variables(variables)
                .orderedVariables((List<Variable<T>>) (List<?>) variables)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (int i = 0; i < orderedVariables.size() - 1; i++) {
            var left  = assignment.getValue(orderedVariables.get(i));
            var right = assignment.getValue(orderedVariables.get(i + 1));
            if (left.isPresent() && right.isPresent() && left.get().compareTo(right.get()) < 0)
                return false;
        }
        return true;
    }

    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        var binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < orderedVariables.size() - 1; i++)
            binaryConstraints.add(BinaryComparatorConstraint.of(orderedVariables.get(i), Operator.GEQ, orderedVariables.get(i + 1)));
        return binaryConstraints;
    }

    @Override
    public String getRelation() {
        return "decreasing";
    }
}
