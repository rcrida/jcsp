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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the sum of a set of numeric variables to a variable target,
 * rather than a fixed bound: {@code v1 + v2 + ... + vn <op> target}. The sibling of {@link
 * SumBoundConstraint} for when the right-hand side is itself a decision variable — see {@link
 * NumericBounds#propagateWeightedSumVsTarget} for why this needed its own class rather than
 * folding {@code target} into {@code SumBoundConstraint} with a nullable field (it can't simply
 * extend {@link UniformNaryConstraint} the way {@code SumBoundConstraint} does, since that
 * class's {@code isSatisfiedBy} is {@code final} and can't distinguish {@code target}'s role from
 * an ordinary summed variable via its generic value-collection bridge).
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once every
 * summed variable and {@code target} are all assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SumVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    @NonNull private final Set<Variable<N>> summedVariables;
    @NonNull private final Variable<N> target;
    @NonNull private final Operator operator;

    public static <N extends Number> SumVariableConstraint<N> of(@NonNull Set<Variable<N>> variables,
                                                                   @NonNull Operator operator,
                                                                   @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(variables);
        allVars.add(target);
        return SumVariableConstraint.<N>builder()
                .variables(allVars)
                .summedVariables(Set.copyOf(variables))
                .target(target)
                .operator(operator)
                .build();
    }

    /**
     * Dispatches the sum's numeric type off {@code target}'s <em>actual assigned value</em>
     * (always available once the containment check passes) rather than a compile-time constant —
     * unlike {@link SumBoundConstraint}, there is no fixed {@code N}-typed {@code bound} to switch
     * on here, and {@code target}'s runtime value is the only {@code N} instance ever available.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        N targetValue = assignment.getValue(target).orElseThrow();
        return operator.compare(sum(assignment, targetValue), targetValue);
    }

    @SuppressWarnings("unchecked")
    private N sum(@NonNull Assignment assignment, @NonNull N typeSample) {
        return switch (typeSample) {
            case Byte b -> {
                int s = 0;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().intValue();
                yield (N) (Number) (byte) s;
            }
            case Short s0 -> {
                int s = 0;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().intValue();
                yield (N) (Number) (short) s;
            }
            case Integer i -> {
                int s = 0;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().intValue();
                yield (N) (Number) s;
            }
            case Long l -> {
                long s = 0L;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().longValue();
                yield (N) (Number) s;
            }
            case Float f -> {
                float s = 0f;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().floatValue();
                yield (N) (Number) s;
            }
            case Double d -> {
                double s = 0.0;
                for (Variable<N> v : summedVariables) s += assignment.getValue(v).orElseThrow().doubleValue();
                yield (N) (Number) s;
            }
            default -> throw new IllegalStateException("Unsupported type: " + typeSample.getClass());
        };
    }

    /** Delegates entirely to {@link NumericBounds#propagateWeightedSumVsTarget} with every coefficient {@code 1.0}. */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<N>> vars = new ArrayList<>(summedVariables);
        double[] coefficients = new double[vars.size()];
        Arrays.fill(coefficients, 1.0);
        return NumericBounds.propagateWeightedSumVsTarget(vars, coefficients, target, operator, domains);
    }

    /**
     * Same shape as {@link SumBoundConstraint#explainInfeasible}: the sum's violation depends on
     * the combined contribution of every variable (including {@code target}, now folded into
     * {@link #getVariables()}), not any single one in isolation, so the fully collective
     * explanation via {@link Propagatable#allSingletonReason} is the only sound, self-contained
     * one.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        String varSum = summedVariables.stream().map(Object::toString).sorted().collect(Collectors.joining(" + "));
        return varSum + " " + operator.symbol + " " + target;
    }
}
