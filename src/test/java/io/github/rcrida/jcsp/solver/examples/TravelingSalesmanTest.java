package io.github.rcrida.jcsp.solver.examples;

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
 * N-city TSP with cities placed equidistantly on a unit circle (regular N-gon),
 * modelled via circuitConstraint: succ[i] is the 1-indexed successor of city i+1.
 * The optimal tour visits them in order around the perimeter — total distance N × 2sin(π/N).
 */
public class TravelingSalesmanTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 12;

    static final double[][] CITIES = IntStream.range(0, N)
            .mapToObj(i -> new double[]{Math.cos(2 * Math.PI * i / N), Math.sin(2 * Math.PI * i / N)})
            .toArray(double[][]::new);

    static final List<Variable<Integer>> SUCC = IntStream.range(0, N)
            .mapToObj(i -> F.<Integer>create("succ" + i))
            .toList();

    static final double OPTIMAL_TOUR_LENGTH = N * 2 * Math.sin(Math.PI / N);

    static final ConstraintSatisfactionProblem TSP = buildTsp();

    static ConstraintSatisfactionProblem buildTsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        SUCC.forEach(s -> builder.variableDomain(s, IntRangeDomain.of(1, N)));
        builder.circuitConstraint(SUCC);
        return builder.build();
    }

    static double dist(int a, int b) {
        double dx = CITIES[a][0] - CITIES[b][0];
        double dy = CITIES[a][1] - CITIES[b][1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Reduced cost matrix lower bound: fixed edges contribute their exact cost;
    // for the remaining freedom, each city's minimum outgoing (row) and minimum
    // incoming (column) edge after row reduction are summed as a tight lower bound.
    static double tourLength(Assignment a) {
        double INF = Double.MAX_VALUE / 2;
        double[][] cost = new double[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                cost[i][j] = i == j ? INF : dist(i, j);

        boolean[] rowFixed = new boolean[N];
        boolean[] colFixed = new boolean[N];
        double lb = 0;

        for (int i = 0; i < N; i++) {
            Optional<Integer> succVal = a.getValue(SUCC.get(i));
            if (succVal.isPresent()) {
                int j = succVal.get() - 1;
                lb += dist(i, j);
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
    void optimize_findsShortestTour() {
        val result = Solver.Factory.INSTANCE.createSolver(TSP, TravelingSalesmanTest::tourLength).getSolution();
        assertThat(result).isPresent();
        assertThat(tourLength(result.get())).isCloseTo(OPTIMAL_TOUR_LENGTH, offset(1e-9));
    }

    @Test
    void getSolutions_returnsImprovingTours() {
        val improving = Solver.Factory.INSTANCE.createSolver(TSP, TravelingSalesmanTest::tourLength).getSolutions().toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(tourLength(improving.get(i))).isLessThan(tourLength(improving.get(i - 1)));
        }
        assertThat(tourLength(improving.getLast())).isCloseTo(OPTIMAL_TOUR_LENGTH, offset(1e-9));
    }
}
