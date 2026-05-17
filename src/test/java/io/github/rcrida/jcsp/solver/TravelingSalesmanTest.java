package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * N-city TSP with cities placed equidistantly on a unit circle (regular N-gon).
 * The optimal tour visits them in order around the perimeter — total distance N × 2sin(π/N).
 * For N=6 (regular hexagon) each edge has length 1, so the optimal tour cost is 6.0.
 */
public class TravelingSalesmanTest {
    static final Solver SOLVER = Solver.Factory.INSTANCE.createSolver();
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 12;

    static final double[][] CITIES = IntStream.range(0, N)
            .mapToObj(i -> new double[]{Math.cos(2 * Math.PI * i / N), Math.sin(2 * Math.PI * i / N)})
            .toArray(double[][]::new);

    static final List<Variable<Integer>> POSITIONS = IntStream.range(0, N)
            .mapToObj(i -> F.<Integer>create("pos" + i))
            .toList();

    static final double OPTIMAL_TOUR_LENGTH = N * 2 * Math.sin(Math.PI / N);

    static final ConstraintSatisfactionProblem TSP = buildTsp();

    static ConstraintSatisfactionProblem buildTsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        POSITIONS.forEach(pos -> builder.variableDomain(pos, IntRangeDomain.of(0, N - 1)));
        builder.allDiffConstraint(Set.copyOf(POSITIONS));
        return builder.build();
    }

    static double dist(int a, int b) {
        double dx = CITIES[a][0] - CITIES[b][0];
        double dy = CITIES[a][1] - CITIES[b][1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Reduced cost matrix lower bound:
    // decided edges contribute their exact cost; for the remaining freedom, each city must
    // still leave via exactly one outgoing edge (row minimum) and be entered via exactly one
    // incoming edge (column minimum after row reduction). The sum is always ≤ any completion.
    static double tourLength(Assignment a) {
        double INF = Double.MAX_VALUE / 2;
        double[][] cost = new double[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                cost[i][j] = i == j ? INF : dist(i, j);

        boolean[] outFixed = new boolean[N];
        boolean[] inFixed = new boolean[N];
        double lb = 0;

        for (int i = 0; i < N; i++) {
            Optional<Integer> from = a.getValue(POSITIONS.get(i));
            Optional<Integer> to = a.getValue(POSITIONS.get((i + 1) % N));
            if (from.isPresent() && to.isPresent()) {
                int f = from.get(), t = to.get();
                lb += dist(f, t);
                outFixed[f] = true;
                inFixed[t] = true;
                for (int k = 0; k < N; k++) {
                    cost[f][k] = k == t ? 0 : INF;
                    if (k != f) cost[k][t] = INF;
                }
            }
        }

        for (int i = 0; i < N; i++) {
            if (outFixed[i]) continue;
            double min = INF;
            for (int j = 0; j < N; j++) min = Math.min(min, cost[i][j]);
            if (min < INF) {
                lb += min;
                for (int j = 0; j < N; j++) if (cost[i][j] < INF) cost[i][j] -= min;
            }
        }

        for (int j = 0; j < N; j++) {
            if (inFixed[j]) continue;
            double min = INF;
            for (int i = 0; i < N; i++) min = Math.min(min, cost[i][j]);
            if (min < INF) lb += min;
        }

        return lb;
    }

    @Test
    void optimize_findsShortestTour() {
        val result = SOLVER.getSolution(TSP, TravelingSalesmanTest::tourLength);
        assertThat(result).isPresent();
        assertThat(tourLength(result.get())).isCloseTo(OPTIMAL_TOUR_LENGTH, offset(1e-9));
    }

    @Test
    void getSolutions_returnsImprovingTours() {
        val improving = SOLVER.getSolutions(TSP, TravelingSalesmanTest::tourLength).toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(tourLength(improving.get(i))).isLessThan(tourLength(improving.get(i - 1)));
        }
        assertThat(tourLength(improving.getLast())).isCloseTo(OPTIMAL_TOUR_LENGTH, offset(1e-9));
    }
}
