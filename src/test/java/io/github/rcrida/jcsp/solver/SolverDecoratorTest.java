package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the short-circuit in {@link SolverDecorator}: when preprocessing reduces all domains
 * to singletons, the forced assignment is returned without invoking the inner solver chain.
 */
public class SolverDecoratorTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    /** Concrete subclass that uses the default {@code preprocess} passthrough. */
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    static class PassthroughDecorator extends SolverDecorator {}

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
        var result = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
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
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolution()).isEmpty();
    }

    @Test
    void singletonDomains_optimisation_returnsDirectly() {
        // The objective overload should also short-circuit when domains are singletons.
        Variable<Integer> x1 = F.create("x1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(5, 5))
                .build();
        var result = Solver.Factory.INSTANCE.createSolver(csp, a -> a.getValue(x1).map(Integer::doubleValue).orElse(0.0))
                .getSolution();
        assertThat(result).isPresent();
        assertThat(result.get().getValue(x1)).hasValue(5);
    }

    @Test
    void defaultPreprocess_isPassthrough() {
        // Exercises SolverDecorator.preprocess default via PassthroughDecorator (no preprocess override).
        // The default preprocess returns the CSP unchanged; getSolutions then delegates to inner.
        Variable<Integer> x = F.create("x_pt");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .build();
        Solver inner = c -> Stream.of(1, 2).map(v -> Assignment.of(Map.of(x, v)));
        var solutions = PassthroughDecorator.builder().inner(inner).build().getSolutions(csp).toList();
        assertThat(solutions).hasSize(2);
    }

    @Test
    void defaultGetSolution_delegatesToInnerGetSolution_notGetSolutionsFindFirst() {
        // inner.getSolutions() and inner.getSolution() deliberately disagree here, so this only
        // passes if SolverDecorator's default getSolution() calls inner.getSolution() directly --
        // not inner.getSolutions().findFirst(), which would silently mask a terminal solver's own
        // single-solution strategy (e.g. DomWdegLubySearch's Luby-restart search) behind the
        // generic "first element of the stream" behaviour.
        Variable<Integer> x = F.create("x_delegate");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .build();
        Solver inner = new Solver() {
            @Override
            public Stream<Assignment> getSolutions(ConstraintSatisfactionProblem c) {
                return Stream.of(Assignment.of(Map.of(x, 1)));
            }

            @Override
            public Optional<Assignment> getSolution(ConstraintSatisfactionProblem c) {
                return Optional.of(Assignment.of(Map.of(x, 99)));
            }
        };
        var result = PassthroughDecorator.builder().inner(inner).build().getSolution(csp);
        assertThat(result).contains(Assignment.of(Map.of(x, 99)));
    }
}
