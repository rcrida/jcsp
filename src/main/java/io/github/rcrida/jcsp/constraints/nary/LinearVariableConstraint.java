package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares a weighted sum of numeric variables to a variable target,
 * rather than a fixed bound: {@code a1*v1 + a2*v2 + ... <op> target}. The sibling of {@link
 * LinearBoundConstraint} for when the right-hand side is itself a decision variable — see {@link
 * SumVariableConstraint} (the unweighted analogue) and {@link
 * NumericBounds#propagateWeightedSumVsTarget} for the shared rationale and propagation algorithm.
 * <p>
 * {@code coefficients} is a caller-supplied, already-typed {@code Map<Variable<N>, N>} exactly
 * like {@link LinearBoundConstraint}'s — no synthesized constant is ever needed here (unlike
 * {@code target} itself, whose coefficient of {@code -1} is applied as a raw {@code double} inside
 * {@link NumericBounds#propagateWeightedSumVsTarget}, never boxed into {@code N}).
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once every
 * coefficient variable and {@code target} are all assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LinearVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    @NonNull private final Map<Variable<N>, N> coefficients;
    @NonNull private final Variable<N> target;
    @NonNull private final Operator operator;

    public static <N extends Number> LinearVariableConstraint<N> of(@NonNull Map<Variable<N>, N> coefficients,
                                                                      @NonNull Operator operator,
                                                                      @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(coefficients.keySet());
        allVars.add(target);
        return LinearVariableConstraint.<N>builder()
                .variables(allVars)
                .coefficients(Map.copyOf(coefficients))
                .target(target)
                .operator(operator)
                .build();
    }

    /**
     * Dispatches the weighted sum's numeric type off {@code target}'s <em>actual assigned
     * value</em> — same reasoning as {@link SumVariableConstraint#isSatisfiedBy}.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        N targetValue = assignment.getValue(target).orElseThrow();
        return operator.compare(weightedSum(assignment, targetValue), targetValue);
    }

    @SuppressWarnings("unchecked")
    private N weightedSum(@NonNull Assignment assignment, @NonNull N typeSample) {
        return switch (typeSample) {
            case Byte b -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    s += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number) (byte) s;
            }
            case Short s0 -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    s += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number) (short) s;
            }
            case Integer i -> {
                int s = 0;
                for (var e : coefficients.entrySet())
                    s += e.getValue().intValue() * assignment.getValue(e.getKey()).orElseThrow().intValue();
                yield (N) (Number) s;
            }
            case Long l -> {
                long s = 0L;
                for (var e : coefficients.entrySet())
                    s += e.getValue().longValue() * assignment.getValue(e.getKey()).orElseThrow().longValue();
                yield (N) (Number) s;
            }
            case Float f -> {
                float s = 0f;
                for (var e : coefficients.entrySet())
                    s += e.getValue().floatValue() * assignment.getValue(e.getKey()).orElseThrow().floatValue();
                yield (N) (Number) s;
            }
            case Double d -> {
                double s = 0.0;
                for (var e : coefficients.entrySet())
                    s += e.getValue().doubleValue() * assignment.getValue(e.getKey()).orElseThrow().doubleValue();
                yield (N) (Number) s;
            }
            default -> throw new IllegalStateException("Unsupported type: " + typeSample.getClass());
        };
    }

    /**
     * Delegates entirely to {@link NumericBounds#propagateWeightedSumVsTarget}, passing each
     * variable's real (looked-up, {@code .doubleValue()}-cast — never synthesized) coefficient.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>(coefficients.keySet());
        double[] coeffs = new double[vars.size()];
        for (int i = 0; i < vars.size(); i++) coeffs[i] = coefficients.get(vars.get(i)).doubleValue();
        return NumericBounds.propagateWeightedSumVsTarget(vars, coeffs, target, operator, domains);
    }

    /**
     * Same shape as {@link LinearBoundConstraint#explainInfeasible}: the weighted sum's violation
     * depends on the combined contribution of every variable (including {@code target}, now
     * folded into {@link #getVariables()}), not any single one in isolation, so the fully
     * collective explanation via {@link Propagatable#allSingletonReason} is the only sound,
     * self-contained one.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        String terms = coefficients.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getValue() + "*" + e.getKey())
                .collect(Collectors.joining(" + "));
        return terms + " " + operator.symbol + " " + target;
    }
}
