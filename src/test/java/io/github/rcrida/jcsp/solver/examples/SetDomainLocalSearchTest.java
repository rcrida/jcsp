package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.LocalSolver;
import io.github.rcrida.jcsp.solver.assignmentfactory.GreedyAssignmentFactory;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link LocalSolver.Factory}'s min-conflicts/tabu-search chain (not just the backtracking
 * {@code Solver.Factory} chain, which is what {@code Prob010SocialGolfersTest} exercises) can solve
 * a CSP built entirely from {@link SetIntervalDomain} variables — the integration test for the
 * {@code SetDomainMoves} local-search neighbourhood generator.
 *
 * <p>Two teams drawn from an 8-person pool must be disjoint (no shared members), sized {@code
 * [3,5]} each — wide enough that add/remove moves are exercised, not only swap. A "core" group of
 * 1-2 people must be a subset of team A, exercising {@code subsetConstraint} alongside {@code
 * disjointConstraint} through the same local-search chain.
 */
public class SetDomainLocalSearchTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Set<String> POOL = Set.of("a", "b", "c", "d", "e", "f", "g", "h");

    static final Variable<Set<String>> TEAM_A = F.create("teamA");
    static final Variable<Set<String>> TEAM_B = F.create("teamB");
    static final Variable<Set<String>> CORE = F.create("core");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(TEAM_A, SetIntervalDomain.of(Set.of(), POOL, 3, 5))
            .variableDomain(TEAM_B, SetIntervalDomain.of(Set.of(), POOL, 3, 5))
            .variableDomain(CORE, SetIntervalDomain.of(Set.of(), POOL, 1, 2))
            .disjointConstraint(TEAM_A, TEAM_B)
            .subsetConstraint(CORE, TEAM_A)
            .build();

    @Test
    void getLocalSolution_randomSeed_findsAValidAssignment() {
        val solver = LocalSolver.Factory.INSTANCE.createLocalSolver(5, 300, RandomAssignmentFactory.INSTANCE);
        val solution = solver.getLocalSolution(CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }

    @Test
    void getLocalSolution_greedySeed_findsAValidAssignment() {
        val solver = LocalSolver.Factory.INSTANCE.createLocalSolver(5, 300, GreedyAssignmentFactory.INSTANCE);
        val solution = solver.getLocalSolution(CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }
}
