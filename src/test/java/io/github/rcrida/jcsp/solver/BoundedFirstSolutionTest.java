package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * The cost bound the question put to a search carries, read from the {@link ObjectiveCut} added to
     * {@code asked} rather than from its domains. The cut is a constraint and narrows no domain of its
     * own accord -- a bound on {@code x + y} leaves both 0..9, since either can still take 9 when the
     * other takes 0 -- so reading domains cannot see a target at all; see {@link ObjectiveCut#enforce}.
     * Identified as the constraint {@code asked} has and {@code base} does not, which is exact even
     * when {@code base} carries {@link LinearBoundConstraint}s of its own. The unbounded question
     * carries no cut and reports the loosest cost the declared domains permit, so a recorded sequence
     * reads as one descent.
     */
    static int boundAsked(ConstraintSatisfactionProblem base, ConstraintSatisfactionProblem asked) {
        return cutBound(base, asked).orElseGet(() -> permittedSpan(asked));
    }

    /** As {@link #boundAsked}, for an objective over {@link #X} alone. */
    static int boundAskedOverX(ConstraintSatisfactionProblem base, ConstraintSatisfactionProblem asked) {
        return cutBound(base, asked).orElseGet(() -> largest(asked, X));
    }

    static Optional<Integer> cutBound(ConstraintSatisfactionProblem base, ConstraintSatisfactionProblem asked) {
        return asked.getConstraints().stream()
                .filter(constraint -> !base.getConstraints().contains(constraint))
                .filter(LinearBoundConstraint.class::isInstance)
                .map(constraint -> ((LinearBoundConstraint<?>) constraint).getBound().intValue())
                .findFirst();
    }

    /**
     * The loosest cost {@code csp}'s domains still permit, read generically: narrowing an
     * {@link IntRangeDomain} does not necessarily leave an {@link IntRangeDomain} behind.
     */
    static int permittedSpan(ConstraintSatisfactionProblem csp) {
        return largest(csp, X) + largest(csp, Y);
    }

    static int largest(ConstraintSatisfactionProblem csp, Variable<Integer> variable) {
        return ((io.github.rcrida.jcsp.domains.DiscreteDomain<?>) csp.getDomain(variable))
                .stream().mapToInt(value -> (Integer) value).max().orElseThrow();
    }

    /**
     * A search that answers each question with the cheapest assignment there is, which is what a real
     * feasibility search would find if it happened to be lucky. Records the bound it was asked about
     * on each call, so a test can assert the sequence of targets the descent chose.
     */
    static BoundedFirstSolution.SearchFactory cheapestPermitted(List<Integer> targetsSeen) {
        return (searchCsp, budget, searchIndex) -> csp -> {
            targetsSeen.add(boundAsked(CSP, searchCsp));
            return Stream.of(at(0, 0));
        };
    }

    static BoundedFirstSolution seeder(BoundedFirstSolution.SearchFactory search) {
        return BoundedFirstSolution.builder().search(search).build();
    }

    @Test
    void noFirstSolution_seedsNothing() {
        val seeded = seeder((searchCsp, budget, searchIndex) -> csp -> Stream.empty()).seed(CSP, SUM);

        assertThat(seeded).isEmpty();
    }

    @Test
    void aSearchThatStopsEarly_seedsNothingRatherThanThrowing() {
        // IncumbentSeeder's contract forbids throwing: branch-and-bound truncates silently, and
        // seeding is not the place to change that (ADR-0011, ADR-0043).
        val limited = seeder((searchCsp, budget, searchIndex) -> csp -> { throw new LimitExceededException(new Statistics()); });
        val cancelled = seeder((searchCsp, budget, searchIndex) -> csp -> { throw new SolverCancelledException(new Statistics()); });

        assertThat(limited.seed(CSP, SUM)).isEmpty();
        assertThat(cancelled.seed(CSP, SUM)).isEmpty();
    }

    @Test
    void anUnusableAnswer_seedsNothing() {
        // Incomplete, so its cost would be computed over unassigned variables. Neither a solution
        // nor a refutation, so the descent must not treat it as either.
        val seeded = seeder((searchCsp, budget, searchIndex) -> csp -> Stream.of(Assignment.of(Map.<Variable<?>, Object>of(X, 1))))
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
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            targets.add(boundAsked(CSP, searchCsp));
            return call[0]++ == 0 ? Stream.of(at(9, 9)) : Stream.empty();
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).as("nothing beat the first solution").contains(at(9, 9));
        // 18 with a lower bound of 0 aims the first probe at 15; refuting it lifts the bound to 16,
        // so the next aims at 17, and refuting that lifts it to 18 and closes the gap.
        assertThat(targets).as("the unbounded question and two probes").hasSize(3);
    }

    @Test
    void everyProbeIsHandedItsBoundAsAConstraint_notAsNarrowedDomains() {
        // The regression this guards. A bound spread across several objective variables narrows no
        // domain at all -- x + y <= 15 leaves both 0..9 -- so a probe given only what one propagation
        // pass narrowed is given back the unbounded problem, answers it with something no better than
        // the incumbent, and ends the descent after a single probe having learned nothing. Both halves
        // are asserted: that the bound is there as a constraint, and that the domains cannot show it.
        val cuts = new ArrayList<Integer>();
        int[] call = {0};
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            cutBound(CSP, searchCsp).ifPresent(cuts::add);
            assertThat(permittedSpan(searchCsp)).as("this bound narrows no domain").isEqualTo(18);
            return call[0]++ == 0 ? Stream.of(at(9, 9)) : Stream.empty();
        };

        seeder(search).seed(CSP, SUM);

        // 18 against a lower bound of 0 aims the first probe at 15; refuting it lifts the bound to 16,
        // so the next aims at 17.
        assertThat(cuts).as("one cut per probe, the unbounded question carrying none").containsExactly(15, 17);
    }

    @Test
    void everyProbeGetsItsOwnSearchIndex() {
        // Same shape as the refuted-probe descent above, which runs two probes, so the indices
        // actually advance rather than being asserted over a single one.
        val indices = new ArrayList<Integer>();
        int[] call = {0};
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            indices.add(searchIndex);
            return call[0]++ == 0 ? Stream.of(at(9, 9)) : Stream.empty();
        };

        seeder(search).seed(CSP, SUM);

        assertThat(indices).as("the first solution, then one index per probe")
                .containsExactly(0, 1, 2);
    }

    @Test
    void anInconclusiveProbe_endsTheDescent() {
        // The band where a bounded question can be neither satisfied nor refuted: one such answer
        // ends the descent, since there is no reason to think a lower target fares better.
        val targets = new ArrayList<Integer>();
        int[] call = {0};
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            targets.add(boundAsked(CSP, searchCsp));
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
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            targets.add(boundAsked(CSP, searchCsp));
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
    void aProbeAnsweringOutsideItsOwnBound_isNotTrusted() {
        // A search that ignores the bound it was given. Its answer costs 18 against a probe asking for
        // at most 15, and the cut is a constraint of the problem it was asked about, so validation
        // rejects it: neither a solution nor a refutation, which ends the descent with the incumbent
        // intact rather than adopting an answer that is no improvement at all.
        val targets = new ArrayList<Integer>();
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            targets.add(boundAsked(CSP, searchCsp));
            return Stream.of(at(9, 9));
        };

        val seeded = seeder(search).seed(CSP, SUM);

        assertThat(seeded).as("the unbounded first solution, unimproved").contains(at(9, 9));
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
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> {
            asked.add(boundAskedOverX(bounded, searchCsp));
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

        val seeded = seeder((searchCsp, budget, searchIndex) -> csp -> Stream.of(Assignment.of(Map.<Variable<?>, Object>of(X, 0))))
                .seed(constrained, justX);

        assertThat(seeded).isEmpty();
    }

    @Test
    void builderRejectsNonsenseBudgets() {
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> csp -> Stream.empty();

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
        val requests = new ArrayList<String>();
        BoundedFirstSolution.SearchFactory search = (searchCsp, budget, searchIndex) -> {
            requests.add(budget + "@" + searchIndex);
            return csp -> Stream.of(at(9, 9));
        };

        BoundedFirstSolution.builder().search(search).initialRestartBudget(500).probeRestartBudget(7)
                .build().seed(CSP, SUM);

        // The index matters as much as the budget: each search has to draw its tie-breaking from its
        // own stream rather than from wherever the previous one left a shared driver, which is what
        // RestartRandomization#forSearch keys on. One probe only, because this stub answers every
        // bound with the same cost and the descent stops as soon as a probe fails to improve.
        assertThat(requests).as("the first solution is search 0 and each probe is the next")
                .containsExactly("500@0", "7@1");
    }
}
