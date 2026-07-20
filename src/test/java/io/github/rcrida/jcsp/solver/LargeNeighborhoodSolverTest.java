package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class LargeNeighborhoodSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static LargeNeighborhoodSolver solver() {
        return LargeNeighborhoodSolver.of(10, 50, RandomAssignmentFactory.INSTANCE);
    }

    // --- satisfaction path ---

    @Test
    void findsSolutionWithExactlyOneConstraint() {
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        Variable<Boolean> d = F.create("d"), e = F.create("e"), f = F.create("f");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .variableDomain(d, BooleanDomain.INSTANCE)
                .variableDomain(e, BooleanDomain.INSTANCE)
                .variableDomain(f, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a, b, c))
                .exactlyOneConstraint(Set.of(d, e, f))
                .build();
        var solution = solver().getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void returnsEmptyForInfeasibleProblem() {
        // ExactlyOne({x,y}) requires exactly one true; equalsConstraints force both false
        Variable<Boolean> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(x, y))
                .equalsConstraint(x, false)
                .equalsConstraint(y, false)
                .build();
        assertThat(LargeNeighborhoodSolver.of(3, 10, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp)).isEmpty();
    }

    // --- optimization path ---

    @Test
    void optimizationSelectsMinimumCostAssignment() {
        // Single slot: a (cost 2), b (cost 1), c (cost 0) — LNS must find c=true
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a, b, c))
                .build();
        // Start infeasible: all false → bestFeasible=null; loop finds first feasible (TRUE branch for null check)
        InitialAssignmentFactory infeasibleStart = csp0 ->
                Assignment.builder().value(a, false).value(b, false).value(c, false).build();
        var solution = LargeNeighborhoodSolver.of(5, 20, infeasibleStart)
                .getLocalSolution(csp, assign ->
                        (assign.getValue(a).orElse(false) ? 2.0 : 0.0)
                        + (assign.getValue(b).orElse(false) ? 1.0 : 0.0));
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
        assertThat(solution.get().getValue(c)).contains(true);
    }

    @Test
    void optimizationFindsImprovingSolution() {
        // Two slots; initial feasible assignment has cost 4; LNS must improve to cost 0
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        Variable<Boolean> c = F.create("c"), d = F.create("d");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .variableDomain(d, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a, b))
                .exactlyOneConstraint(Set.of(c, d))
                .build();
        // a=true,c=true → cost 4; b=true,d=true → cost 0
        InitialAssignmentFactory highCostStart = csp0 ->
                Assignment.builder().value(a, true).value(b, false).value(c, true).value(d, false).build();
        // With slotsPerStep=2, both slots are relaxed simultaneously → optimal found in one step
        var solver = LargeNeighborhoodSolver.builder()
                .maxAttempts(1).maxSteps(5).initialAssignmentFactory(highCostStart).slotsPerStep(2)
                .build();
        var solution = solver.getLocalSolution(csp,
                assign -> (assign.getValue(a).orElse(false) ? 2.0 : 0.0)
                          + (assign.getValue(c).orElse(false) ? 2.0 : 0.0));
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(b)).contains(true);
        assertThat(solution.get().getValue(d)).contains(true);
    }

    @Test
    void optimizationDoesNotUpdateBestWhenCostNotImproved() {
        // Constant objective (always 0) — bestFeasible set from initial, never updated after
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a, b))
                .build();
        InitialAssignmentFactory feasibleStart = csp0 ->
                Assignment.builder().value(a, true).value(b, false).build();
        var solution = LargeNeighborhoodSolver.of(1, 5, feasibleStart)
                .getLocalSolution(csp, assign -> 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void returnsEmptyForInfeasibleProblemOnOptimizationPath() {
        Variable<Boolean> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(x, y))
                .equalsConstraint(x, false)
                .equalsConstraint(y, false)
                .build();
        assertThat(LargeNeighborhoodSolver.of(3, 10, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp, assign -> 0.0)).isEmpty();
    }

    // --- factory routing ---

    @Test
    void factoryRoutesToLNSForOptimizationWithExactlyOne() {
        // Factory must route to LNS (not MinConflicts) for optimization + ExactlyOne
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(a, b, c))
                .build();
        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 50, RandomAssignmentFactory.INSTANCE);
        // c=true is the only zero-cost assignment
        var solution = solver.getLocalSolution(csp,
                assign -> assign.getValue(c).orElse(false) ? 0.0 : 1.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
        assertThat(solution.get().getValue(c)).contains(true);
    }

    @Test
    void slotWithAllSingletonDomains_isExcludedFromNeighborhood() {
        // x1, x2 both have singleton domain {false} → ExactlyOne({x1,x2}) becomes empty after
        // filtering → excluded from the neighborhood entirely, so LNS can never relax it. (A
        // single-variable set would get simplified by the builder into a UnaryValueConstraint
        // instead of a real ExactlyOneConstraint — see exactlyOneConstraint's Javadoc — so this
        // needs >=2 variables to actually reach extractSlots's empty-slot filter.)
        // ExactlyOne({y,z}) is still relaxed, but ExactlyOne({x1,x2}) is permanently violated
        // (both fixed false, so it can never have exactly one true) → no feasible solution
        // exists, returns empty, regardless of what LNS does with {y,z}.
        Variable<Boolean> x1 = F.create("x1"), x2 = F.create("x2");
        Variable<Boolean> y = F.create("y"), z = F.create("z");
        var singletonFalse = DomainObjectSet.<Boolean>builder().value(false).build();
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, singletonFalse)
                .variableDomain(x2, singletonFalse)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .variableDomain(z, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(x1, x2))
                .exactlyOneConstraint(Set.of(y, z))
                .build();
        InitialAssignmentFactory factory = csp0 ->
                Assignment.builder().value(x1, false).value(x2, false).value(y, true).value(z, false).build();
        assertThat(LargeNeighborhoodSolver.of(1, 5, factory).getLocalSolution(csp)).isEmpty();
    }

    @Test
    void factoryUsesMinConflictsForOptimizationWithoutExactlyOne() {
        // No ExactlyOne → factory stays on MinConflicts path for optimization
        Variable<Boolean> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .atLeastNConstraint(Set.of(a, b, c), 2)
                .atMostNConstraint(Set.of(a, b, c), 2)
                .build();
        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 100, RandomAssignmentFactory.INSTANCE);
        var solution = solver.getLocalSolution(csp, assign -> 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }
}
