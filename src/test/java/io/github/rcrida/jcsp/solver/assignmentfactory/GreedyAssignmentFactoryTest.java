package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.val;
import io.github.rcrida.jcsp.solver.MinConflictsSolver;
import io.github.rcrida.jcsp.solver.examples.csplib.Prob054NQueensTest;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class GreedyAssignmentFactoryTest {

    @Test
    void greedyAssignmentIsCompleteForAllVariables() {
        val csp = Prob054NQueensTest.nQueens();
        val assignment = GreedyAssignmentFactory.INSTANCE.getAssignment(csp);
        assertThat(assignment.isComplete(csp)).isTrue();
    }

    @Test
    void greedyAssignmentHasFewerConflictsOnAverageThanRandom() {
        val csp = Prob054NQueensTest.nQueens();
        double avgGreedyViolations = IntStream.range(0, 20)
                .mapToLong(i -> csp.getConstraints().stream()
                        .filter(c -> !c.isSatisfiedBy(GreedyAssignmentFactory.INSTANCE.getAssignment(csp))).count())
                .average().orElseThrow();
        double avgRandomViolations = IntStream.range(0, 20)
                .mapToLong(i -> csp.getConstraints().stream()
                        .filter(c -> !c.isSatisfiedBy(RandomAssignmentFactory.INSTANCE.getAssignment(csp))).count())
                .average().orElseThrow();
        assertThat(avgGreedyViolations).isLessThan(avgRandomViolations);
    }

    @Test
    void greedyAssignmentCanSeedLocalSearch() {
        // Both the greedy seed and min-conflicts break ties via ThreadLocalRandom, so a single
        // attempt occasionally fails to converge within 500 steps. maxAttempts=5 runs independent
        // random restarts in parallel and returns on first success, making that residual failure
        // probability negligible without weakening what the test actually checks.
        val csp = Prob054NQueensTest.nQueens();
        val solver = MinConflictsSolver.of(5, 500, GreedyAssignmentFactory.INSTANCE);
        val solution = solver.getLocalSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }
}
