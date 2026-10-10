package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.MinConflictsSolver;
import io.github.rcrida.jcsp.solver.examples.csplib.Prob054NQueensTest;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class GreedyAssignmentFactoryTest {

    static final Variable.Factory F = Variable.Factory.INSTANCE;

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

    @Test
    void greedyAssignment_setBoundedDomain_isCompleteAndWithinDomain() {
        val a = F.<Set<String>>create("a");
        val b = F.<Set<String>>create("b");
        val domain = SetIntervalDomain.of(Set.of(), Set.of("x", "y", "z", "w"), 2, 2);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain).variableDomain(b, domain)
                .disjointConstraint(a, b)
                .build();
        val assignment = GreedyAssignmentFactory.INSTANCE.getAssignment(csp);
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.getValue(a)).hasValueSatisfying(v -> assertThat(domain.contains(v)).isTrue());
        assertThat(assignment.getValue(b)).hasValueSatisfying(v -> assertThat(domain.contains(v)).isTrue());
    }

    /**
     * Draws per arm, which this comparison needs far more of than the {@link
     * Prob054NQueensTest#nQueens} one above. There, greedy averages 1.05 violations against random's
     * 5.37 and 20 draws never invert (measured over 500 comparisons). Here the single
     * {@code disjointConstraint} is violated on 50% of greedy draws against 84% of random ones, so
     * each average is a mean of Bernoulli trials only 0.33 apart: at 20 draws the comparison inverts
     * on about 0.85% of runs (measured over 2,000 comparisons, and observed failing once in CI),
     * which at 200 falls to roughly 1e-14. Neither factory can be seeded -- both break ties through
     * {@link java.util.concurrent.ThreadLocalRandom} -- so sample size is the only control available.
     */
    private static final int COMPARISON_DRAWS = 200;

    @Test
    void greedyAssignment_setBoundedDomain_hasFewerConflictsOnAverageThanRandom() {
        val a = F.<Set<String>>create("a2");
        val b = F.<Set<String>>create("b2");
        val domain = SetIntervalDomain.of(Set.of(), Set.of("x", "y", "z", "w"), 2, 2);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain).variableDomain(b, domain)
                .disjointConstraint(a, b)
                .build();
        double avgGreedyViolations = IntStream.range(0, COMPARISON_DRAWS)
                .mapToLong(i -> csp.getConstraints().stream()
                        .filter(c -> !c.isSatisfiedBy(GreedyAssignmentFactory.INSTANCE.getAssignment(csp))).count())
                .average().orElseThrow();
        double avgRandomViolations = IntStream.range(0, COMPARISON_DRAWS)
                .mapToLong(i -> csp.getConstraints().stream()
                        .filter(c -> !c.isSatisfiedBy(RandomAssignmentFactory.INSTANCE.getAssignment(csp))).count())
                .average().orElseThrow();
        assertThat(avgGreedyViolations).isLessThanOrEqualTo(avgRandomViolations);
    }
}
