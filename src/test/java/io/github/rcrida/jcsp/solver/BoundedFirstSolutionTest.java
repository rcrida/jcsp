package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BoundedFirstSolutionTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("bfsX");
    static final Variable<Integer> Y = F.create("bfsY");

    /** Minimise x + y over {0..9}², so the cheapest solution is 0 and the costliest 18. */
    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(0, 9))
            .variableDomain(Y, IntRangeDomain.of(0, 9))
            .build();

    static final LinearObjective SUM = LinearObjective.builder()
            .coefficient(X, 1.0).coefficient(Y, 1.0).build();

    static Assignment at(int x, int y) {
        return Assignment.of(Map.<Variable<?>, Object>of(X, x, Y, y));
    }

    /**
     * The loosest cost the bound on {@code csp} still permits, read generically: narrowing an
     * {@link IntRangeDomain} by a cut does not necessarily leave an {@link IntRangeDomain} behind.
     */
    static int permittedSpan(ConstraintSatisfactionProblem csp) {
        return largest(csp, X) + largest(csp, Y);
    }

    static int largest(ConstraintSatisfactionProblem csp, Variable<Integer> variable) {
        return ((io.github.rcrida.jcsp.domains.DiscreteDomain<?>) csp.getDomain(variable))
                .stream().mapToInt(value -> (Integer) value).max().orElseThrow();
    }

    /**
     * A search that answers each question with the cheapest assignment its bound still permits, which
     * is what a real feasibility search would find if it happened to be lucky. Records the upper bound
     * it saw on each call, so a test can assert the sequence of targets the descent chose.
     */
    static IntFunction<Solver> cheapestPermitted(List<Integer> targetsSeen) {
        return budget -> csp -> {
            targetsSeen.add(permittedSpan(csp));
            return Stream.of(at(0, 0));
        };
    }

    static BoundedFirstSolution seeder(IntFunction<Solver> search) {
        return BoundedFirstSolution.builder().search(search).build();
    }

    @Test
    void noFirstSolution_seedsNothing() {
        val seeded = seeder(budget -> csp -> Stream.empty()).seed(CSP, SUM);

        assertThat(seeded).isEmpty();
    }

    @Test
    void aSearchThatStopsEarly_seedsNothingRatherThanThrowing() {
        // IncumbentSeeder's contract forbids throwing: branch-and-bound truncates silently, and
        // seeding is not the place to change that (ADR-0011, ADR-0043).
        val limited = seeder(budget -> csp -> { throw new LimitExceededException(new Statistics()); });
        val cancelled = seeder(budget -> csp -> { throw new SolverCancelledException(new Statistics()); });

        assertThat(limited.seed(CSP, SUM)).isEmpty();
        assertThat(cancelled.seed(CSP, SUM)).isEmpty();
    }

    @Test
    void anUnusableAnswer_seedsNothing() {
        // Incomplete, so its cost would be computed over unassigned variables. Neither a solution
        // nor a refutation, so the descent must not treat it as either.
        val seeded = seeder(budget -> csp -> Stream.of(Assignment.of(Map.<Variable<?>, Object>of(X, 1))))
                .seed(CSP, SUM);

        assertThat(seeded).isEmpty();
    }

    @Test
    void aNonLinearObjective_seedsTheFirstSolutionWithoutProbing() {
        // No LinearObjective means no exact cut, so a "bounded" probe would re-ask the question just
        // answered. The first solution is still worth having.
        val targets = new ArrayList<Integer>();

        val seeded = seeder(cheapestPermitted(targets)).seed(CSP, a -> 0.0);

        assertThat(seeded).contains(at(0, 0));
        assertThat(targets).as("exactly one question, the unbounded one").hasSize(1);
    }

    @Test
    void aFirstSolutionAlreadyAtTheLowerBound_isNotProbedAgainstAtAll() {
        // The stub returns cost 0, which is also the LP relaxation's lower bound, so the first
        // solution is already provably optimal and there is no gap left to probe into.
        val targets = new ArrayList<Integer>();

        val seeded = seeder(cheapestPermitted(targets)).seed(CSP, SUM);

        assertThat(seeded).contains(at(0, 0));
        assertThat(targets).as("the unbounded question only").containsExactly(18);
    }

    @Test
    void aRefutedProbe_raisesTheLowerBoundAndKeepsDescending() {
        // Answers the unbounded question with the costliest solution (18) and then refutes every
        // bounded one. Each refutation raises the lower bound, so targets march upwards and the
        // descent runs out of room rather than out of probes.
        // Discriminated by call order, not by the bound the probe sees: a cut of "x + y <= 15" leaves
        // both domains at 0..9, since either variable can still take 9 when the other takes 0.
        val targets = new ArrayList<Integer>();
        int[] call = {0};
        IntFunction<Solver> search = budget -> csp -> {
            targets.add(permittedSpan(csp));
            return call[0]++ == 0 ? Stream.of(at(9, 9)) : Stream.empty();
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).as("nothing beat the first solution").contains(at(9, 9));
        // 18 with a lower bound of 0 aims the first probe at 15; refuting it lifts the bound to 16,
        // so the next aims at 17, and refuting that lifts it to 18 and closes the gap.
        assertThat(targets).as("the unbounded question and two probes").hasSize(3);
    }

    @Test
    void anInconclusiveProbe_endsTheDescent() {
        // The band where a bounded question can be neither satisfied nor refuted: one such answer
        // ends the descent, since there is no reason to think a lower target fares better.
        val targets = new ArrayList<Integer>();
        int[] call = {0};
        IntFunction<Solver> search = budget -> csp -> {
            targets.add(permittedSpan(csp));
            if (call[0]++ == 0) {
                return Stream.of(at(9, 9));
            }
            throw new RestartsExhaustedException(1, new Statistics());
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).contains(at(9, 9));
        assertThat(targets).as("one unbounded question and exactly one probe before giving up").hasSize(2);
    }

    @Test
    void aProbeThatImproves_isAdoptedAndTheNextOneStartsFromItsRealCost() {
        // The second answer costs 4 while its target was looser than that, so the descent must
        // measure the next step from 4 rather than from the target it asked for.
        val targets = new ArrayList<Integer>();
        int[] call = {0};
        IntFunction<Solver> search = budget -> csp -> {
            targets.add(permittedSpan(csp));
            return switch (call[0]++) {
                case 0 -> Stream.of(at(9, 9));
                case 1 -> Stream.of(at(2, 2));
                default -> Stream.empty();
            };
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).contains(at(2, 2));
    }

    @Test
    void maxProbesOfZero_seedsTheFirstSolutionUnimproved() {
        val targets = new ArrayList<Integer>();

        val seeded = BoundedFirstSolution.builder()
                .search(cheapestPermitted(targets))
                .maxProbes(0)
                .build()
                .seed(CSP, SUM);

        assertThat(seeded).contains(at(0, 0));
        assertThat(targets).as("the unbounded question only").hasSize(1);
    }

    @Test
    void aProbeAnsweringNoBetterThanTheIncumbent_endsTheDescent() {
        // The bound asked for at most target, so an answer that costs as much as the incumbent means
        // the search ignored the bound. Nothing has been tightened, so there is nothing to tighten
        // from, and continuing would ask the same question again.
        val targets = new ArrayList<Integer>();
        IntFunction<Solver> search = budget -> csp -> {
            targets.add(permittedSpan(csp));
            return Stream.of(at(9, 9));
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).contains(at(9, 9));
        assertThat(targets).as("the unbounded question and one probe").hasSize(2);
    }

    @Test
    void anObjectiveWithNoExactCut_seedsTheFirstSolutionWithoutProbing() {
        // A fractional coefficient cannot be cut exactly, and an inexact cut would have to be
        // loosened by an epsilon to stay sound. Without a cut, a "bounded" probe is the unbounded
        // question over again, so the descent declines rather than spending a probe on it.
        val targets = new ArrayList<Integer>();
        val halfSum = LinearObjective.builder().coefficient(X, 0.5).coefficient(Y, 0.5).build();

        val seeded = seeder(cheapestPermitted(targets)).seed(CSP, halfSum);

        assertThat(seeded).contains(at(0, 0));
        assertThat(targets).as("the unbounded question only").hasSize(1);
    }

    @Test
    void aGapNarrowerThanOneWholeUnit_leavesNothingToProbeFor() {
        // 2x >= 9 relaxes to x >= 4.5 but forces x >= 5, so the first solution at 5 sits half a unit
        // above the lower bound. The step is at least 1, which would aim the probe below the bound
        // itself -- a question already known to be unsatisfiable, so there is nothing to ask.
        val bounded = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(0, 9))
                .linearConstraint(Map.of(X, 2), io.github.rcrida.jcsp.constraints.Operator.GEQ, 9)
                .build();
        val justX = LinearObjective.builder().coefficient(X, 1.0).build();
        val asked = new ArrayList<Integer>();
        IntFunction<Solver> search = budget -> csp -> {
            asked.add(largest(csp, X));
            return Stream.of(Assignment.of(Map.<Variable<?>, Object>of(X, 5)));
        };

        val seeded = seeder(search).seed(bounded, justX);

        assertThat(seeded).contains(Assignment.of(Map.<Variable<?>, Object>of(X, 5)));
        assertThat(asked).as("the unbounded question only").hasSize(1);
    }

    @Test
    void aCompleteButInconsistentAnswer_seedsNothing() {
        // x=0 violates 2x >= 9. Complete, so it passes the first half of validation, and cheaper
        // than any real solution -- exactly the answer that must not be trusted.
        val constrained = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(0, 9))
                .linearConstraint(Map.of(X, 2), io.github.rcrida.jcsp.constraints.Operator.GEQ, 9)
                .build();
        val justX = LinearObjective.builder().coefficient(X, 1.0).build();

        val seeded = seeder(budget -> csp -> Stream.of(Assignment.of(Map.<Variable<?>, Object>of(X, 0))))
                .seed(constrained, justX);

        assertThat(seeded).isEmpty();
    }

    @Test
    void builderRejectsNonsenseBudgets() {
        IntFunction<Solver> search = budget -> csp -> Stream.empty();

        assertThatThrownBy(() -> BoundedFirstSolution.builder().search(search).initialRestartBudget(0).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("initialRestartBudget");
        assertThatThrownBy(() -> BoundedFirstSolution.builder().search(search).probeRestartBudget(0).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("probeRestartBudget");
        assertThatThrownBy(() -> BoundedFirstSolution.builder().search(search).maxProbes(-1).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxProbes");
        assertThatThrownBy(() -> BoundedFirstSolution.builder().search(search).stepDivisor(0).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stepDivisor");
    }

    @Test
    void theTwoSearchesGetTheirOwnBudgets() {
        val budgets = new ArrayList<Integer>();
        IntFunction<Solver> search = budget -> {
            budgets.add(budget);
            return csp -> Stream.of(at(9, 9));
        };

        BoundedFirstSolution.builder().search(search).initialRestartBudget(500).probeRestartBudget(7)
                .build().seed(CSP, SUM);

        assertThat(budgets).as("one search per role, built once rather than once per probe")
                .containsExactly(500, 7);
    }
}
