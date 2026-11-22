package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code left^exponent <op> right} for an integer {@code exponent >= 2}, where {@code right} is a
 * variable: the general form of {@link SquareVariableConstraint}, for a product that repeats one
 * factor.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PowerVariableConstraint<N extends Number> extends BinaryConstraint<N, N> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    /**
     * Relative slack added outward to every root bound: {@link Math#pow} can land a root just inside
     * an exact integer ({@code 27^(1/3)} is {@code 3.0000000000000004} or {@code 2.9999999999999996}),
     * and a bound one ulp inside would remove that integer.
     */
    private static final double ROOT_SLACK = 1e-9;

    @Getter private final int exponent;
    @NonNull Operator operator;

    public static <N extends Number> PowerVariableConstraint<N> of(
            @NonNull Variable<N> operand, int exponent, @NonNull Operator operator, @NonNull Variable<N> target) {
        assert exponent >= 2 : "exponent must be at least 2, got " + exponent;
        return PowerVariableConstraint.<N>builder()
                .left(operand).right(target).exponent(exponent).operator(operator).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull N operandValue, @NonNull N targetValue) {
        // + 0.0 turns an odd power's -0.0 into 0.0, which the boxed comparison tells apart.
        return operator.compare(Math.pow(operandValue.doubleValue(), exponent) + 0.0, targetValue.doubleValue());
    }

    @Override
    public String getRelation() {
        return getLeft() + "^" + exponent + " " + operator.symbol + " " + getRight();
    }

    /**
     * Bounds propagation, as {@link SquareVariableConstraint#propagate} does for {@code exponent}
     * 2: {@code right} is raised to the smallest achievable power under {@code LEQ}/{@code EQ} and
     * lowered to the largest under {@code GEQ}/{@code EQ}. An odd power is monotone, so {@code left}
     * is clipped from above by the signed root of {@code right}'s maximum under {@code LEQ}/{@code
     * EQ} and from below by the root of its minimum under {@code GEQ}/{@code EQ}. An even power
     * clips {@code left} to {@code ±root(right.max)} under {@code LEQ}/{@code EQ} only: under
     * {@code GEQ} it excludes a middle band, a disjunction that bounds cannot express.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        Domain<N> leftDomain = (Domain<N>) domains.get(getLeft());
        Domain<N> rightDomain = (Domain<N>) domains.get(getRight());
        double xMin = NumericBounds.min(leftDomain), xMax = NumericBounds.max(leftDomain);
        double targetLo = NumericBounds.min(rightDomain), targetHi = NumericBounds.max(rightDomain);

        boolean even = exponent % 2 == 0;
        double atMin = Math.pow(xMin, exponent), atMax = Math.pow(xMax, exponent);
        double powHi = even ? Math.max(atMin, atMax) : atMax;
        double powLo = !even ? atMin : (xMin <= 0 && xMax >= 0) ? 0 : Math.min(atMin, atMax);

        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;
        if (leqLike && powLo > targetHi) return Optional.empty();
        if (geqLike && powHi < targetLo) return Optional.empty();

        double newTargetLo = leqLike ? Math.max(targetLo, powLo) : targetLo;
        double newTargetHi = geqLike ? Math.min(targetHi, powHi) : targetHi;

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        Optional<Domain<N>> prunedTarget = NumericBounds.narrow(rightDomain, newTargetLo, newTargetHi);
        if (prunedTarget.isPresent()) {
            if (prunedTarget.get().isEmpty()) return Optional.empty();
            updated.put(getRight(), prunedTarget.get());
        }

        double newXMin = xMin, newXMax = xMax;
        if (even) {
            // Only under LEQ/EQ, where newTargetHi >= powLo >= 0, so the root is real.
            if (leqLike) {
                double root = widen(root(newTargetHi));
                newXMin = Math.max(xMin, -root);
                newXMax = Math.min(xMax, root);
            }
        } else {
            if (leqLike) newXMax = Math.min(xMax, widen(root(newTargetHi)));
            if (geqLike) newXMin = Math.max(xMin, -widen(-root(newTargetLo)));
        }
        Optional<Domain<N>> prunedLeft = NumericBounds.narrow(leftDomain, newXMin, newXMax);
        if (prunedLeft.isPresent()) {
            if (prunedLeft.get().isEmpty()) return Optional.empty();
            updated.put(getLeft(), prunedLeft.get());
        }

        return Optional.of(updated);
    }

    /** The real {@code exponent}-th root of {@code value}, signed for an odd exponent. */
    private double root(double value) {
        return Math.signum(value) * Math.pow(Math.abs(value), 1.0 / exponent);
    }

    /** {@code bound} moved up by {@link #ROOT_SLACK}, relative to its magnitude. */
    private static double widen(double bound) {
        return bound + ROOT_SLACK * Math.max(1.0, Math.abs(bound));
    }

    /**
     * As {@link SquareVariableConstraint#explainInfeasible}: the bounds checks read only the two
     * ranges, so citing them is a sound reason.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }
}
