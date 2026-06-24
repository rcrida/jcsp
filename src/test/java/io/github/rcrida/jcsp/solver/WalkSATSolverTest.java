package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.BooleanDomain;
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
    void factoryRoutesToWalkSATForBooleanOnlyCSP() {
        Variable<Boolean> x = F.create("x");
        Variable<Boolean> y = F.create("y");
        Variable<Boolean> z = F.create("z");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, BooleanDomain.INSTANCE)
                .variableDomain(y, BooleanDomain.INSTANCE)
                .variableDomain(z, BooleanDomain.INSTANCE)
                .exactlyOneConstraint(Set.of(x, y, z))
                .build();
        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 200, RandomAssignmentFactory.INSTANCE);
        var solution = solver.getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }
}
