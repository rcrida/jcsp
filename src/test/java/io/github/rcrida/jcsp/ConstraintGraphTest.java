package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstraintGraphTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    private static ConstraintSatisfactionProblem randomProblem(Random rng, int id) {
        int n = 2 + rng.nextInt(11);
        var builder = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Variable<Integer> variable = VF.create("g" + id + "_" + i);
            variables.add(variable);
            builder.variableDomain(variable, IntRangeDomain.of(0, 2));
        }
        int constraints = rng.nextInt(n + 3);
        for (int j = 0; j < constraints; j++) {
            int u = rng.nextInt(n), v = rng.nextInt(n);
            if (u == v) continue;
            int w = rng.nextInt(n);
            if (n > 2 && w != u && w != v && rng.nextInt(6) == 0) {
                builder.allDiffConstraint(new LinkedHashSet<>(List.of(variables.get(u), variables.get(v), variables.get(w))));
            } else {
                builder.notEqualsConstraint(variables.get(u), variables.get(v));
            }
        }
        return builder.build();
    }

    @Test
    void lazyNeighboursAreIndistinguishableFromBuiltOnes() {
        Random rng = new Random(11);
        for (int trial = 0; trial < 500; trial++) {
            ConstraintSatisfactionProblem csp = randomProblem(rng, trial);
            var built = new ConstraintGraph(csp.getConstraints(), csp.getVariableDomains());
            // A budget and a threshold of zero put every variable on the lazy path.
            var lazy = new ConstraintGraph(csp.getConstraints(), csp.getVariableDomains(), 0, 0);

            assertThat(lazy.getNeighbours().keySet()).containsExactlyElementsOf(built.getNeighbours().keySet());
            for (Variable<?> variable : built.getNeighbours().keySet()) {
                Set<Variable<?>> expected = built.getNeighbours().get(variable);
                Set<Variable<?>> actual = lazy.getNeighbours().get(variable);
                // Same elements in the same order: search tie-breaking reads that order.
                assertThat(actual).containsExactlyElementsOf(expected);
                assertThat(actual.size()).isEqualTo(expected.size());
                assertThat(actual.isEmpty()).isEqualTo(expected.isEmpty());
                for (Variable<?> other : built.getNeighbours().keySet()) {
                    assertThat(actual.contains(other)).isEqualTo(expected.contains(other));
                }
            }
            assertThat(lazy.isCyclic()).isEqualTo(built.isCyclic());
            assertThat(lazy.isFullyConnected()).isEqualTo(built.isFullyConnected());
        }
    }

    @Test
    void aVariableInAGraphWithinBudgetGetsABuiltSet() {
        Variable<Integer> a = VF.create("builtA");
        Variable<Integer> b = VF.create("builtB");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 1))
                .variableDomain(b, IntRangeDomain.of(0, 1))
                .notEqualsConstraint(a, b)
                .build();

        assertThat(csp.getNeighbours().get(a).getClass().getSimpleName()).isNotEqualTo("LazyNeighbours");
        assertThat(csp.getNeighbours().get(a)).containsExactly(b);
    }

    @Test
    void aVariableInAConstraintTooWideToBuildIsGivenAViewInstead() {
        // 3,000 variables in one constraint would be 9 million neighbour entries; past the budget
        // each variable's set is read from the scope rather than held.
        var builder = ConstraintSatisfactionProblem.builder();
        Set<Variable<Integer>> wide = new LinkedHashSet<>();
        for (int i = 0; i < 3_000; i++) {
            Variable<Integer> variable = VF.create("wide" + i);
            wide.add(variable);
            builder.variableDomain(variable, IntRangeDomain.of(0, 1));
        }
        Variable<Integer> outside = VF.create("wideOutside");
        builder.variableDomain(outside, IntRangeDomain.of(0, 1));
        builder.sumConstraint(wide, io.github.rcrida.jcsp.constraints.Operator.LEQ, 3_000);
        ConstraintSatisfactionProblem csp = builder.build();
        Variable<Integer> member = wide.iterator().next();

        Set<Variable<?>> neighbours = csp.getNeighbours(member);

        assertThat(neighbours.getClass().getSimpleName()).isEqualTo("LazyNeighbours");
        assertThat(neighbours).hasSize(2_999).doesNotContain(member, outside);
        assertThat(neighbours.contains(wide.stream().skip(1).findFirst().orElseThrow())).isTrue();
        assertThat(neighbours.isEmpty()).isFalse();
        assertThat(csp.getNeighbours(outside)).isEmpty();
    }

    @Test
    void lazyNeighboursCannotBeModified() {
        Variable<Integer> a = VF.create("roA");
        Variable<Integer> b = VF.create("roB");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 1))
                .variableDomain(b, IntRangeDomain.of(0, 1))
                .notEqualsConstraint(a, b)
                .build();
        var lazy = new ConstraintGraph(csp.getConstraints(), csp.getVariableDomains(), 0, 0);
        Set<Variable<?>> neighbours = lazy.getNeighbours().get(a);

        assertThatThrownBy(() -> neighbours.add(a)).isInstanceOf(UnsupportedOperationException.class);
        var iterator = neighbours.iterator();
        iterator.next();
        assertThatThrownBy(iterator::remove).isInstanceOf(UnsupportedOperationException.class);
    }
}
