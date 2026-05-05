package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class IndependentSubproblemSolverTest {
    static Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static Variable V1 = FACTORY.create("V1");
    static Variable V2 = FACTORY.create("V2");
    static Variable V3 = FACTORY.create("V3");
    static Variable V4 = FACTORY.create("V4");

    // Two independent subproblems: {V1 != V2} over {1,2,3} and {V3 != V4} over {1,2}
    static ConstraintSatisfactionProblem TWO_SUBPROBLEM_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(V1, IntRangeDomain.of(1, 3))
            .variableDomain(V2, IntRangeDomain.of(1, 3))
            .variableDomain(V3, IntRangeDomain.of(1, 2))
            .variableDomain(V4, IntRangeDomain.of(1, 2))
            .notEqualsConstraint(V1, V2)
            .notEqualsConstraint(V3, V4)
            .build();

    Solver inner = Solver.Factory.INSTANCE.createSolver();
    IndependentSubproblemSolver solver = new IndependentSubproblemSolver(inner);

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
        val callCount = new AtomicInteger();
        val counting = new IndependentSubproblemSolver(csp -> {
            callCount.incrementAndGet();
            return inner.getSolutions(csp);
        });

        counting.getSolutions(TWO_SUBPROBLEM_CSP).toList();

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void getSolutions_innerSubproblemSolutionsCachedAcrossIterations() {
        val elementCount = new AtomicInteger();
        val counting = new IndependentSubproblemSolver(
                csp -> inner.getSolutions(csp).peek(a -> elementCount.incrementAndGet()));

        counting.getSolutions(TWO_SUBPROBLEM_CSP).limit(3).toList();

        // sp1 (V1!=V2 over {1,2,3}) contributes 2 elements, sp2 (V3!=V4 over {1,2}) has 2 elements
        // computed once and replayed from cache for sp1's second element — 2+2=4 total
        assertThat(elementCount.get()).isEqualTo(4);
    }
}
