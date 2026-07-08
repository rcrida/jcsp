package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IndependentSubproblemSolverTest {
    static Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static Variable<Integer> V1 = FACTORY.create("V1");
    static Variable<Integer> V2 = FACTORY.create("V2");
    static Variable<Integer> V3 = FACTORY.create("V3");
    static Variable<Integer> V4 = FACTORY.create("V4");

    // Two independent subproblems: {V1 != V2} over {1,2,3} and {V3 != V4} over {1,2}
    static ConstraintSatisfactionProblem TWO_SUBPROBLEM_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(V1, IntRangeDomain.of(1, 3))
            .variableDomain(V2, IntRangeDomain.of(1, 3))
            .variableDomain(V3, IntRangeDomain.of(1, 2))
            .variableDomain(V4, IntRangeDomain.of(1, 2))
            .notEqualsConstraint(V1, V2)
            .notEqualsConstraint(V3, V4)
            .build();

    Solver inner = csp -> Solver.Factory.INSTANCE.createSolver(csp).getSolutions();
    IndependentSubproblemSolver solver = IndependentSubproblemSolver.builder().innerFactory(sub -> inner).build();

    @Test
    void getSolutions_independentSubproblems() {
        assertThat(solver.getSolutions(TWO_SUBPROBLEM_CSP)).hasSize(12);
    }

    @Test
    void getSolutions_singleSubproblem() {
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 2))
                .variableDomain(V2, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(V1, V2)
                .build();
        assertThat(solver.getSolutions(csp)).hasSize(2);
    }

    @Test
    void getSolutions_eachSubproblemSolvedOnce() {
        // innerFactory is called exactly once per sub-problem (a fresh inner solver each time,
        // mirroring production where each sub-problem gets its own NogoodStore); this Solver's
        // own getSolutions(csp) is what actually does the solving, wrapped by the factory.
        val callCount = new AtomicInteger();
        val counting = IndependentSubproblemSolver.builder()
                .innerFactory(sub -> {
                    callCount.incrementAndGet();
                    return inner;
                })
                .build();

        counting.getSolutions(TWO_SUBPROBLEM_CSP).toList();

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void getSolution_independentSubproblems() {
        assertThat(solver.getSolution(TWO_SUBPROBLEM_CSP)).isPresent();
    }

    @Test
    void getSolution_singleSubproblem() {
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 2))
                .variableDomain(V2, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(V1, V2)
                .build();
        assertThat(solver.getSolution(csp)).isPresent();
    }

    @Test
    void getSolution_unsatSubproblem() {
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 3))
                .variableDomain(V2, IntRangeDomain.of(1, 3))
                .variableDomain(V3, IntRangeDomain.of(1, 1))
                .variableDomain(V4, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(V1, V2)
                .notEqualsConstraint(V3, V4)
                .build();
        assertThat(solver.getSolution(csp)).isEmpty();
    }

    @Test
    void getSolution_propagatesRuntimeExceptionFromSubproblem() {
        var boom = new RuntimeException("boom");
        var throwing = IndependentSubproblemSolver.builder()
                .innerFactory(sub -> { throw boom; })
                .build();
        assertThatThrownBy(() -> throwing.getSolution(TWO_SUBPROBLEM_CSP)).isSameAs(boom);
    }

    @Test
    void getSolutions_innerSubproblemSolutionsCachedAcrossIterations() {
        val elementCount = new AtomicInteger();
        Solver peekingInner = csp -> inner.getSolutions(csp).peek(a -> elementCount.incrementAndGet());
        val counting = IndependentSubproblemSolver.builder()
                .innerFactory(sub -> peekingInner)
                .build();

        counting.getSolutions(TWO_SUBPROBLEM_CSP).limit(3).toList();

        // sp1 (V1!=V2 over {1,2,3}) contributes 2 elements, sp2 (V3!=V4 over {1,2}) has 2 elements
        // computed once and replayed from cache for sp1's second element — 2+2=4 total
        assertThat(elementCount.get()).isEqualTo(4);
    }
}
