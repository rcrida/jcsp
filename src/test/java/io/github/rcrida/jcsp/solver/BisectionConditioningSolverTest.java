package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class BisectionConditioningSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    /** Extracts singleton values from all domains — used as the terminal inner solver in tests. */
    static final Solver SINGLETON_EXTRACTOR = csp ->
            Stream.of(Assignment.of(csp.getVariableDomains().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().singleValue().orElseThrow()))));

    static BisectionConditioningSolver solver(double epsilon) {
        return BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(epsilon)
                .objective(a -> 0.0)
                .build();
    }

    @Test
    void noNonSingletonBounded_delegatesToInner() {
        Variable<Double> x = F.create("x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(3.0, 3.0))
                .build();
        var solutions = solver(1e-9).getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(x)).contains(3.0);
    }

    @Test
    void withinEpsilon_snapsToMidpoint() {
        Variable<Double> x = F.create("x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(2.0, 2.5))
                .build();
        // epsilon=1.0, width=0.5 ≤ epsilon → snap to midpoint 2.25
        var solutions = solver(1.0).getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat((Double) solutions.get(0).getValue(x).orElseThrow()).isCloseTo(2.25, within(1e-9));
    }

    @Test
    void nonBoundedDomainVariable_ignoredByFindWidestBounded() {
        // n (IntRangeDomain) is not a BoundedDomain → exercises the instanceof=false branch in findWidestBounded
        Variable<Double> x = F.create("x");
        Variable<Integer> n = F.create("n");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(2.0, 3.0))
                .variableDomain(n, IntRangeDomain.of(5, 5))
                .build();
        // epsilon=2.0, x.width=1.0 ≤ epsilon → snap x to midpoint 2.5; n is non-BoundedDomain, left unchanged
        var solutions = solver(2.0).getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat((Double) solutions.get(0).getValue(x).orElseThrow()).isCloseTo(2.5, within(1e-9));
        assertThat(solutions.get(0).getValue(n)).contains(5);
    }

    @Test
    void nonSingletonDiscrete_delegatesToInner() {
        // x ∈ [0,0.5] (width ≤ epsilon=1.0 → snapped immediately to 0.25),
        // n ∈ {1,2} (non-singleton discrete — not a BoundedDomain).
        // After snapping x: findWidestBounded returns null but !isFullyDetermined() → delegates to inner.
        // This exercises the false branch of the isFullyDetermined() ternary in allFeasible.
        Variable<Double> x = F.create("x_disc");
        Variable<Integer> n = F.create("n_disc");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 0.5))
                .variableDomain(n, IntRangeDomain.of(1, 2))
                .build();
        Solver inner = c -> List.of(1, 2).stream()
                .map(v -> Assignment.of(Map.of(x, c.getDomain(x).singleValue().orElseThrow(), n, v)));
        var solver = BisectionConditioningSolver.builder()
                .inner(inner)
                .epsilon(1.0)
                .objective(a -> 0.0)
                .build();
        // getSolutions filters by improving objective (a->0.0): first included, second excluded
        var solutions = solver.getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
    }

    @Test
    void getSolution_doesNotSkipBisectionLogic() {
        // Guards against a future change making this class inherit SolverDecorator's default
        // getSolution() (delegate straight to inner) instead of its own explicit override: inner
        // is SINGLETON_EXTRACTOR, which throws NoSuchElementException on singleValue().orElseThrow()
        // if x isn't already singleton -- it only becomes singleton once bisection snaps it, so this
        // test fails loudly (not silently) if getSolution() ever bypasses bisection.
        Variable<Double> x = F.create("x_no_skip");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(2.0, 2.5))
                .build();
        var solution = solver(1.0).getSolution(csp);
        assertThat(solution).isPresent();
        assertThat((Double) solution.get().getValue(x).orElseThrow()).isCloseTo(2.25, within(1e-9));
    }

    @Test
    void bisects_oneInfeasibleBranch() {
        // x+y=3.5, x∈[0,2], y∈[0,2] — SumConstraint Double bound triggers propagateDouble
        // Bisect x at mid=1.0:
        //   left  [0,1]: y must be in [2.5,∞) ∩ [0,2] → empty → infeasible (exercises repropagate empty branch)
        //   right [1,2]: propagation narrows both to [1.5,2]; bisection continues to epsilon, producing solutions
        Variable<Double> x = F.create("x");
        Variable<Double> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 2.0))
                .variableDomain(y, IntervalDomain.of(0.0, 2.0))
                .sumConstraint(Set.of(x, y), Operator.EQ, 3.5)
                .build();
        var solution = solver(0.1).getSolution(csp);
        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        double yVal = (Double) solution.get().getValue(y).orElseThrow();
        assertThat(xVal + yVal).isCloseTo(3.5, within(1e-6));
    }
}
