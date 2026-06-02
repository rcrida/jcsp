package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the short-circuit in {@link SolverDecorator}: when preprocessing reduces all domains
 * to singletons, the forced assignment is returned without invoking the inner solver chain.
 */
public class SolverDecoratorTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void singletonDomains_returnsDirectly() {
        // Both domains are already singletons; the solver should short-circuit and return the
        // forced assignment without running tree decomposition or backtracking.
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(2, 2))
                .allDiffConstraint(Set.of(x1, x2))
                .build();
        var result = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getValue(x1)).hasValue(1);
        assertThat(result.get().getValue(x2)).hasValue(2);
    }

    @Test
    void singletonDomains_violatedNaryConstraint_returnsEmpty() {
        // Singleton domains pass propagation (NC/AC3/AllDiff don't check n-ary predicates),
        // but the forced assignment violates the predicate constraint → short-circuit returns empty.
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(2, 2))
                .predicateConstraint(Set.of(x1, x2), a -> false)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolution(csp)).isEmpty();
    }

    @Test
    void singletonDomains_optimisation_returnsDirectly() {
        // The objective overload should also short-circuit when domains are singletons.
        Variable<Integer> x1 = F.create("x1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(5, 5))
                .build();
        var result = Solver.Factory.INSTANCE.createSolver()
                .getSolution(csp, a -> a.getValue(x1).map(Integer::doubleValue).orElse(0.0));
        assertThat(result).isPresent();
        assertThat(result.get().getValue(x1)).hasValue(5);
    }
}
