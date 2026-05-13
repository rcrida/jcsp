package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.val;
import io.github.rcrida.jcsp.solver.MinConflictsSolver;
import io.github.rcrida.jcsp.solver.NQueensTest;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class GreedyAssignmentFactoryTest {

    @Test
    void greedyAssignmentIsCompleteForAllVariables() {
        val csp = NQueensTest.nQueens();
        val assignment = GreedyAssignmentFactory.INSTANCE.getAssignment(csp);
        assertThat(assignment.isComplete(csp)).isTrue();
    }

    @Test
    void greedyAssignmentHasFewerConflictsOnAverageThanRandom() {
        val csp = NQueensTest.nQueens();
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
        val csp = NQueensTest.nQueens();
        val solver = new MinConflictsSolver(500);
        val solution = solver.getLocalSolution(csp, GreedyAssignmentFactory.INSTANCE);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(csp)).isTrue();
    }
}
