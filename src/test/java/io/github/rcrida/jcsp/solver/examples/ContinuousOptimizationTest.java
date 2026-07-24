package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Optimization over continuous {@link IntervalDomain} variables via
 * {@link Solver.Factory#createSolver()}.
 *
 * <p>x+y=7, x,y∈[0,10]. Minimise (x−2)²: the optimum is x=2, y=5.
 * {@link BisectionConditioningSolver} explores the feasible region down to
 * {@link Solver.Factory#DEFAULT_BISECTION_EPSILON}; the improving sequence via
 * {@code getSolution(csp, objective)} converges to x≈2.
 */
public class ContinuousOptimizationTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void minimizeObjective_findsOptimum() {
        Variable<Double> x = F.create("x");
        Variable<Double> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .sumConstraint(Set.of(x, y), Operator.EQ, 7.0)
                .build();
        // (x-2)^2 is always >= 0, so 0.0 is a valid lower bound for a not-yet-resolved x -- the same
        // "unassigned contributes nothing yet" convention every optimization objective in this
        // codebase must follow (see BranchAndBoundSolver's Javadoc).
        var solution = Solver.Factory.INSTANCE.createSolver(csp,
                        a -> a.getValue(x).map(v -> Math.pow((Double) v - 2.0, 2)).orElse(0.0))
                .getSolution();
        assertThat(solution).isPresent();
        assertThat((Double) solution.get().getValue(x).orElseThrow()).isCloseTo(2.0, within(0.01));
        assertThat((Double) solution.get().getValue(y).orElseThrow()).isCloseTo(5.0, within(0.01));
    }
}
