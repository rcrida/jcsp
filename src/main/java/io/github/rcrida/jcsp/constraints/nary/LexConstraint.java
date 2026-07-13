package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
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
     * The first position not already forced equal (both domains singleton with the same value),
     * found by a left-to-right scan shared by {@link #propagate} and {@link #explainInfeasible} —
     * for {@link Operator#GEQ}/{@link Operator#GT} the pair's roles are swapped so it's always
     * {@code lesser <op> greater}. Empty when every position is forced equal.
     */
    private record UndecidedPosition<T>(Variable<T> lesser, Variable<T> greater, boolean strictHere) {}

    @SuppressWarnings("unchecked")
    private Optional<UndecidedPosition<T>> firstUndecidedPosition(Map<Variable<?>, Domain<?>> domains,
                                                                   boolean swapped, boolean strict) {
        for (int i = 0; i < variablePairs.size(); i++) {
            VariablePair<T> pair = variablePairs.get(i);
            Variable<T> lesser  = swapped ? pair.right() : pair.left();
            Variable<T> greater = swapped ? pair.left()  : pair.right();
            Domain<T> lesserDom  = (Domain<T>) domains.get(lesser);
            Domain<T> greaterDom = (Domain<T>) domains.get(greater);

            if (lesserDom.isSingleton() && greaterDom.isSingleton()
                    && lesserDom.singleValue().equals(greaterDom.singleValue())) {
                continue;
            }

            boolean strictHere = strict && i == variablePairs.size() - 1;
            return Optional.of(new UndecidedPosition<>(lesser, greater, strictHere));
        }
        return Optional.empty();
    }

    /**
     * At the first undecided position, {@code lesser <= greater} is a necessary condition (the
     * lexicographic order is decided here or later, never by an earlier position being violated),
     * so values outside the other side's range are pruned. At the final position, a strict
     * operator ({@link Operator#LT}/{@link Operator#GT}) tightens this to {@code lesser < greater}.
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

        var position = firstUndecidedPosition(domains, swapped, strict);
        if (position.isEmpty()) {
            return operator.compare(0, 0) ? Optional.of(Map.of()) : Optional.empty();
        }

        UndecidedPosition<T> p = position.get();
        Domain<T> lesserDom  = (Domain<T>) domains.get(p.lesser());
        Domain<T> greaterDom = (Domain<T>) domains.get(p.greater());
        T greaterMax = domainMax(greaterDom);
        T lesserMin  = domainMin(lesserDom);

        Domain<T> newLesserDom  = clipUpper(lesserDom,  greaterMax, p.strictHere());
        Domain<T> newGreaterDom = clipLower(greaterDom, lesserMin,  p.strictHere());
        if (newLesserDom.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (lesserDom  instanceof BoundedDomain<?> ? newLesserDom  != lesserDom  : newLesserDom.size()  != lesserDom.size())  updated.put(p.lesser(),  newLesserDom);
        if (greaterDom instanceof BoundedDomain<?> ? newGreaterDom != greaterDom : newGreaterDom.size() != greaterDom.size()) updated.put(p.greater(), newGreaterDom);
        return Optional.of(updated);
    }

    /**
     * On infeasibility, finds the same failing point via the same {@link #firstUndecidedPosition}
     * scan {@link #propagate} uses (no domain threading needed: {@code propagate} returns as soon
     * as it finds the first non-forced-equal position, so at most one position is ever examined),
     * then attributes the conflict to every variable involved:
     * <ul>
     *   <li><b>Out-of-order position</b>: {@code lesserMin > greaterMax} (or {@code >=} when
     *       strict) depends on <em>both</em> {@code lesser} and {@code greater}'s current bounds
     *       jointly — neither alone proves the position fails, since a wider domain on the omitted
     *       side could still satisfy the ordering. Both must be singleton before citing either,
     *       via {@link Propagatable#allSingletonReason} — the same lesson as
     *       {@link DiffnConstraint}'s joint mandatory-overlap condition.</li>
     *   <li><b>Every position forced equal</b>: reaching the final {@code 0 <op> 0} check requires
     *       every position's pair to already be singleton-and-equal (that is the loop's own
     *       {@code continue} condition), so citing every variable across every position is always
     *       available here — {@code allSingletonReason} degrades to a plain confirmation, never to
     *       {@code Map.of()}.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.empty();
        }

        boolean swapped = operator == Operator.GEQ || operator == Operator.GT;
        boolean strict = operator == Operator.LT || operator == Operator.GT;

        var position = firstUndecidedPosition(domains, swapped, strict);
        if (position.isEmpty()) {
            if (operator.compare(0, 0)) return Optional.empty();
            List<Variable<T>> allVars = variablePairs.stream()
                    .flatMap(pair -> Stream.of(pair.left(), pair.right()))
                    .toList();
            return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(allVars, domains));
        }

        UndecidedPosition<T> p = position.get();
        Domain<T> lesserDom  = (Domain<T>) domains.get(p.lesser());
        Domain<T> greaterDom = (Domain<T>) domains.get(p.greater());
        T greaterMax = domainMax(greaterDom);
        T lesserMin  = domainMin(lesserDom);
        boolean infeasible = p.strictHere()
                ? lesserMin.compareTo(greaterMax) >= 0
                : lesserMin.compareTo(greaterMax) > 0;
        if (!infeasible) return Optional.empty(); // propagate() would narrow, not fail, at this position
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(List.of(p.lesser(), p.greater()), domains));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T domainMax(Domain<T> domain) {
        if (domain instanceof BoundedDomain<?> bd) return (T) bd.getMax();
        return ((DiscreteDomain<T>) domain).stream().max(Comparator.naturalOrder()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T domainMin(Domain<T> domain) {
        if (domain instanceof BoundedDomain<?> bd) return (T) bd.getMin();
        return ((DiscreteDomain<T>) domain).stream().min(Comparator.naturalOrder()).orElseThrow();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> Domain<T> clipUpper(Domain<T> domain, T max, boolean strict) {
        if (domain instanceof BoundedDomain<?> bd) {
            double newMax = ((Number) max).doubleValue();
            if (newMax >= bd.getMax().doubleValue()) return domain;
            BoundedDomain raw = bd;
            return (Domain<T>) raw.withBounds(bd.getMin().doubleValue(), newMax);
        }
        Predicate<T> keep = strict ? v -> v.compareTo(max) < 0 : v -> v.compareTo(max) <= 0;
        return prune((DiscreteDomain<T>) domain, keep);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> Domain<T> clipLower(Domain<T> domain, T min, boolean strict) {
        if (domain instanceof BoundedDomain<?> bd) {
            double newMin = ((Number) min).doubleValue();
            if (newMin <= bd.getMin().doubleValue()) return domain;
            BoundedDomain raw = bd;
            return (Domain<T>) raw.withBounds(newMin, bd.getMax().doubleValue());
        }
        Predicate<T> keep = strict ? v -> v.compareTo(min) > 0 : v -> v.compareTo(min) >= 0;
        return prune((DiscreteDomain<T>) domain, keep);
    }

    private static <T> DiscreteDomain<T> prune(DiscreteDomain<T> domain, Predicate<T> keep) {
        DiscreteDomain.Builder<T> builder = domain.toBuilder();
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
