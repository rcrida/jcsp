package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code |left - right| <op> target}, where {@link #target} is itself a variable rather than a
 * fixed bound -- the {@code dist(a,b)} shape XCSP3 intension trees use, mirroring {@link
 * MaxVariableConstraint}/{@link MinVariableConstraint}'s own "variable-target sibling" shape.
 * Extends {@link NaryConstraint} directly (not {@link UniformNaryConstraint}, whose {@code
 * isSatisfiedBy} is {@code final}) for the same reason every other variable-target sibling in this
 * package does. Unlike {@link MaxVariableConstraint}/{@link MinVariableConstraint}, {@link #left}/
 * {@link #right} are two fixed operands, not an arbitrary-size set -- {@code dist} is inherently
 * binary -- but {@link #target} being a genuine third variable still rules out a plain {@link
 * io.github.rcrida.jcsp.constraints.binary.BinaryConstraint} (exactly two variables) the way {@link
 * io.github.rcrida.jcsp.constraints.binary.DivisionConstraint}'s fixed-bound sibling uses.
 * <p>
 * Added for XCSP3's {@code dist(a,b)} intension shape (see {@code
 * Xcsp3CallbackHandler#recognizeDistanceOfPair}), which occurs in the bundled competition corpus
 * two ways: directly, {@code eq(dist(a,b), c)} (c a plain variable), and via a two-auxiliary
 * decomposition for comparing two distances against each other, {@code ne(dist(a,b), dist(c,d))} --
 * by far the more common shape.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AbsoluteDifferenceVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @Getter @NonNull private final Variable<N> left;
    @Getter @NonNull private final Variable<N> right;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final Variable<N> target;

    public static <N extends Number> AbsoluteDifferenceVariableConstraint<N> of(
            @NonNull Variable<N> left, @NonNull Variable<N> right, @NonNull Operator operator, @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>();
        allVars.add(left);
        allVars.add(right);
        allVars.add(target);
        return AbsoluteDifferenceVariableConstraint.<N>builder()
                .variables(allVars)
                .left(left).right(right).operator(operator).target(target)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        double diff = Math.abs(assignment.getValue(left).orElseThrow().doubleValue()
                - assignment.getValue(right).orElseThrow().doubleValue());
        double targetValue = assignment.getValue(target).orElseThrow().doubleValue();
        return operator.compare(diff, targetValue);
    }

    /**
     * Bounds-consistency propagation for {@code |left - right| <op> target}. Only {@link
     * Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} propagate, matching every other
     * target-comparing propagator in this codebase.
     * <p>
     * The achievable range of {@code left - right} over the current box is {@code [diffLo, diffHi]}
     * ({@code diffLo = left.min - right.max}, {@code diffHi = left.max - right.min}). From there,
     * the achievable range of {@code |left - right|} is {@code [dLo, dHi]}: {@code dHi = max(|
     * diffLo|, |diffHi|)}, and {@code dLo} is {@code 0} when the interval {@code [diffLo, diffHi]}
     * straddles zero (some choice of left/right makes them equal), otherwise the smaller of {@code
     * |diffLo|}/{@code |diffHi|} (the closest the two sides can get). This mirrors {@link
     * MaxVariableConstraint}/{@link MinVariableConstraint}'s own {@code [mLo, mHi]} reasoning, just
     * computed from the combined {@code left - right} interval instead of per-variable extremes.
     * <p>
     * <b>{@link #target} narrowing</b>: a target value below {@code dLo} can never satisfy {@code
     * dist <= target} (LEQ/EQ) -- {@link #target}'s lower bound rises to {@code dLo}; a target value
     * above {@code dHi} can never satisfy {@code dist >= target} (GEQ/EQ) -- its upper bound falls to
     * {@code dHi}. Given the two infeasibility checks below pass, {@code dLo <= dHi} always, so the
     * new numeric range never crosses -- but for a gappy discrete {@link #target} domain, narrowing
     * to that otherwise-valid range can still delete every remaining value (the same class of gap
     * {@code AbsoluteDifferenceConstraintTest} exercises for its own two sides), so the narrowed
     * result is checked for emptiness explicitly rather than recorded unconditionally; an earlier
     * version of this method didn't (silently recording an emptied domain into the update map
     * without signalling infeasibility), a real bug confirmed via a genuine corpus instance
     * ({@code GracefulGraph-K02-P04.xml.lzma}) that intermittently threw {@code
     * NoSuchElementException: empty domain has no minimum} from a later propagation round reading
     * that already-empty domain back out.
     * <p>
     * <b>{@link #left}/{@link #right} narrowing</b> (LEQ/EQ only): {@code dist <= target} decomposes
     * into the conjunction {@code (left - right <= target) AND (right - left <= target)} -- two
     * independent linear inequalities, each narrowed the same way {@link
     * io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint} clips one side using the
     * other's current bound plus {@link #target}'s current maximum as the effective offset. {@code
     * dist >= target} (GEQ) has no such conjunctive decomposition -- it's a disjunction ({@code left
     * - right >= target OR right - left >= target}), and narrowing either side soundly in the
     * general case would need to first prove one disjunct dead, which this method doesn't attempt;
     * GEQ therefore only narrows {@link #target}, leaving {@link #left}/{@link #right} untouched --
     * sound, not maximally tight, the same tradeoff several other propagators in this codebase accept
     * (e.g. {@link NValueConstraint}, {@link DistinctVectorsConstraint}). {@link #operator}
     * {@link Operator#EQ} still gets the LEQ-side conjunctive narrowing (the two are combined via
     * {@code EQ} needing both {@code <=} and {@code >=} to hold), just not the GEQ-side one either,
     * for the same reason.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        Domain<N> leftDomain = (Domain<N>) domains.get(left);
        Domain<N> rightDomain = (Domain<N>) domains.get(right);
        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double leftLo = NumericBounds.min(leftDomain), leftHi = NumericBounds.max(leftDomain);
        double rightLo = NumericBounds.min(rightDomain), rightHi = NumericBounds.max(rightDomain);
        double targetLo = NumericBounds.min(targetDomain), targetHi = NumericBounds.max(targetDomain);

        double diffLo = leftLo - rightHi;
        double diffHi = leftHi - rightLo;
        double dHi = Math.max(Math.abs(diffLo), Math.abs(diffHi));
        double dLo = (diffLo <= 0 && diffHi >= 0) ? 0 : Math.min(Math.abs(diffLo), Math.abs(diffHi));

        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;
        if (leqLike && dLo > targetHi) return Optional.empty();
        if (geqLike && dHi < targetLo) return Optional.empty();

        double newTargetLo = leqLike ? Math.max(targetLo, dLo) : targetLo;
        double newTargetHi = geqLike ? Math.min(targetHi, dHi) : targetHi;

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        Optional<Domain<N>> prunedTarget = NumericBounds.narrow(targetDomain, newTargetLo, newTargetHi);
        if (prunedTarget.isPresent()) {
            if (prunedTarget.get().isEmpty()) return Optional.empty();
            updated.put(target, prunedTarget.get());
        }

        if (leqLike) {
            double bound = targetHi;
            double newLeftHi = Math.min(leftHi, rightHi + bound);
            double newLeftLo = Math.max(leftLo, rightLo - bound);
            Optional<Domain<N>> prunedLeft = NumericBounds.narrow(leftDomain, newLeftLo, newLeftHi);
            if (prunedLeft.isPresent()) {
                if (prunedLeft.get().isEmpty()) return Optional.empty();
                updated.put(left, prunedLeft.get());
            }

            double newRightHi = Math.min(rightHi, leftHi + bound);
            double newRightLo = Math.max(rightLo, leftLo - bound);
            Optional<Domain<N>> prunedRight = NumericBounds.narrow(rightDomain, newRightLo, newRightHi);
            if (prunedRight.isPresent()) {
                if (prunedRight.get().isEmpty()) return Optional.empty();
                updated.put(right, prunedRight.get());
            }
        }

        return Optional.of(updated);
    }

    /**
     * {@link #propagate}'s bounds-only infeasibility checks are derived purely from {@link #left}/
     * {@link #right}/{@link #target}'s current bounding ranges, so {@link
     * RangeNogoodConstraint#fromCurrentBounds} is always a sound explanation, falling back to {@link
     * ValueSetNogoodConstraint#fromCurrentState} when a side isn't safely citable as a range (the
     * same two-step fallback {@link MaxVariableConstraint#explainInfeasible} uses).
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        return "|" + left + " - " + right + "| " + operator.symbol + " " + target;
    }
}
