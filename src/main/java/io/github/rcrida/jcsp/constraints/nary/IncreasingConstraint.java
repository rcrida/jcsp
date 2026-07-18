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
 * An n-ary constraint that enforces a non-decreasing order on a sequence of variables:
 * {@code vars[0] <= vars[1] <= ... <= vars[n-1]}.
 * <p>
 * Only fully assigned consecutive pairs are checked — partially assigned sequences
 * are optimistically satisfied. Equivalent to MiniZinc's {@code increasing(vars)}.
 * <p>
 * Implements {@link Propagatable} directly rather than relying solely on its {@link
 * BinaryDecomposable} pairs, which only ever reach {@link io.github.rcrida.jcsp.consistency.arc.AC3}
 * (skipping non-{@code DiscreteDomain} arcs entirely, so a {@link io.github.rcrida.jcsp.domains.BoundedDomain}
 * chain previously got no propagation at all). {@code propagate()} computes bounds consistency for
 * the whole chain in one pass each direction via {@link OrderingPropagation}, generic over {@code T}
 * itself (not narrowed to {@link Number} the way {@link BinaryComparatorConstraint} is) — since
 * {@link io.github.rcrida.jcsp.domains.BoundedDomain#getMin}/{@code getMax}/{@code withBounds} are
 * already {@code T}-typed, no {@code double} conversion is needed, so even a non-numeric chain (e.g.
 * ordering {@link String}/enum variables) gets real propagation.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class IncreasingConstraint<T extends Comparable<T>> extends NaryConstraint
        implements BinaryDecomposable, Propagatable {
    @NonNull private final List<Variable<T>> orderedVariables;

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> IncreasingConstraint<T> of(
            @NonNull List<? extends Variable<T>> variables) {
        return IncreasingConstraint.<T>builder()
                .variables(variables)
                .orderedVariables((List<Variable<T>>) (List<?>) variables)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (int i = 0; i < orderedVariables.size() - 1; i++) {
            var left  = assignment.getValue(orderedVariables.get(i));
            var right = assignment.getValue(orderedVariables.get(i + 1));
            if (left.isPresent() && right.isPresent() && left.get().compareTo(right.get()) > 0)
                return false;
        }
        return true;
    }

    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        var binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < orderedVariables.size() - 1; i++)
            binaryConstraints.add(BinaryComparatorConstraint.of(orderedVariables.get(i), Operator.LEQ, orderedVariables.get(i + 1)));
        return binaryConstraints;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        var bounds = OrderingPropagation.nonDecreasingBounds(orderedVariables, domains);
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < orderedVariables.size(); i++) {
            T newMin = bounds.newMins().get(i);
            T newMax = bounds.newMaxs().get(i);
            if (newMin.compareTo(newMax) > 0) return Optional.empty();
            Domain<T> current = (Domain<T>) domains.get(orderedVariables.get(i));
            var narrowed = OrderingPropagation.narrow(current, newMin, newMax);
            if (narrowed.isPresent()) {
                if (narrowed.get().isEmpty()) return Optional.empty();
                updated.put(orderedVariables.get(i), narrowed.get());
            }
        }
        return Optional.of(updated);
    }

    /**
     * When bounds narrowing empties a position's feasible range, the violation traces back to a
     * (possibly non-adjacent) pair {@code v[p] <= v[q]} with {@code p < q} — implied purely by
     * transitivity, sound regardless of the values of every variable strictly between them.
     * Attributed via {@link Propagatable#allSingletonReason} over just that pair: both must be
     * singleton to cite concrete values, since citing only one side's value wouldn't by itself
     * prove the pair violates the ordering.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        var bounds = OrderingPropagation.nonDecreasingBounds(orderedVariables, domains);
        for (int i = 0; i < orderedVariables.size(); i++) {
            if (bounds.newMins().get(i).compareTo(bounds.newMaxs().get(i)) > 0) {
                var p = orderedVariables.get(bounds.minSource()[i]);
                var q = orderedVariables.get(bounds.maxSource()[i]);
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(List.of(p, q), domains));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "increasing";
    }
}
