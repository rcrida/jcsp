package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
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

    /**
     * A {@link io.github.rcrida.jcsp.constraints.nary.ProductConstraint} objective is invisible to
     * {@link io.github.rcrida.jcsp.solver.lp.LpModelBuilder}'s LP relaxation, so
     * {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver#resolveContinuousResidual}'s LP fast
     * path always fails its consistency check here and falls back to
     * {@link io.github.rcrida.jcsp.solver.BisectionConditioningSolver} -- whose incumbent pruning
     * relies on {@code intervalLowerBound} (real interval arithmetic over each open variable's
     * current bounds) rather than only variables already singleton, since without it this exact case
     * doesn't finish within a normal test timeout. x*y&gt;=20 with x,y&isin;[4,6]: the true minimum of
     * x+y is at x=y=&#8730;20&#8776;4.472, an interior point of the box, not a corner.
     */
    @Test
    void productConstraintResidual_nonlinearInequality_resolvesViaBisection() {
        Variable<Double> x = F.create("prod_x");
        Variable<Double> y = F.create("prod_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(4.0, 6.0))
                .variableDomain(y, IntervalDomain.of(4.0, 6.0))
                .productConstraint(Set.of(x, y), Operator.GEQ, 20.0)
                .build();
        Map<Variable<? extends Number>, Double> coeffs = new HashMap<>();
        coeffs.put(x, 1.0);
        coeffs.put(y, 1.0);
        LinearObjective objective = LinearObjective.builder().coefficients(coeffs).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal * yVal).isGreaterThanOrEqualTo(20.0 - 1e-6);
        // Bisection converges to within DEFAULT_BISECTION_EPSILON (1e-3) of the true optimum
        // per-variable, but the two variables' grid points don't land symmetrically around
        // sqrt(20) -- 0.02 comfortably covers the observed ~0.012 offset either side.
        assertThat(xVal).isCloseTo(Math.sqrt(20.0), within(0.02));
        assertThat(yVal).isCloseTo(Math.sqrt(20.0), within(0.02));
    }
}
