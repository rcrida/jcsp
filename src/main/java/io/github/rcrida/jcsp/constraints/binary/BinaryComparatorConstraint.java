package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.consistency.PropagationResult;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A binary constraint that compares two variables of the same type using an {@link Operator}:
 * {@code left <op> right}, e.g. {@code v1 <= v2} or {@code v1 != v2}.
 * <p>
 * Works with any {@link Comparable} type — not limited to {@link Number} like
 * {@link BinaryOffsetConstraint}. Useful as the binary decomposition of ordering
 * constraints such as {@link io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint}.
 * Implements {@link Propagatable} to narrow bounds via {@link NumericBounds}: {@link BoundedDomain}
 * sides via {@code withBounds}, discrete sides via value deletion — including a plain
 * discrete/discrete pair, as long as its values are actually {@link Number}s (both sides share
 * the same type parameter, so checking one side settles it for both). For a non-numeric
 * {@code Comparable} pair (e.g. ordering {@link String} or enum variables), propagation stays
 * a no-op and the ordering is enforced purely by {@code isSatisfiedBy} plus AC3 during search,
 * since {@link NumericBounds} has no notion of bounds for a type it can't convert to
 * {@code double}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryComparatorConstraint<T extends Comparable<T>> extends BinaryConstraint<T, T> implements Propagatable {
    @NonNull private final Operator operator;

    public static <T extends Comparable<T>> BinaryComparatorConstraint<T> of(
            @NonNull Variable<T> left, @NonNull Operator operator, @NonNull Variable<T> right) {
        return BinaryComparatorConstraint.<T>builder()
                .left(left).operator(operator).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T leftValue, @NonNull T rightValue) {
        return operator.compare(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return getLeft() + " " + operator.symbol + " " + getRight();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        if (operator == Operator.NEQ) return Optional.of(Map.of());
        Domain<?> lDomain = domains.get(getLeft());
        Domain<?> rDomain = domains.get(getRight());
        boolean lBounded = lDomain instanceof BoundedDomain<?>;
        boolean rBounded = rDomain instanceof BoundedDomain<?>;
        // A plain discrete/discrete pair only benefits if its values are actually Numbers —
        // both sides share the same type parameter, so checking one side settles it for both.
        if (!lBounded && !rBounded && !isNumericDiscrete(lDomain)) return Optional.of(Map.of());

        Domain<? extends Number> lNumeric = (Domain<? extends Number>) lDomain;
        Domain<? extends Number> rNumeric = (Domain<? extends Number>) rDomain;
        double lMin = NumericBounds.min(lNumeric);
        double lMax = NumericBounds.max(lNumeric);
        double rMin = NumericBounds.min(rNumeric);
        double rMax = NumericBounds.max(rNumeric);
        double newLMin = lMin, newLMax = lMax, newRMin = rMin, newRMax = rMax;
        if (operator == Operator.LEQ || operator == Operator.LT) {
            newLMax = Math.min(lMax, rMax);
            newRMin = Math.max(rMin, lMin);
        } else if (operator == Operator.GEQ || operator == Operator.GT) {
            newLMin = Math.max(lMin, rMin);
            newRMax = Math.min(rMax, lMax);
        } else { // EQ
            newLMin = newRMin = Math.max(lMin, rMin);
            newLMax = newRMax = Math.min(lMax, rMax);
        }
        if (newLMin > newLMax) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        var prunedL = NumericBounds.narrow(lNumeric, newLMin, newLMax);
        if (prunedL.isPresent()) {
            if (prunedL.get().isEmpty()) return Optional.empty();
            updated.put(getLeft(), prunedL.get());
        }
        var prunedR = NumericBounds.narrow(rNumeric, newRMin, newRMax);
        if (prunedR.isPresent()) {
            if (prunedR.get().isEmpty()) return Optional.empty();
            updated.put(getRight(), prunedR.get());
        }
        return Optional.of(updated);
    }

    /**
     * Whether a plain (non-{@link BoundedDomain}) discrete domain holds {@link Number} values,
     * i.e. whether {@link NumericBounds} can be applied to it at all. Peeking a single value
     * suffices since a {@link DiscreteDomain}'s values are uniformly typed.
     */
    private static boolean isNumericDiscrete(Domain<?> domain) {
        return ((DiscreteDomain<?>) domain).stream().findFirst().orElseThrow() instanceof Number;
    }

    /**
     * When bounds narrowing empties the feasible range, attributes the conflict to whichever
     * side already holds a singleton domain (a value pinned by earlier propagation) — the other
     * side is omitted since no single value can be blamed for it. Empty when neither side is
     * singleton, e.g. two open ranges with no overlap; callers fall back to the full assignment.
     * <p>
     * This is the reference implementation of {@link Propagatable#explainInfeasible}. Its benefit
     * varies by domain-type pairing, since {@code propagate()} only narrows when the pair is
     * numeric (bounded, or discrete with {@link Number} values) — see the class javadoc.
     * <ul>
     *     <li>Discrete/discrete numeric pairs: real benefit, reachable whenever narrowing empties
     *     one side — the same mechanism as {@link BinaryOffsetConstraint}.</li>
     *     <li>Discrete/discrete non-numeric pairs (e.g. ordering {@link String} or enum
     *     variables): no benefit. {@code propagate()} is a no-op for these (delegates entirely
     *     to AC3), so this method's infeasible branch is unreachable.</li>
     *     <li>Bounded/bounded pairs: usually no benefit either. A conflict between two intervals
     *     is typically caught during preprocessing — {@code PropagationFixpointSolver}'s
     *     snap-then-reconverge loop, run once before search — which reports the CSP UNSAT
     *     directly from the solver-decorator chain without ever invoking
     *     {@code MacAndFixpointConflictExplainer} (that only runs inside
     *     {@code DomWdegLubySearch}'s search loop).</li>
     *     <li>Mixed discrete/bounded pairs: the one case with real payoff. By the time search
     *     runs, the bounded side is already snapped to a singleton; if a live discrete variable's
     *     search-time assignment then conflicts with it, MAC wraps that assignment in a singleton
     *     {@code AssignedDomain} before this method runs, so it attributes the conflict to
     *     {@code {discreteVar: assignedValue}} (plus the bounded side) instead of the caller
     *     falling back to the entire accumulated partial assignment — a nogood that prunes that
     *     discrete choice across branches and Luby restarts, not just the one search path.</li>
     * </ul>
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(domains.get(getLeft()), getLeft(), reason);
        Propagatable.addIfSingleton(domains.get(getRight()), getRight(), reason);
        return reason;
    }
}
