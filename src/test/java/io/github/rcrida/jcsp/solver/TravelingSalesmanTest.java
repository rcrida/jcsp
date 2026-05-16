package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4-city TSP: cities at the corners of a 2×2 square.
 * The optimal tour visits them in order around the perimeter — total distance 8.0.
 * Any tour that crosses a diagonal costs 4 + 4√2 ≈ 9.66.
 *
 * <pre>
 *   D(0,2) --- C(2,2)
 *     |           |
 *   A(0,0) --- B(2,0)
 * </pre>
 *
 * Variables pos0..pos3 represent which city is visited at each step.
 * allDiff ensures each city appears exactly once (a valid permutation).
 */
public class TravelingSalesmanTest {
    static final Solver SOLVER = Solver.Factory.INSTANCE.createSolver();
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final double[][] CITIES = {{0, 0}, {2, 0}, {2, 2}, {0, 2}};  // A, B, C, D

    static final Variable<Integer> POS0 = F.create("pos0");
    static final Variable<Integer> POS1 = F.create("pos1");
    static final Variable<Integer> POS2 = F.create("pos2");
    static final Variable<Integer> POS3 = F.create("pos3");

    static final ConstraintSatisfactionProblem TSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(POS0, IntRangeDomain.of(0, 3))
            .variableDomain(POS1, IntRangeDomain.of(0, 3))
            .variableDomain(POS2, IntRangeDomain.of(0, 3))
            .variableDomain(POS3, IntRangeDomain.of(0, 3))
            .allDiffConstraint(Set.of(POS0, POS1, POS2, POS3))
            .build();

    static double dist(int a, int b) {
        double dx = CITIES[a][0] - CITIES[b][0];
        double dy = CITIES[a][1] - CITIES[b][1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    static final List<Variable<Integer>> POSITIONS = List.of(POS0, POS1, POS2, POS3);

    // Sums only edges whose both endpoints are assigned — always a valid lower bound
    // since unaccounted edges have non-negative cost. The return edge is included only
    // when all positions are filled (complete tour).
    static double tourLength(Assignment a) {
        List<Integer> tour = POSITIONS.stream()
                .map(v -> a.getValue(v))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        double cost = 0;
        for (int i = 1; i < tour.size(); i++)
            cost += dist(tour.get(i - 1), tour.get(i));
        if (tour.size() == POSITIONS.size())
            cost += dist(tour.getLast(), tour.getFirst());
        return cost;
    }

    @Test
    void optimize_findsShortestTour() {
        val result = SOLVER.getSolution(TSP, TravelingSalesmanTest::tourLength);
        assertThat(result).isPresent();
        assertThat(tourLength(result.get())).isEqualTo(8.0);
    }

    @Test
    void getSolutions_returnsImprovingTours() {
        val improving = SOLVER.getSolutions(TSP, TravelingSalesmanTest::tourLength).toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(tourLength(improving.get(i))).isLessThan(tourLength(improving.get(i - 1)));
        }
        assertThat(tourLength(improving.getLast())).isEqualTo(8.0);
    }
}
