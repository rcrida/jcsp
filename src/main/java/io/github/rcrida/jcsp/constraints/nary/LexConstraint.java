package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
public class LexConstraint<T extends Comparable<T>> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.LT, Operator.LEQ, Operator.GT, Operator.GEQ);

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

    /**
     * Scans positions left to right for the first pair not already forced equal (both domains
     * singletons with the same value). For {@link Operator#GEQ}/{@link Operator#GT}, the pair's
     * roles are swapped so the comparison is always expressed as {@code lesser <op> greater}.
     * <p>
     * At that position, {@code lesser <= greater} is a necessary condition (the lexicographic
     * order is decided here or later, never by an earlier position being violated), so values
     * outside the other side's range are pruned. At the final position, a strict operator
     * ({@link Operator#LT}/{@link Operator#GT}) tightens this to {@code lesser < greater}.
     * If every position is forced equal, the constraint reduces to {@code 0 <op> 0}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        boolean swapped = operator == Operator.GEQ || operator == Operator.GT;
        boolean strict = operator == Operator.LT || operator == Operator.GT;

        for (int i = 0; i < variablePairs.size(); i++) {
            VariablePair<T> pair = variablePairs.get(i);
            Variable<T> lesser  = swapped ? pair.right() : pair.left();
            Variable<T> greater = swapped ? pair.left()  : pair.right();
            Domain<T> lesserDom  = (Domain<T>) domains.get(lesser);
            Domain<T> greaterDom = (Domain<T>) domains.get(greater);

            if (lesserDom.size() == 1 && greaterDom.size() == 1
                    && lesserDom.toList().get(0).equals(greaterDom.toList().get(0))) {
                continue;
            }

            boolean strictHere = strict && i == variablePairs.size() - 1;
            T greaterMax = greaterDom.stream().max(Comparator.naturalOrder()).orElseThrow();
            T lesserMin  = lesserDom.stream().min(Comparator.naturalOrder()).orElseThrow();

            Predicate<T> keepLesser  = strictHere ? v -> v.compareTo(greaterMax) < 0 : v -> v.compareTo(greaterMax) <= 0;
            Predicate<T> keepGreater = strictHere ? v -> v.compareTo(lesserMin) > 0  : v -> v.compareTo(lesserMin) >= 0;

            Domain<T> newLesserDom  = prune(lesserDom, keepLesser);
            Domain<T> newGreaterDom = prune(greaterDom, keepGreater);
            if (newLesserDom.isEmpty()) return Optional.empty();

            Map<Variable<?>, Domain<?>> updated = new HashMap<>();
            if (newLesserDom.size() != lesserDom.size()) updated.put(lesser, newLesserDom);
            if (newGreaterDom.size() != greaterDom.size()) updated.put(greater, newGreaterDom);
            return Optional.of(updated);
        }

        return operator.compare(0, 0) ? Optional.of(Map.of()) : Optional.empty();
    }

    private static <T> Domain<T> prune(Domain<T> domain, Predicate<T> keep) {
        Domain.Builder<T> builder = domain.toBuilder();
        for (T v : domain.toList()) if (!keep.test(v)) builder.delete(v);
        return builder.build();
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
