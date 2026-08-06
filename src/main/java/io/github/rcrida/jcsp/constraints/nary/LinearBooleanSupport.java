package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Shared bounds-consistency arithmetic for {@link LinearBooleanBoundConstraint} and {@link
 * LinearBooleanVariableConstraint} — the two constraint types relating a weighted sum of boolean
 * indicator variables (each {@code true} contributing its coefficient, each {@code false}
 * contributing zero) to a numeric right-hand side. A boolean variable's domain only ever has one
 * or two possible values, so — unlike {@link LinearBoundConstraint}'s general per-variable
 * min/max-over-a-domain scan — the two candidate contributions ({@code 0} and the coefficient
 * itself) can be tested directly, which is both simpler and strictly stronger than bounds
 * consistency (it is real domain consistency, since there are only ever two candidate values to
 * rule in or out).
 */
final class LinearBooleanSupport {
    private LinearBooleanSupport() {
    }

    record Contribution(double min, double max) {
    }

    /**
     * The range of possible contributions {@code coeff * (bool ? 1 : 0)} given what values {@code
     * dom} still allows.
     */
    static Contribution contributionRange(@NonNull DiscreteDomain<Boolean> dom, double coeff) {
        boolean canFalse = dom.contains(false);
        boolean canTrue = dom.contains(true);
        if (canFalse && canTrue) return new Contribution(Math.min(0, coeff), Math.max(0, coeff));
        double only = canFalse ? 0 : coeff;
        return new Contribution(only, only);
    }

    /**
     * Whether {@code contrib} (either {@code 0} or a variable's coefficient) remains consistent
     * with the constraint given the combined min/max range {@code [restMin, restMax]} every other
     * term could still contribute, against right-hand side {@code k}. Callers only ever invoke
     * this for {@link Operator#EQ}/{@link Operator#LEQ}/{@link Operator#GEQ} (both call sites
     * gate on that set first, the same way {@link LinearBoundConstraint#propagate} does), so —
     * mirroring that class's if/else style rather than an exhaustive switch — no other operator
     * needs a branch here.
     */
    static boolean feasible(@NonNull Operator operator, double contrib, double restMin, double restMax, double k) {
        if (operator == Operator.EQ) return k - contrib >= restMin && k - contrib <= restMax;
        if (operator == Operator.LEQ) return contrib + restMin <= k;
        return contrib + restMax >= k;
    }

    /**
     * Narrows {@code dom} to a singleton when exactly one of {@code true}/{@code false} remains
     * {@link #feasible}; empty when both remain possible (no pruning yet).
     */
    static Optional<DiscreteDomain<Boolean>> narrowed(@NonNull DiscreteDomain<Boolean> dom, boolean falseOk, boolean trueOk) {
        if (falseOk == trueOk) return Optional.empty();
        return Optional.of(falseOk ? dom.toBuilder().delete(true).build() : dom.toBuilder().delete(false).build());
    }

    /**
     * Sums each {@code true}-valued boolean's coefficient, dispatching the accumulator's runtime
     * numeric type off {@code typeSample} the same way {@link LinearVariableConstraint} dispatches
     * off its target's actual assigned value.
     */
    @SuppressWarnings("unchecked")
    static <N extends Number> N weightedSumOfTrue(@NonNull Map<Variable<Boolean>, N> coefficients,
                                                    @NonNull Assignment assignment, @NonNull N typeSample) {
        return switch (typeSample) {
            case Byte ignored -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().intValue();
                yield (N) (Number) (byte) s;
            }
            case Short ignored -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().intValue();
                yield (N) (Number) (short) s;
            }
            case Integer ignored -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().intValue();
                yield (N) (Number) s;
            }
            case Long ignored -> {
                long s = 0L;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().longValue();
                yield (N) (Number) s;
            }
            case Float ignored -> {
                float s = 0f;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().floatValue();
                yield (N) (Number) s;
            }
            case Double ignored -> {
                double s = 0.0;
                for (var e : coefficients.entrySet())
                    if (Boolean.TRUE.equals(assignment.getValue(e.getKey()).orElseThrow())) s += e.getValue().doubleValue();
                yield (N) (Number) s;
            }
            default -> throw new IllegalStateException("Unsupported type: " + typeSample.getClass());
        };
    }
}
