package io.github.rcrida.jcsp.solver.lp;

import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;

/**
 * The result of solving an {@link LpModelBuilder}-built relaxation: {@link #lowerBound} is a valid
 * lower bound (see ADR-0009) on the true objective for any completion consistent with the relaxed
 * problem's current variable bounds; {@link #solution} is the LP-optimal value found for each
 * variable the model covered, on the same scale {@code lowerBound} was computed on regardless of
 * whether the underlying jcsp variable is continuous or (relaxed) discrete.
 */
public record LpBound(double lowerBound, Map<Variable<?>, Double> solution) {
}
