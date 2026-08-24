package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
        // x+y=3.5, x∈[0,2], y∈[0,2] — SumBoundConstraint Double bound triggers propagateDouble
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

    // ── Incumbent-seeded getSolutions(csp, double) (ADR-0009 deferred item) ─

    @Test
    void seededIncumbent_alreadyBeatenPrunesImmediatelyWithoutBisecting() {
        // minimize x, x in [0,10]. partialAssignmentLowerBound at the root is 0.0 (nothing is
        // singleton yet, so the objective's "unassigned contributes nothing" convention applies) --
        // seeding a bound already <= that (-1.0) must prune the whole residual in one check, before
        // any bisection recursion happens at all.
        Variable<Double> x = F.create("seed_x1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-9)
                .objective(a -> a.getValue(x).map(v -> (Double) v).orElse(0.0))
                .build();

        assertThat(bisection.getSolutions(csp, -1.0).toList()).isEmpty();
    }

    @Test
    void seededIncumbent_stillFindsAGenuinelyImprovingSolution() {
        // Same CSP/objective, but seeded with a real (non-trivial) incumbent that a feasible point
        // (x close to 0) can still beat -- confirms seeding doesn't over-prune, only skips work that
        // couldn't have improved on the seed anyway.
        Variable<Double> x = F.create("seed_x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-3)
                .objective(a -> a.getValue(x).map(v -> (Double) v).orElse(0.0))
                .build();

        var solutions = bisection.getSolutions(csp, 5.0).toList();

        assertThat(solutions).isNotEmpty();
        double best = (Double) solutions.getLast().getValue(x).orElseThrow();
        assertThat(best).isLessThan(5.0);
    }

    @Test
    void unseededGetSolutions_stillDefaultsToMaxValueIncumbent() {
        // The public single-arg getSolutions(csp) must remain exactly as before -- unaffected by the
        // new seeded overload existing alongside it.
        Variable<Double> x = F.create("seed_x3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-3)
                .objective(a -> a.getValue(x).map(v -> (Double) v).orElse(0.0))
                .build();

        assertThat(bisection.getSolutions(csp).toList()).isNotEmpty();
    }

    // ── lowerBound dispatch: LinearObjective → intervalLowerBound ─

    @Test
    void linearObjective_positiveCoefficient_usesDomainMinAsBound() {
        // minimize x, x in [0,10]: intervalLowerBound = 1.0 * min(0) = 0.0. Seeding an incumbent
        // already <= that (-1.0) must prune the whole residual in one check, before any bisection --
        // exercises lowerBound's true (LinearObjective) branch and termLowerBound's
        // coefficient >= 0 branch together.
        Variable<Double> x = F.create("lo_pos_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        Map<Variable<? extends Number>, Double> coeffs = new HashMap<>();
        coeffs.put(x, 1.0);
        LinearObjective objective = LinearObjective.builder().coefficients(coeffs).build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-3)
                .objective(objective)
                .build();

        assertThat(bisection.getSolutions(csp, -1.0).toList()).isEmpty();
    }

    @Test
    void linearObjective_negativeCoefficient_usesDomainMaxAsBound() {
        // minimize -x (== maximize x), x in [0,10]: the correct interval bound uses the domain
        // MAX (coefficient < 0), giving -1.0*10 = -10.0. Seeding incumbent=-5.0 must NOT prune
        // immediately (-10.0 >= -5.0 is false), so the search proceeds and eventually finds x near
        // 10 improving on -5.0. If termLowerBound wrongly used the min (0.0) here instead, the bound
        // would be 0.0 >= -5.0 (true) and incorrectly prune everything, returning an empty result --
        // this test fails loudly in that case, catching the min/max selection getting inverted.
        Variable<Double> x = F.create("lo_neg_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        Map<Variable<? extends Number>, Double> coeffs = new HashMap<>();
        coeffs.put(x, -1.0);
        LinearObjective objective = LinearObjective.builder().coefficients(coeffs).build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-3)
                .objective(objective)
                .build();

        var solutions = bisection.getSolutions(csp, -5.0).toList();
        assertThat(solutions).isNotEmpty();
        double best = (Double) solutions.getLast().getValue(x).orElseThrow();
        assertThat(best).isGreaterThan(9.9);
    }

    @Test
    void linearObjective_singletonVariable_contributesExactValueNotMinOrMax() {
        // x is already singleton (5.0), y is open [0,10]. intervalLowerBound = 1.0*5.0 (x's exact
        // value, via termLowerBound's isSingleton branch) + 1.0*0.0 (y's min) = 5.0. Seeding an
        // incumbent of 4.0 (< 5.0) must prune immediately -- if the singleton branch were skipped in
        // favour of treating x like any other BoundedDomain, the bound would still coincidentally be
        // 5.0 here (min==max==5.0 for a singleton), so this also implicitly confirms isSingleton() is
        // checked before the unconditional BoundedDomain cast, not after.
        Variable<Double> x = F.create("lo_singleton_x");
        Variable<Double> y = F.create("lo_singleton_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(5.0, 5.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .build();
        Map<Variable<? extends Number>, Double> coeffs = new HashMap<>();
        coeffs.put(x, 1.0);
        coeffs.put(y, 1.0);
        LinearObjective objective = LinearObjective.builder().coefficients(coeffs).build();
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(SINGLETON_EXTRACTOR)
                .epsilon(1e-3)
                .objective(objective)
                .build();

        assertThat(bisection.getSolutions(csp, 4.0).toList()).isEmpty();
    }
}
