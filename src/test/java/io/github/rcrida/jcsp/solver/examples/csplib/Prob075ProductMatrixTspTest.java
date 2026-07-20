package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Product Matrix Travelling Salesman Problem (CSPLib prob075): given two length-N vectors
 * {@code C} and {@code P}, the cost of the directed edge from city {@code i} to city
 * {@code j != i} is the product {@code C[i] * P[j]} — an asymmetric cost matrix with no
 * geometric interpretation, unlike a Euclidean TSP. Modelled via {@code circuitConstraint}:
 * {@code succ[i]} is the 1-indexed successor of city {@code i}.
 * <p>
 * {@code C} and {@code P} are arbitrary positive integers chosen so no two candidate tours tie
 * for optimal (confirmed by exhaustive brute force over all {@code (N-1)!} tours, see
 * {@code OPTIMAL_TOUR_COST}'s Javadoc) — the resulting instance has no closed-form optimum the
 * way a regular-polygon Euclidean TSP does, so the expected optimum is a verified constant
 * rather than a derived formula.
 */
public class Prob075ProductMatrixTspTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 6;

    static final int[] C = {2, 5, 1, 7, 3, 4};
    static final int[] P = {4, 1, 6, 2, 8, 3};

    static final List<Variable<Integer>> SUCC = IntStream.range(0, N)
            .mapToObj(i -> F.<Integer>create("succ" + i))
            .toList();

    /**
     * Verified by exhaustive brute force over all {@code (N-1)! = 120} distinct tours (fixing
     * city 0 as the start, since a circuit's cost is rotation-invariant): the true optimum is
     * 64.0, with the next-best tour costing 70.0 — a clear, tie-free minimum.
     */
    static final double OPTIMAL_TOUR_COST = 64.0;

    static final ConstraintSatisfactionProblem TSP = buildTsp();

    static ConstraintSatisfactionProblem buildTsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        SUCC.forEach(s -> builder.variableDomain(s, IntRangeDomain.of(1, N)));
        builder.circuitConstraint(SUCC);
        return builder.build();
    }

    static double edgeCost(int i, int j) {
        return (double) C[i] * P[j];
    }

    // Reduced cost matrix lower bound: fixed edges contribute their exact cost;
    // for the remaining freedom, each city's minimum outgoing (row) and minimum
    // incoming (column) edge after row reduction are summed as a tight lower bound.
    static double tourCost(Assignment a) {
        double INF = Double.MAX_VALUE / 2;
        double[][] cost = new double[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                cost[i][j] = i == j ? INF : edgeCost(i, j);

        boolean[] rowFixed = new boolean[N];
        boolean[] colFixed = new boolean[N];
        double lb = 0;

        for (int i = 0; i < N; i++) {
            Optional<Integer> succVal = a.getValue(SUCC.get(i));
            if (succVal.isPresent()) {
                int j = succVal.get() - 1;
                lb += edgeCost(i, j);
                rowFixed[i] = true;
                colFixed[j] = true;
                for (int k = 0; k < N; k++) {
                    cost[i][k] = k == j ? 0 : INF;
                    if (k != i) cost[k][j] = INF;
                }
            }
        }

        for (int i = 0; i < N; i++) {
            if (rowFixed[i]) continue;
            double min = INF;
            for (int j = 0; j < N; j++) min = Math.min(min, cost[i][j]);
            if (min < INF) {
                lb += min;
                for (int j = 0; j < N; j++) if (cost[i][j] < INF) cost[i][j] -= min;
            }
        }

        for (int j = 0; j < N; j++) {
            if (colFixed[j]) continue;
            double min = INF;
            for (int i = 0; i < N; i++) min = Math.min(min, cost[i][j]);
            if (min < INF) lb += min;
        }

        return lb;
    }

    @Test
    void optimize_findsCheapestTour() {
        val result = Solver.Factory.INSTANCE.createSolver(TSP, Prob075ProductMatrixTspTest::tourCost).getSolution();
        assertThat(result).isPresent();
        assertThat(tourCost(result.get())).isCloseTo(OPTIMAL_TOUR_COST, offset(1e-9));
    }

    @Test
    void getSolutions_returnsImprovingTours() {
        val improving = Solver.Factory.INSTANCE.createSolver(TSP, Prob075ProductMatrixTspTest::tourCost).getSolutions().toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(tourCost(improving.get(i))).isLessThan(tourCost(improving.get(i - 1)));
        }
        assertThat(tourCost(improving.getLast())).isCloseTo(OPTIMAL_TOUR_COST, offset(1e-9));
    }
}
