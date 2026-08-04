package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * An explicit linear objective {@code constant + sum(coefficients[v] * v)}, for callers that want
 * {@link io.github.rcrida.jcsp.solver.lp.LpModelBuilder} (see ADR-0009) to build a joint LP
 * relaxation over the objective and the problem's linear constraints. Unlike an opaque {@link
 * ToDoubleFunction}{@code <Assignment>}, {@link #coefficients} exposes real per-variable weights an
 * LP model builder can read -- an opaque function can't be introspected for them. Implements {@link
 * ToDoubleFunction}{@code <Assignment>} itself so an instance can be passed directly to {@link
 * Solver.Factory#createSolver(io.github.rcrida.jcsp.ConstraintSatisfactionProblem, ToDoubleFunction)}'s
 * existing overload with no adapter needed.
 * <p>
 * {@link #applyAsDouble(Assignment)} skips any coefficient variable absent from {@code assignment},
 * treating it as contributing {@code 0} -- the same "unassigned contributes nothing yet"
 * partial-assignment convention {@link BranchAndBoundSolver} and {@link BisectionConditioningSolver}
 * already require of every optimization objective in this codebase, valid as a lower bound only
 * when every coefficient and every relevant domain value is non-negative.
 */
@Value
@Builder
public class LinearObjective implements ToDoubleFunction<Assignment> {
    @Singular @NonNull Map<Variable<? extends Number>, Double> coefficients;
    @Builder.Default double constant = 0.0;

    @Override
    public double applyAsDouble(@NonNull Assignment assignment) {
        double total = constant;
        Map<Variable<?>, Object> values = assignment.getValues();
        for (var entry : coefficients.entrySet()) {
            Object value = values.get(entry.getKey());
            if (value == null) continue;
            total += entry.getValue() * ((Number) value).doubleValue();
        }
        return total;
    }
}
