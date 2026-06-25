package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class WalkSATSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static WalkSATSolver solver() {
        return WalkSATSolver.of(20, 500, RandomAssignmentFactory.INSTANCE);
    }

    @Test
    void domainObjectSetWithBothBooleans_treatedAsFlippable() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        var both = DomainObjectSet.<Boolean>builder().value(true).value(false).build();
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, both)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .notEqualsConstraint(x, y)
                .build();
        assertThat(WalkSATSolver.of(10, 100, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp)).isPresent();
    }

    @Test
    void findsSolutionForSimpleSatisfiableProblem() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .notEqualsConstraint(x, y)
                .build();
        assertThat(solver().getLocalSolution(csp)).isPresent();
    }

    @Test
    void findsSolutionWithAtLeastNConstraint() {
        Variable<Boolean> a = F.create("a");
        Variable<Boolean> b = F.create("b");
        Variable<Boolean> c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .atLeastNConstraint(Set.of(a, b, c), 2)  // at least two true
                .atMostNConstraint(Set.of(a, b, c), 2)   // at most two true
                .build();
        var solution = solver().getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void singletonDomainObjectSet_varsEmpty_returnsEmpty() {
        Variable<Boolean> x = F.create("x");
        var singleton = DomainObjectSet.<Boolean>builder().value(false).build();
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, singleton)
                .equalsConstraint(x, true)
                .build();
        InitialAssignmentFactory factory = c -> Assignment.builder().value(x, false).build();
        assertThat(WalkSATSolver.of(1, 5, factory).getLocalSolution(csp)).isEmpty();
    }

    @Test
    void returnsEmptyForInfeasibleProblem() {
        Variable<Boolean> x = F.create("x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .equalsConstraint(x, true)
                .equalsConstraint(x, false)
                .build();
        assertThat(WalkSATSolver.of(3, 10, RandomAssignmentFactory.INSTANCE).getLocalSolution(csp)).isEmpty();
    }

    @Test
    void canFlip_returnsTrueForBooleanDomainsAndFalseForOthers() {
        assertThat(WalkSATSolver.canFlip(BooleanDomain.INSTANCE)).isTrue();
        assertThat(WalkSATSolver.canFlip(DomainObjectSet.<Boolean>builder().value(true).value(false).build())).isTrue();
        assertThat(WalkSATSolver.canFlip(IntervalDomain.of(0.0, 1.0))).isFalse();
        assertThat(WalkSATSolver.canFlip(DomainObjectSet.<Boolean>builder().value(true).build())).isFalse();
    }

    @Test
    void objectiveOverload_returnsMinimumCostSolution() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        // exactly one must be true; objective: prefer y=true (cost 0) over x=true (cost 1)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(x, y))
                .build();
        var solver = WalkSATSolver.of(20, 200, RandomAssignmentFactory.INSTANCE);
        var solution = solver.getLocalSolution(csp,
                a -> a.getValue(x).orElse(false) ? 1.0 : 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
        // with enough attempts the minimum-cost (y=true) solution should be found
        assertThat(solution.get().getValue(x)).contains(false);
        assertThat(solution.get().getValue(y)).contains(true);
    }

    @Test
    void atMostOneConstraint_excludedFromNonDecomposableNary() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        Variable<Boolean> z = F.create("z");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .variableDomain(z, BooleanDomain.INSTANCE)
                .atMostOneConstraint(Set.of(x, y, z))
                .build();
        var solution = WalkSATSolver.of(10, 100, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void factory_bipartiteGraphColouring_solvedViaWalkSAT() {
        // K_{3,3}: two groups of 3 nodes, every cross-pair must have different colours.
        // Only notEquals constraints — no ExactlyOne or AtLeastN — so factory routes to WalkSAT.
        Variable<Boolean> l1 = F.create("L1"), l2 = F.create("L2"), l3 = F.create("L3");
        Variable<Boolean> r1 = F.create("R1"), r2 = F.create("R2"), r3 = F.create("R3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(l1, BooleanDomain.INSTANCE)
                .variableDomain(l2, BooleanDomain.INSTANCE)
                .variableDomain(l3, BooleanDomain.INSTANCE)
                .variableDomain(r1, BooleanDomain.INSTANCE)
                .variableDomain(r2, BooleanDomain.INSTANCE)
                .variableDomain(r3, BooleanDomain.INSTANCE)
                .notEqualsConstraint(l1, r1).notEqualsConstraint(l1, r2).notEqualsConstraint(l1, r3)
                .notEqualsConstraint(l2, r1).notEqualsConstraint(l2, r2).notEqualsConstraint(l2, r3)
                .notEqualsConstraint(l3, r1).notEqualsConstraint(l3, r2).notEqualsConstraint(l3, r3)
                .build();
        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 100, RandomAssignmentFactory.INSTANCE);
        var solution = solver.getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void factoryRoutesToWalkSATForBooleanOnlyCSP() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        Variable<Boolean> z = F.create("z");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .variableDomain(z, BooleanDomain.INSTANCE)
                .notEqualsConstraint(x, y)
                .notEqualsConstraint(y, z)
                .build();
        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 200, RandomAssignmentFactory.INSTANCE);
        var solution = solver.getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void factory_singletonAfterPreprocessing_routesToWalkSAT() {
        // equalsConstraint forces x=true during preprocessing → x domain becomes singleton {true}
        // allBoolean check must accept it via d.isSingleton(); canFlip then skips x safely
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .equalsConstraint(x, Boolean.TRUE)
                .notEqualsConstraint(x, y)
                .build();
        var solution = LocalSolver.Factory.INSTANCE.createLocalSolver(5, 50, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void factory_atLeastNConstraint_routedToMinConflicts() {
        // AtLeastNConstraint survives preprocessing → noCountingConstraints=false → MinConflicts used
        Variable<Boolean> a = F.create("a");
        Variable<Boolean> b = F.create("b");
        Variable<Boolean> c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(b, BooleanDomain.INSTANCE)
                .variableDomain(c, BooleanDomain.INSTANCE)
                .atLeastNConstraint(Set.of(a, b, c), 2)
                .build();
        var solution = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 100, RandomAssignmentFactory.INSTANCE)
                .getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }

    @Test
    void reifyConstraintWithBinaryBody_includedAsNonDecomposableNary() {
        // ReifiedConstraint with a binary body is NaryConstraint + BinaryDecomposable but
        // getAsBinaryConstraints() returns empty → candidates() includes it as non-decomposable
        Variable<Boolean> a = F.create("a");
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, BooleanDomain.INSTANCE)
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .reifyConstraint(a, BinaryNotEqualsConstraint.of(x, y))
                .build();
        var solution = WalkSATSolver.of(10, 100, RandomAssignmentFactory.INSTANCE).getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }
}
