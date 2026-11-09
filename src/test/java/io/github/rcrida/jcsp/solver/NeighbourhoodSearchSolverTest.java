package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.neighbourhood.DecisionSource;
import io.github.rcrida.jcsp.solver.neighbourhood.LargeNeighbourhoodSearch;
import io.github.rcrida.jcsp.solver.neighbourhood.NeighbourhoodGenerator;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimise {@code x + y + z} over {@code 0..3}: the optimum is 0, and {@code (3, 3, 3)} is a poor start. */
class NeighbourhoodSearchSolverTest {

    /** Delegates to the random generator and counts how it is used. */
    private static final class Recording implements NeighbourhoodGenerator.Factory {
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger freed = new AtomicInteger();

        @Override
        public NeighbourhoodGenerator create(ConstraintSatisfactionProblem csp, ToDoubleFunction<Assignment> objective) {
            created.incrementAndGet();
            NeighbourhoodGenerator inner = NeighbourhoodGenerator.Factory.RANDOM.create(csp, objective);
            return (decisions, incumbent, count, random) -> {
                freed.incrementAndGet();
                return inner.free(decisions, incumbent, count, random);
            };
        }
    }

    private final ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder =
            ConstraintSatisfactionProblem.builder();
    private final Variable<Integer> x = builder.createVariable("x", IntRangeDomain.of(0, 3));
    private final Variable<Integer> y = builder.createVariable("y", IntRangeDomain.of(0, 3));
    private final Variable<Integer> z = builder.createVariable("z", IntRangeDomain.of(0, 3));
    private final ConstraintSatisfactionProblem csp = builder.build();
    private final ToDoubleFunction<Assignment> sum = a ->
            a.getValue(x).orElse(0) + a.getValue(y).orElse(0) + a.getValue(z).orElse(0);
    private final Recording generator = new Recording();

    private Optional<Assignment> start(int value) {
        return Optional.of(Assignment.of(Map.of(x, value, y, value, z, value)));
    }

    private BranchAndBoundSolver complete(ToDoubleFunction<Assignment> objective, IncumbentSeeder seeder,
                                          RestartRandomization randomization, Inference inference) {
        return BranchAndBoundSolver.builder()
                .objective(objective)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(inference)
                .incumbentSeeder(seeder)
                .restartRandomization(randomization)
                .build();
    }

    private BranchAndBoundSolver complete(IncumbentSeeder seeder) {
        return complete(sum, seeder, RestartRandomization.NONE, Solver.Factory.FULL_PROPAGATION_INFERENCE);
    }

    /** Neighbourhood runs from the first, freeing about half the decisions, with generous cutoffs unless given. */
    private LargeNeighbourhoodSearch eager(long repairFailures, long repairNodes) {
        return new LargeNeighbourhoodSearch(0, 0.5, repairFailures, repairNodes, 1.0, 0.05, 20_000, 0.1,
                DecisionSource.Factory.REPLAY, List.of(generator));
    }

    private List<Double> costs(Solver solver, ConstraintSatisfactionProblem problem, ToDoubleFunction<Assignment> objective) {
        try (Stream<Assignment> solutions = solver.getSolutions(problem)) {
            return solutions.map(objective::applyAsDouble).collect(Collectors.toList());
        }
    }

