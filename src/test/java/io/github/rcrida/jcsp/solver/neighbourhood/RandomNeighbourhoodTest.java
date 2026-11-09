package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RandomNeighbourhoodTest {
    private final ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder =
            ConstraintSatisfactionProblem.builder();
    private final List<Variable<?>> decisions = new ArrayList<>();

    private NeighbourhoodGenerator generator() {
        for (int i = 0; i < 8; i++) decisions.add(builder.createVariable("d" + i, IntRangeDomain.of(0, 9)));
        return NeighbourhoodGenerator.Factory.RANDOM.create(builder.build(), assignment -> 0.0);
    }

    @Test
    void freesExactlyTheRequestedNumberOfDistinctDecisions() {
        NeighbourhoodGenerator generator = generator();

        for (int count = 1; count < decisions.size(); count++) {
            Set<Variable<?>> freed = generator.free(decisions, Assignment.empty(), count, new Random(count));

            assertThat(freed).hasSize(count).isSubsetOf(decisions);
        }
    }

    @Test
    void repeatsForTheSameSeedAndVariesWithIt() {
        NeighbourhoodGenerator generator = generator();

        Set<Variable<?>> first = generator.free(decisions, Assignment.empty(), 3, new Random(7));
        Set<Variable<?>> again = generator.free(decisions, Assignment.empty(), 3, new Random(7));
        Set<Set<Variable<?>>> seen = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) seen.add(generator.free(decisions, Assignment.empty(), 3, new Random(seed)));

        assertThat(again).isEqualTo(first);
        assertThat(seen.size()).isGreaterThan(5);
    }

    @Test
    void leavesTheDecisionListAlone() {
        NeighbourhoodGenerator generator = generator();
        List<Variable<?>> before = new ArrayList<>(decisions);

        generator.free(decisions, Assignment.empty(), 4, new Random(1));

        assertThat(decisions).isEqualTo(before);
    }
}
