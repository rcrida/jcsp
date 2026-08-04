package io.github.rcrida.jcsp.solver.lp;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds and solves a joint LP relaxation over a {@link ConstraintSatisfactionProblem}'s linear
 * structure and a caller-supplied {@link LinearObjective} (see ADR-0009). Only {@link
 * SumBoundConstraint}, {@link SumVariableConstraint}, {@link LinearBoundConstraint}, and {@link
 * LinearVariableConstraint} are translated into rows; every other constraint is invisible to the
 * relaxation. That's sound rather than merely approximate: dropping a constraint only enlarges a
 * minimization's feasible region, so {@link #solve}'s bound stays a valid lower bound on the full
 * problem's true optimum regardless of how much of the problem isn't linear -- it's just looser the
 * less linear the problem is. A variable's domain bounds become an ojAlgo box constraint whether the
 * variable is continuous ({@link io.github.rcrida.jcsp.domains.BoundedDomain}) or discrete ({@link
 * io.github.rcrida.jcsp.domains.IntRangeDomain} and friends) -- for a discrete variable, dropping
 * its integrality requirement down to a real-valued range is exactly the "relaxation" in "LP
 * relaxation".
 * <p>
 * Like {@link SumBoundConstraint}/{@link LinearBoundConstraint}'s own propagation, only {@link
 * Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} translate into a row; a linear
 * constraint using any other operator is skipped the same way those propagators already treat it as
 * a no-op.
 */
@Slf4j
public final class LpModelBuilder {
    private LpModelBuilder() {}

    /**
     * Solves the LP relaxation of {@code csp} against {@code objective}, returning {@link
     * Optional#empty()} if the relaxation itself is infeasible (in which case {@code csp} is
     * certainly infeasible too, since the relaxation only ever enlarges the feasible region).
     */
    public static Optional<LpBound> solve(@NonNull ConstraintSatisfactionProblem csp, @NonNull LinearObjective objective) {
        List<Variable<?>> variables = List.copyOf(relevantVariables(csp, objective));
        if (variables.isEmpty()) {
            return Optional.of(new LpBound(objective.getConstant(), Map.of()));
        }

        Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables = new LinkedHashMap<>();
        ExpressionsBasedModel model = build(csp, objective, variables, ojVariables);

        Optimisation.Result result = model.minimise();
        if (!result.getState().isFeasible()) {
            log.debug("LP relaxation infeasible: {}", result.getState());
            return Optional.empty();
        }

        Map<Variable<?>, Double> solution = new LinkedHashMap<>();
        for (Variable<?> variable : variables) {
            solution.put(variable, ojVariables.get(variable).getValue().doubleValue());
        }
        return Optional.of(new LpBound(result.getValue() + objective.getConstant(), solution));
    }

    private static ExpressionsBasedModel build(ConstraintSatisfactionProblem csp,
                                                LinearObjective objective,
                                                List<Variable<?>> variables,
                                                Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        for (Variable<?> variable : variables) {
            double[] bounds = boundsOf(csp, variable);
            var ojVariable = model.addVariable(variable.getName())
                    .lower(bounds[0])
                    .upper(bounds[1])
                    .weight(objective.getCoefficients().getOrDefault(variable, 0.0));
            ojVariables.put(variable, ojVariable);
        }

        int index = 0;
        for (Constraint constraint : csp.getConstraints()) {
            addRow(model, ojVariables, constraint, index++);
        }
        return model;
    }

    private static Set<Variable<?>> relevantVariables(ConstraintSatisfactionProblem csp, LinearObjective objective) {
        Set<Variable<?>> variables = new LinkedHashSet<>(objective.getCoefficients().keySet());
        for (Constraint constraint : csp.getConstraints()) {
            if (isLinear(constraint)) {
                variables.addAll(constraint.getVariables());
            }
        }
        return variables;
    }

    private static boolean isLinear(Constraint constraint) {
        return constraint instanceof SumBoundConstraint<?>
                || constraint instanceof LinearBoundConstraint<?>
                || constraint instanceof SumVariableConstraint<?>
                || constraint instanceof LinearVariableConstraint<?>;
    }

    /**
     * Translates one linear constraint into an ojAlgo {@link Expression} row, skipping it entirely
     * (adding nothing to {@code model}) when it isn't one of the four linear constraint types, or
     * its {@link Operator} isn't propagating -- mirroring {@link SumBoundConstraint}/{@link
     * LinearBoundConstraint}'s own propagators, which treat a non-{@code EQ}/{@code LEQ}/{@code GEQ}
     * operator as a no-op rather than something to model.
     */
    private static void addRow(ExpressionsBasedModel model,
                                Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables,
                                Constraint constraint,
                                int index) {
        switch (constraint) {
            case SumBoundConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = newRow(model, index, ojVariables, c.getVariables());
                    applyBound(expression, c.getOperator(), c.getBound().doubleValue());
                }
            }
            case LinearBoundConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = model.addExpression("row" + index);
                    for (var entry : c.getCoefficients().entrySet()) {
                        expression.set(ojVariables.get(entry.getKey()), entry.getValue().doubleValue());
                    }
                    applyBound(expression, c.getOperator(), c.getBound().doubleValue());
                }
            }
            case SumVariableConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = newRow(model, index, ojVariables, c.getSummedVariables());
                    expression.set(ojVariables.get(c.getTarget()), -1.0);
                    applyBound(expression, c.getOperator(), 0.0);
                }
            }
            case LinearVariableConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = model.addExpression("row" + index);
                    for (var entry : c.getCoefficients().entrySet()) {
                        expression.set(ojVariables.get(entry.getKey()), entry.getValue().doubleValue());
                    }
                    expression.set(ojVariables.get(c.getTarget()), -1.0);
                    applyBound(expression, c.getOperator(), 0.0);
                }
            }
            default -> { }
        }
    }

    private static Expression newRow(ExpressionsBasedModel model, int index,
                                      Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables,
                                      Set<? extends Variable<?>> unitCoefficientVariables) {
        Expression expression = model.addExpression("row" + index);
        for (Variable<?> variable : unitCoefficientVariables) {
            expression.set(ojVariables.get(variable), 1.0);
        }
        return expression;
    }

    private static boolean isPropagating(Operator operator) {
        return operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.GEQ;
    }

    /** Only ever called once {@link #isPropagating} has confirmed {@code operator} is one of these three. */
    private static void applyBound(Expression expression, Operator operator, double bound) {
        if (operator == Operator.EQ) {
            expression.level(bound);
        } else if (operator == Operator.LEQ) {
            expression.upper(bound);
        } else {
            expression.lower(bound);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static double[] boundsOf(ConstraintSatisfactionProblem csp, Variable<?> variable) {
        Domain domain = csp.getDomain((Variable) variable);
        return new double[]{NumericBounds.min(domain), NumericBounds.max(domain)};
    }
}