    @Test
    void withNoGeneratorsItIsTheCompleteSearchAlone() {
        BranchAndBoundSolver plain = complete((c, o) -> start(3));
        List<Double> expected = costs(plain, csp, sum);

        List<Double> actual = costs(new NeighbourhoodSearchSolver(plain, LargeNeighbourhoodSearch.OFF), csp, sum);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getLast()).isZero();
    }

    @Test
    void aNeighbourhoodRunImprovesAPoorStartingSolution() {
        BranchAndBoundSolver plain = complete((c, o) -> start(3));

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), csp, sum);

        // The seed, then what the neighbourhood run found with one decision still fixed at 3, then the
        // optimum from the complete search that follows.
        assertThat(costs.getFirst()).isEqualTo(9.0);
        assertThat(costs.get(1)).isBetween(3.0, 8.0);
        assertThat(costs.getLast()).isZero();
        assertThat(generator.created.get()).isEqualTo(1);
        assertThat(generator.freed.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aNeighbourhoodRunThatFindsNothingLeavesTheCompleteSearchToFinish() {
        BranchAndBoundSolver plain = complete((c, o) -> start(0));

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), csp, sum);

        assertThat(costs).containsExactly(0.0);
        assertThat(generator.freed.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aRunCutOffByItsNodeBudgetStillLeavesTheOptimumToTheCompleteSearch() {
        BranchAndBoundSolver plain = complete((c, o) -> start(3));

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 1)), csp, sum);

        assertThat(costs.getLast()).isZero();
        assertThat(generator.freed.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aRunCutOffByItsFailureBudgetStillLeavesTheOptimumToTheCompleteSearch() {
        // Six variables that must differ, over just six values: searching below a fixing fails at once.
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder permutation = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> vars = new ArrayList<>();
        for (int i = 0; i < 6; i++) vars.add(permutation.createVariable("p" + i, IntRangeDomain.of(0, 5)));
        permutation.allDiffConstraint(new HashSet<>(vars));
        ConstraintSatisfactionProblem problem = permutation.build();
        ToDoubleFunction<Assignment> weighted = a -> {
            double total = 0;
            for (int i = 0; i < vars.size(); i++) total += (i + 1) * a.getValue(vars.get(i)).orElse(0);
            return total;
        };
        Assignment identity = Assignment.of(Map.of(vars.get(0), 5, vars.get(1), 4, vars.get(2), 3,
                vars.get(3), 2, vars.get(4), 1, vars.get(5), 0));
        BranchAndBoundSolver plain = complete(weighted, (c, o) -> Optional.of(identity), RestartRandomization.NONE,
                Solver.Factory.FULL_PROPAGATION_INFERENCE);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(1, 10_000)), problem, weighted);

        // Optimum: the largest variable gets 0 and so on up: 6*0 + 5*1 + 4*2 + 3*3 + 2*4 + 1*5 = 35.
        assertThat(costs.getLast()).isEqualTo(35.0);
    }

    @Test
    void withoutAStartingSolutionTheCompleteSearchFindsOneFirst() {
        BranchAndBoundSolver plain = complete(sum, null, RestartRandomization.NONE, Solver.Factory.FULL_PROPAGATION_INFERENCE);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), csp, sum);

        assertThat(costs.getLast()).isZero();
    }

    @Test
    void aLimitReachedDuringANeighbourhoodRunEndsTheStream() {
        BranchAndBoundSolver limited = BranchAndBoundSolver.builder()
                .objective(sum)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .incumbentSeeder((c, o) -> start(3))
                .limits(SolverLimits.ofNodes(3))
                .build();

        List<Double> costs = costs(new NeighbourhoodSearchSolver(limited, eager(200, 10_000)), csp, sum);

        // The seed, and whatever the run found before the limit: the optimum was never reached.
        assertThat(costs.getFirst()).isEqualTo(9.0);
        assertThat(costs.getLast()).isGreaterThan(0.0);
    }

    @Test
    void aProblemWithOneDecisionHasNoNeighbourhoodToSearch() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder single = ConstraintSatisfactionProblem.builder();
        Variable<Integer> only = single.createVariable("only", IntRangeDomain.of(0, 3));
        ConstraintSatisfactionProblem problem = single.build();
        ToDoubleFunction<Assignment> cost = a -> a.getValue(only).orElse(0);
        BranchAndBoundSolver plain = complete(cost, (c, o) -> Optional.of(Assignment.of(Map.of(only, 3))),
                RestartRandomization.NONE, Solver.Factory.FULL_PROPAGATION_INFERENCE);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), problem, cost);

        assertThat(costs.getLast()).isZero();
        assertThat(generator.freed.get()).isZero();
    }

    @Test
    void cancellationWhileSettingUpARunEndsItQuietly() {
        Inference cancelling = (problem, variable, assignment) -> {
            throw new SolverCancelledException(new Statistics());
        };
        BranchAndBoundSolver plain = complete(sum, (c, o) -> start(3), RestartRandomization.NONE, cancelling);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), csp, sum);

        assertThat(costs).containsExactly(9.0);
    }

    @Test
    void aProblemWithAContinuousVariableGetsNoNeighbourhoods() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder mixed = ConstraintSatisfactionProblem.builder();
        Variable<Integer> n = mixed.createVariable("n", IntRangeDomain.of(0, 3));
        Variable<Double> c = mixed.createVariable("c", IntervalDomain.of(0.0, 1.0));
        ConstraintSatisfactionProblem problem = mixed.build();
        LinearObjective objective = LinearObjective.builder().coefficient(n, 1.0).coefficient(c, 1.0).build();
        BranchAndBoundSolver plain = complete(objective, null, RestartRandomization.NONE,
                Solver.Factory.FULL_PROPAGATION_INFERENCE);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), problem, objective);

        assertThat(costs.getLast()).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(generator.created.get()).isZero();
    }

    @Test
    void aLinearObjectiveCutIsAppliedToTheNeighbourhood() {
        LinearObjective linear = LinearObjective.builder().coefficient(x, 1.0).coefficient(y, 1.0).coefficient(z, 1.0).build();
        BranchAndBoundSolver plain = complete(linear, (c, o) -> start(3), RestartRandomization.NONE,
                Solver.Factory.FULL_PROPAGATION_INFERENCE);

        List<Double> costs = costs(new NeighbourhoodSearchSolver(plain, eager(200, 10_000)), csp, linear);

        assertThat(costs.getFirst()).isEqualTo(9.0);
        assertThat(costs.getLast()).isZero();
        assertThat(generator.freed.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aSeededSolveRepeats() {
        Set<List<Double>> seen = new HashSet<>();
        for (int run = 0; run < 3; run++) {
            Recording fresh = new Recording();
            LargeNeighbourhoodSearch search = new LargeNeighbourhoodSearch(0, 0.5, 200, 10_000, 1.0, 0.05, 20_000, 0.1,
                    DecisionSource.Factory.REPLAY, List.of(fresh));
            BranchAndBoundSolver plain = complete(sum, (c, o) -> start(3), RestartRandomization.seeded(11),
                    Solver.Factory.FULL_PROPAGATION_INFERENCE);

            seen.add(costs(new NeighbourhoodSearchSolver(plain, search), csp, sum));
        }

        assertThat(seen).hasSize(1);
    }

    @Test
    void theConfigWiresItIntoTheOptimizationChain() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder permutation = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> vars = new ArrayList<>();
        for (int i = 0; i < 6; i++) vars.add(permutation.createVariable("q" + i, IntRangeDomain.of(0, 5)));
        permutation.allDiffConstraint(new HashSet<>(vars));
        ConstraintSatisfactionProblem problem = permutation.build();
        ToDoubleFunction<Assignment> total = a -> {
            double sumOfValues = 0;
            for (Variable<Integer> v : vars) sumOfValues += (v.getIndex() + 1) * a.getValue(v).orElse(0);
            return sumOfValues;
        };
        SolverConfig config = SolverConfig.builder().largeNeighbourhoodSearch(eager(200, 10_000)).build();

        Optional<Assignment> best = Solver.Factory.INSTANCE.createSolver(problem, total, config).getSolution();

        assertThat(best).isPresent();
        assertThat(generator.created.get()).isGreaterThanOrEqualTo(1);
    }
}
