package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
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
 * An n-ary constraint that enforces a non-increasing order on a sequence of variables:
 * {@code vars[0] >= vars[1] >= ... >= vars[n-1]}.
 * <p>
 * Only fully assigned consecutive pairs are checked — partially assigned sequences
 * are optimistically satisfied. Equivalent to MiniZinc's {@code decreasing(vars)}.
 * <p>
 * Implements {@link Propagatable} directly, same rationale as {@link IncreasingConstraint}. {@code
 * v[0] >= v[1] >= ... >= v[n-1]} holds iff the reversed list is non-decreasing, so {@code
 * propagate()}/{@code explainInfeasible()} simply delegate to {@link OrderingPropagation} over
 * {@code orderedVariables.reversed()} rather than duplicating the bounds computation mirrored.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DecreasingConstraint<T extends Comparable<T>> extends NaryConstraint
        implements BinaryDecomposable, Propagatable {
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
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> reversed = orderedVariables.reversed();
        var bounds = OrderingPropagation.nonDecreasingBounds(reversed, domains);
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < reversed.size(); i++) {
            T newMin = bounds.newMins().get(i);
            T newMax = bounds.newMaxs().get(i);
            if (newMin.compareTo(newMax) > 0) return Optional.empty();
            Domain<T> current = (Domain<T>) domains.get(reversed.get(i));
            var narrowed = OrderingPropagation.narrow(current, newMin, newMax);
            if (narrowed.isPresent()) {
                if (narrowed.get().isEmpty()) return Optional.empty();
                updated.put(reversed.get(i), narrowed.get());
            }
        }
        return Optional.of(updated);
    }

    /**
     * Same reasoning as {@link IncreasingConstraint#explainInfeasible}, mirrored over the reversed
     * (non-decreasing) variable order.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> reversed = orderedVariables.reversed();
        var bounds = OrderingPropagation.nonDecreasingBounds(reversed, domains);
        for (int i = 0; i < reversed.size(); i++) {
            if (bounds.newMins().get(i).compareTo(bounds.newMaxs().get(i)) > 0) {
                var p = reversed.get(bounds.minSource()[i]);
                var q = reversed.get(bounds.maxSource()[i]);
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(List.of(p, q), domains));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "decreasing";
    }
}
