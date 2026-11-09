package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A star: one centre not-equal to each of thirteen leaves, all fourteen also all-different. The centre is
 * related to every leaf by a binary constraint, each leaf only to the centre, and the all-different is too
 * wide to relate anything in particular.
 */
class CostWeightedNeighbourhoodTest {
    private static final int LEAVES = 13;

    private final ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder =
            ConstraintSatisfactionProblem.builder();
    private final Variable<Integer> centre = builder.createVariable("centre", IntRangeDomain.of(0, 20));
    private final List<Variable<Integer>> leaves = new ArrayList<>();
    private final List<Variable<?>> decisions = new ArrayList<>();
    private ConstraintSatisfactionProblem csp;

    CostWeightedNeighbourhoodTest() {
        decisions.add(centre);
        for (int i = 0; i < LEAVES; i++) {
            Variable<Integer> leaf = builder.createVariable("leaf" + i, IntRangeDomain.of(0, 20));
            leaves.add(leaf);
            decisions.add(leaf);
            builder.notEqualsConstraint(centre, leaf);
        }
        List<Variable<Integer>> all = new ArrayList<>(leaves);
        all.add(centre);
        builder.allDiffConstraint(new LinkedHashSet<>(all));
        csp = builder.build();
    }

    private Assignment incumbent(int centreValue, int leaf0Value) {
        Map<Variable<?>, Object> values = new HashMap<>();
        values.put(centre, centreValue);
        for (int i = 0; i < LEAVES; i++) values.put(leaves.get(i), i == 0 ? leaf0Value : 10 + i);
        return Assignment.of(values);
    }

    /** Cost of the centre is leaf0's term; cost of each leaf is the centre's term. */
    private ToDoubleFunction<Assignment> objective(double leaf0Coefficient) {
        return LinearObjective.builder().coefficient(leaves.get(0), leaf0Coefficient).coefficient(centre, 1.0).build();
    }

    private NeighbourhoodGenerator generator(ToDoubleFunction<Assignment> objective) {
        return NeighbourhoodGenerator.Factory.COST_WEIGHTED.create(csp, objective);
    }

    @Test
    void freesExactlyTheRequestedNumberOfDistinctDecisions() {
        Assignment incumbent = incumbent(3, 5);

        for (int count = 1; count < decisions.size(); count++) {
            Set<Variable<?>> freed = generator(objective(10.0)).free(decisions, incumbent, count, new Random(count));

            assertThat(freed).hasSize(count).isSubsetOf(decisions);
        }
    }

    @Test
    void favoursTheDecisionThatCostsTheObjectiveMost() {
        Assignment incumbent = incumbent(3, 5);
        int centreChosen = 0;

        for (int seed = 0; seed < 200; seed++) {
            Set<Variable<?>> freed = generator(objective(10.0)).free(decisions, incumbent, 1, new Random(seed));
            if (freed.contains(centre)) centreChosen++;
        }

        // The centre is in a pool of ten of the fourteen about 71% of the time and wins whenever it is.
        assertThat(centreChosen).isGreaterThan(110);
    }

    @Test
    void doesNotFreeTheSameDecisionEveryTime() {
        Assignment incumbent = incumbent(3, 3);
        NeighbourhoodGenerator generator = generator(objective(10.0));
        int centreChosen = 0;

        for (int call = 0; call < 40; call++) {
            if (generator.free(decisions, incumbent, 1, new Random(call)).contains(centre)) centreChosen++;
        }

        assertThat(centreChosen).isBetween(3, 35);
    }

    @Test
    void withoutALinearObjectiveEveryCostIsZeroButTheFreedStillSpreadAround() {
        Assignment incumbent = incumbent(3, 5);
        Set<Variable<?>> everFreed = new LinkedHashSet<>();
        NeighbourhoodGenerator generator = generator(assignment -> 0.0);

        for (int call = 0; call < 30; call++) {
            Set<Variable<?>> freed = generator.free(decisions, incumbent, 3, new Random(call));
            assertThat(freed).hasSize(3);
            everFreed.addAll(freed);
        }

        assertThat(everFreed.size()).isGreaterThan(8);
    }

    @Test
    void costsAreWorkedOutAgainForANewIncumbent() {
        NeighbourhoodGenerator generator = generator(objective(10.0));
        Assignment first = incumbent(3, 5);
        Assignment second = incumbent(3, 0);

        generator.free(decisions, first, 2, new Random(1));
        Set<Variable<?>> sameAgain = generator.free(decisions, first, 2, new Random(1));
        Set<Variable<?>> other = generator.free(decisions, second, 2, new Random(1));

        assertThat(sameAgain).hasSize(2);
        assertThat(other).hasSize(2);
    }

    @Test
    void relatedDecisionsAreDrawnFromTheNeighboursOfThePreviousOne() {
        Assignment incumbent = incumbent(3, 5);

        // Many seeds, enough picks that both the neighbour pools and the random top-ups are used, from the
        // centre (thirteen neighbours, more than a tournament) and from a leaf (one neighbour, the centre).
        for (int seed = 0; seed < 150; seed++) {
            Set<Variable<?>> freed = generator(objective(10.0)).free(decisions, incumbent, 9, new Random(seed));

            assertThat(freed).hasSize(9).isSubsetOf(decisions);
        }
    }

    @Test
    void aSeededChoiceRepeats() {
        Assignment incumbent = incumbent(3, 5);

        Set<Variable<?>> first = generator(objective(10.0)).free(decisions, incumbent, 6, new Random(42));
        Set<Variable<?>> again = generator(objective(10.0)).free(decisions, incumbent, 6, new Random(42));

        assertThat(again).isEqualTo(first);
    }
}
