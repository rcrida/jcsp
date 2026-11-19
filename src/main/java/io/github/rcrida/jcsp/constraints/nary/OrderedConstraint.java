package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Valuation;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.ComparableBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint generalising {@link IncreasingConstraint}/{@link DecreasingConstraint} to
 * all four ordering operators: {@code vars[0] <op> vars[1] <op> ... <op> vars[n-1]} for
 * {@code op} one of {@link Operator#LT}, {@link Operator#LEQ}, {@link Operator#GEQ}, {@link
 * Operator#GT}. Prefer {@link IncreasingConstraint}/{@link DecreasingConstraint} directly for the
 * common {@code LEQ}/{@code GEQ} cases -- this class exists for callers that need a single
 * {@link io.github.rcrida.jcsp.constraints.Constraint} object parameterised by an ordering
 * operator chosen at runtime (e.g. {@code Xcsp3CallbackHandler}'s {@code ordered} mapping, which
 * needs one object to hand to {@code reifyConstraint}/{@code impliesConstraint} rather than the
 * {@code N-1} separate pairwise constraints a direct decomposition would give).
 * <p>
 * {@link #isSatisfiedBy} and {@link #getAsBinaryConstraints} are exact for all four operators.
 * {@link #propagate} is bounds-consistent for each: {@code LEQ}/{@code GEQ} through {@link
 * IncreasingConstraint}/{@link DecreasingConstraint}'s non-strict chain computation, {@code
 * LT}/{@code GT} through {@link OrderingPropagation#strictlyIncreasingBounds}, so a chain with two
 * equal neighbours fails.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class OrderedConstraint<T extends Comparable<T>> extends NaryConstraint
        implements BinaryDecomposable, Propagatable {
    @NonNull private final List<Variable<T>> orderedVariables;
    @NonNull private final Operator operator;

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> OrderedConstraint<T> of(
            @NonNull List<? extends Variable<T>> variables, @NonNull Operator operator) {
        assert operator == Operator.LT || operator == Operator.LEQ
                || operator == Operator.GEQ || operator == Operator.GT
                : "OrderedConstraint only supports LT/LEQ/GEQ/GT, got: " + operator;
        return OrderedConstraint.<T>builder()
                .variables(variables)
                .orderedVariables((List<Variable<T>>) (List<?>) variables)
                .operator(operator)
                .build();
    }

    private boolean ascending() {
        return operator == Operator.LT || operator == Operator.LEQ;
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Valuation assignment) {
        for (int i = 0; i < orderedVariables.size() - 1; i++) {
            var left = assignment.getValue(orderedVariables.get(i));
            var right = assignment.getValue(orderedVariables.get(i + 1));
            if (left.isPresent() && right.isPresent() && !operator.compare(left.get(), right.get()))
                return false;
        }
        return true;
    }

    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        var binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < orderedVariables.size() - 1; i++)
            binaryConstraints.add(BinaryComparatorConstraint.of(orderedVariables.get(i), operator, orderedVariables.get(i + 1)));
        return binaryConstraints;
    }

    private boolean strict() {
        return operator == Operator.LT || operator == Operator.GT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> chain = ascending() ? orderedVariables : orderedVariables.reversed();
        List<T> newMins;
        List<T> newMaxs;
        if (strict()) {
            var bounds = OrderingPropagation.strictlyIncreasingBounds(chain, domains);
            if (bounds.isEmpty()) return Optional.empty();
            newMins = bounds.get().newMins();
            newMaxs = bounds.get().newMaxs();
        } else {
            var bounds = OrderingPropagation.nonDecreasingBounds(chain, domains);
            newMins = bounds.newMins();
            newMaxs = bounds.newMaxs();
        }
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            T newMin = newMins.get(i);
            T newMax = newMaxs.get(i);
            if (newMin.compareTo(newMax) > 0) return Optional.empty();
            Domain<T> current = (Domain<T>) domains.get(chain.get(i));
            var narrowed = ComparableBounds.narrow(current, newMin, newMax);
            if (narrowed.isPresent()) {
                if (narrowed.get().isEmpty()) return Optional.empty();
                updated.put(chain.get(i), narrowed.get());
            }
        }
        return Optional.of(updated);
    }

    /**
     * Same reasoning as {@link IncreasingConstraint#explainInfeasible}, over whichever direction
     * {@link #ascending()} selects. A strict chain's infeasibility can also rest on the variables
     * between the two ends, so it is explained only once every variable is a single value.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> chain = ascending() ? orderedVariables : orderedVariables.reversed();
        if (strict()) {
            if (OrderingPropagation.strictlyIncreasingBounds(chain, domains).isPresent()) return Optional.empty();
            var reason = Propagatable.allSingletonReason(orderedVariables, domains);
            return reason.size() == orderedVariables.size() ? GroundNogoodConstraint.fromReason(reason) : Optional.empty();
        }
        var bounds = OrderingPropagation.nonDecreasingBounds(chain, domains);
        for (int i = 0; i < chain.size(); i++) {
            if (bounds.newMins().get(i).compareTo(bounds.newMaxs().get(i)) > 0) {
                var p = chain.get(bounds.minSource()[i]);
                var q = chain.get(bounds.maxSource()[i]);
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(List.of(p, q), domains));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "ordered(" + operator.symbol + ")";
    }
}
