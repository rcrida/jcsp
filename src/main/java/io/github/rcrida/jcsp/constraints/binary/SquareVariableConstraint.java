package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.constraints.unary.SquareConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code left^2 <op> right}, where {@code right} is itself a variable rather than a fixed bound --
 * the variable-target sibling of {@link SquareConstraint}. Unlike {@link
 * io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint}'s own variable-target
 * sibling ({@code AbsoluteDifferenceVariableConstraint}, which needs {@code NaryConstraint} because
 * it has three variables total -- two fixed operands plus a target), this constraint has only two
 * variables total ({@code left} the squared operand, {@code right} the target) and so fits
 * {@link BinaryConstraint} directly, exactly like {@link
 * io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint} itself does for its own
 * two-variable, fixed-bound shape -- {@link BinaryConstraint#isSatisfiedBy(Object, Object)} is
 * abstract, not {@code final}, so there's no "isSatisfiedBy is final" reason to reach for
 * {@code NaryConstraint} here the way there is for {@code UniformNaryConstraint}'s own fixed
 * subclasses.
 * <p>
 * Added for XCSP3's {@code mul(x,x)} self-product intension shape (see {@code
 * Xcsp3CallbackHandler}'s {@code ProductRecognizer}), confirmed via a real corpus instance
 * ({@code LowAutocorrelation-015.xml.lzma}) where {@link
 * io.github.rcrida.jcsp.constraints.nary.ProductVariableConstraint}'s own {@code Set<Variable<N>>}
 * factor representation can't hold {@code x} twice.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SquareVariableConstraint<N extends Number> extends BinaryConstraint<N, N> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull Operator operator;

    public static <N extends Number> SquareVariableConstraint<N> of(
            @NonNull Variable<N> operand, @NonNull Operator operator, @NonNull Variable<N> target) {
        return SquareVariableConstraint.<N>builder().left(operand).right(target).operator(operator).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull N operandValue, @NonNull N targetValue) {
        double squared = operandValue.doubleValue() * operandValue.doubleValue();
        return operator.compare(squared, targetValue.doubleValue());
    }

    @Override
    public String getRelation() {
        return getLeft() + "^2 " + operator.symbol + " " + getRight();
    }

    /**
     * Bounds-consistency propagation for {@code left^2 <op> right}, structured exactly like {@link
     * io.github.rcrida.jcsp.constraints.nary.AbsoluteDifferenceVariableConstraint#propagate}: only
     * {@link Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} propagate. {@code right}
     * is always narrowed to the achievable {@code [sqLo, sqHi]} square range (see {@link
     * SquareConstraint}'s own Javadoc for that derivation); {@code left} is additionally narrowed
     * for {@code LEQ}/{@code EQ} (the decomposition {@code left^2 <= right} implies {@code
     * -sqrt(right.max) <= left <= sqrt(right.max)}), mirroring {@code
     * AbsoluteDifferenceVariableConstraint}'s own conjunctive decomposition for its {@code
     * LEQ}-like case. {@code GEQ} has no such decomposition -- {@code left^2 >= right} excludes an
     * open middle band, a disjunction -- so it only narrows {@code right}, leaving {@code left}
     * untouched, the same tradeoff that class's own {@code GEQ} case accepts. {@code newTargetHi}
     * is not re-checked for non-negativity before the {@code sqrt} below: whenever {@code leqLike}
     * is true and the {@code sqLo > targetHi} infeasibility check above didn't already return,
     * {@code targetHi >= sqLo >= 0} necessarily holds, and {@code newTargetHi = min(targetHi,
     * sqHi)} of two values each {@code >= sqLo} is itself {@code >= sqLo >= 0} -- a real
     * non-negative bound, never a defensive guess.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        Domain<N> leftDomain = (Domain<N>) domains.get(getLeft());
        Domain<N> rightDomain = (Domain<N>) domains.get(getRight());
        double xMin = NumericBounds.min(leftDomain), xMax = NumericBounds.max(leftDomain);
        double targetLo = NumericBounds.min(rightDomain), targetHi = NumericBounds.max(rightDomain);

        double sqHi = Math.max(xMin * xMin, xMax * xMax);
        double sqLo = (xMin <= 0 && xMax >= 0) ? 0 : Math.min(xMin * xMin, xMax * xMax);

        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;
        if (leqLike && sqLo > targetHi) return Optional.empty();
        if (geqLike && sqHi < targetLo) return Optional.empty();

        double newTargetLo = geqLike ? Math.max(targetLo, sqLo) : targetLo;
        double newTargetHi = leqLike ? Math.min(targetHi, sqHi) : targetHi;

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        Optional<Domain<N>> prunedTarget = NumericBounds.narrow(rightDomain, newTargetLo, newTargetHi);
        if (prunedTarget.isPresent()) {
            if (prunedTarget.get().isEmpty()) return Optional.empty();
            updated.put(getRight(), prunedTarget.get());
        }

        if (leqLike) {
            double root = Math.sqrt(newTargetHi);
            double newXMin = Math.max(xMin, -root);
            double newXMax = Math.min(xMax, root);
            Optional<Domain<N>> prunedLeft = NumericBounds.narrow(leftDomain, newXMin, newXMax);
            if (prunedLeft.isPresent()) {
                if (prunedLeft.get().isEmpty()) return Optional.empty();
                updated.put(getLeft(), prunedLeft.get());
            }
        }

        return Optional.of(updated);
    }

    /**
     * {@link #propagate}'s bounds-only infeasibility checks are derived purely from {@code
     * left}/{@code right}'s current bounding ranges, so {@link RangeNogoodConstraint#fromCurrentBounds}
     * is always a sound explanation, falling back to {@link ValueSetNogoodConstraint#fromCurrentState}
     * when a side isn't safely citable as a range -- the same two-step fallback {@link
     * io.github.rcrida.jcsp.constraints.nary.AbsoluteDifferenceVariableConstraint#explainInfeasible}
     * uses.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }
}
