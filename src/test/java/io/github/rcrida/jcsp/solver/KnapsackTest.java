package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binary knapsack: choose which items to pack to maximise value without exceeding
 * the weight capacity. Each item is a 0/1 variable; the weight constraint is a
 * {@code linearConstraint}.
 *
 * <pre>
 *   Item | Weight | Value
 *   -----|--------|------
 *    x1  |   3    |   4
 *    x2  |   4    |   5
 *    x3  |   5    |   6
 *    x4  |   6    |   7
 *   Capacity: 10
 * </pre>
 *
 * Feasible subsets (total weight ≤ 10): {}, {x1}, {x2}, {x3}, {x4},
 * {x1,x2}, {x1,x3}, {x1,x4}, {x2,x3}, {x2,x4} — 10 in total.
 * Optimal selection: {x2, x4} with weight 10 and value 12.
 */
public class KnapsackTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X1 = F.create("x1");
    static final Variable<Integer> X2 = F.create("x2");
    static final Variable<Integer> X3 = F.create("x3");
    static final Variable<Integer> X4 = F.create("x4");
    static final IntRangeDomain BINARY = IntRangeDomain.of(0, 1);

    static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(X1, BINARY).variableDomain(X2, BINARY)
                .variableDomain(X3, BINARY).variableDomain(X4, BINARY)
                .linearConstraint(Map.of(X1, 3, X2, 4, X3, 5, X4, 6), Operator.LEQ, 10)
                .build();
    }

    @Test
    void feasibility_allValidSelections() {
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(problem())).hasSize(10);
    }

    @Test
    void optimization_maxValue() {
        // Maximise 4*x1 + 5*x2 + 6*x3 + 7*x4 by minimising its negation.
        // Optimal: x2=1, x4=1 → weight=10, value=12.
        // orElse(1) gives the optimistic lower bound for the negated objective: assume every
        // unassigned variable takes its maximum value (1), so the true value can only be lower.
        val result = Solver.Factory.INSTANCE.createSolver().getSolution(problem(),
                a -> -(4 * a.getValue(X1).orElse(1)
                      + 5 * a.getValue(X2).orElse(1)
                      + 6 * a.getValue(X3).orElse(1)
                      + 7 * a.getValue(X4).orElse(1)));
        assertThat(result).hasValueSatisfying(a -> {
            assertThat(a.getValue(X1)).hasValue(0);
            assertThat(a.getValue(X2)).hasValue(1);
            assertThat(a.getValue(X3)).hasValue(0);
            assertThat(a.getValue(X4)).hasValue(1);
        });
    }
}
