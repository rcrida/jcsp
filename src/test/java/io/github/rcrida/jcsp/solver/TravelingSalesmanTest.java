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
    static final int N = 6;

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

    // Only sums edges where both adjacent tour positions are assigned — a valid lower bound
    // for any non-negative distance function, regardless of the order variables are assigned.
    static double tourLength(Assignment a) {
        double cost = 0;
        for (int i = 0; i < N; i++) {
            Optional<Integer> from = a.getValue(POSITIONS.get(i));
            Optional<Integer> to = a.getValue(POSITIONS.get((i + 1) % N));
            if (from.isPresent() && to.isPresent())
                cost += dist(from.get(), to.get());
        }
        return cost;
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
