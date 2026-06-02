package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An n-ary constraint that compares two sequences of variables lexicographically:
 * {@code left <op> right}, where {@code <op>} is typically {@link Operator#LT} (strict
 * lex-less) or {@link Operator#LEQ} (lex-less-or-equal).
 * <p>
 * Comparison proceeds element-by-element; at the first differing position {@code i},
 * {@code operator.compare(sign(left[i] - right[i]), 0)} determines the result.
 * If all positions are equal, {@code operator.compare(0, 0)} applies.
 * For partial assignments any unassigned position is treated optimistically.
 * <p>
 * Both sequences must have the same length. Equivalent to MiniZinc's
 * {@code lex_less(left, right)} and {@code lex_lesseq(left, right)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LexConstraint<T extends Comparable<T>> extends NaryConstraint {
    record VariablePair<T>(Variable<T> left, Variable<T> right) {}

    @Singular private final List<VariablePair<T>> variablePairs;
    @NonNull private final Operator operator;

    public static <T extends Comparable<T>> LexConstraint<T> of(
            @NonNull List<? extends Variable<T>> left,
            @NonNull Operator operator,
            @NonNull List<? extends Variable<T>> right) {
        assert left.size() == right.size() : "LexConstraint requires equal-length sequences";
        val variablePairs = IntStream.range(0, left.size()).mapToObj(i -> new VariablePair<>(left.get(i), right.get(i))).toList();
        return LexConstraint.<T>builder()
                .variables(Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet()))
                .variablePairs(variablePairs)
                .operator(operator)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (VariablePair<T> pair : variablePairs) {
            val lv = assignment.getValue(pair.left);
            val rv = assignment.getValue(pair.right);
            if (lv.isEmpty() || rv.isEmpty()) return true;
            int cmp = lv.get().compareTo(rv.get());
            if (cmp != 0) return operator.compare(Integer.signum(cmp), 0);
        }
        // All positions equal: LEQ/GEQ/EQ → satisfied; LT/GT/NEQ → not satisfied.
        // operator.compare(0, 0) evaluates "0 op 0", which correctly captures this for all operators.
        return operator.compare(0, 0);
    }

    @Override
    public String getRelation() {
        val leftVars = variablePairs.stream().map(VariablePair::left).toList();
        val rightVars = variablePairs.stream().map(VariablePair::right).toList();
        return leftVars.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]"))
                + " " + operator.symbol + " "
                + rightVars.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
