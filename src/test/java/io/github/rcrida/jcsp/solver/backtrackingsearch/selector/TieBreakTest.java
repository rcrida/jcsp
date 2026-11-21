package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TieBreakTest {
    private final Variable.Factory variables = new Variable.Factory();
    private final Variable<Integer> a = variables.create("a");
    private final Variable<Integer> b = variables.create("b");
    private final Variable<Integer> c = variables.create("c");
    private final Variable<Integer> d = variables.create("d");

    /** a has degree 2 (a≠b, a≠c), b and c degree 1, d none; the nogood over b and c is not counted. */
    private Set<Constraint> constraints() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 3))
                .variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 3))
                .variableDomain(d, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(a, b)
                .notEqualsConstraint(a, c)
                .build();
        Set<Constraint> constraints = new HashSet<>(csp.getConstraints());
        constraints.add(GroundNogoodConstraint.of(Map.<Variable<?>, Object>of(b, 1, c, 1)));
        constraints.add(GroundNogoodConstraint.of(Map.<Variable<?>, Object>of(b, 2, c, 2)));
        return constraints;
    }

    /** Always answers with the last tied variable, and records what it was asked and told. */
    private static final class Last implements TieBreak {
        final List<List<Variable<?>>> asked = new ArrayList<>();
        final List<Random> told = new ArrayList<>();

        @Override
        public Variable<?> choose(List<Variable<?>> tied) {
            asked.add(List.copyOf(tied));
            return tied.get(tied.size() - 1);
        }

        @Override
        public void onRestart(Random random) {
            told.add(random);
        }
    }

    @Test
    void aTieBreakIgnoresRestartsByDefault() {
        TieBreak first = tied -> tied.get(0);

        first.onRestart(new Random(1));

        assertThat(first.choose(List.of(b, c))).isEqualTo(b);
    }

    @Test
    void randomTakesTheFirstWithoutASource() {
        TieBreak random = TieBreak.Factory.RANDOM.create(Set.of());

        assertThat(random.choose(List.of(b, c, d))).isEqualTo(b);
    }

    @Test
    void randomDrawsFromTheRestartsSourceUntilItIsCleared() {
        TieBreak tieBreak = TieBreak.Factory.RANDOM.create(Set.of());
        Random random = mock(Random.class);
        when(random.nextInt(3)).thenReturn(2);

        tieBreak.onRestart(random);
        assertThat(tieBreak.choose(List.of(b, c, d))).isEqualTo(d);

        tieBreak.onRestart(null);
        assertThat(tieBreak.choose(List.of(b, c, d))).isEqualTo(b);
    }

    @Test
    void maxDegreeTakesTheMostConstrainedWithoutAskingTheNext() {
        Last next = new Last();
        TieBreak tieBreak = TieBreak.Factory.maxDegree(constraints -> next).create(constraints());

        assertThat(tieBreak.choose(List.of(d, b, a, c))).isEqualTo(a);
        assertThat(next.asked).isEmpty();
    }

    @Test
    void maxDegreeHandsTheMostConstrainedThatRemainTiedToTheNextInOrder() {
        // b and c have degree 1 each -- the nogood between them would make it 3 were it counted -- and
        // d, in no constraint, has 0.
        Last next = new Last();
        TieBreak tieBreak = TieBreak.Factory.maxDegree(constraints -> next).create(constraints());

        assertThat(tieBreak.choose(List.of(d, c, b))).isEqualTo(b);
        assertThat(next.asked).containsExactly(List.of(c, b));
    }

    @Test
    void maxDegreePassesRestartsOn() {
        Last next = new Last();
        TieBreak tieBreak = TieBreak.Factory.maxDegree(constraints -> next).create(constraints());
        Random random = new Random(1);

        tieBreak.onRestart(random);

        assertThat(next.told).containsExactly(random);
    }

    @Test
    void firstRunUsesTheFirstUntilTheSecondRestart() {
        Last first = new Last();
        Last later = new Last();
        TieBreak tieBreak = TieBreak.Factory.firstRun(constraints -> first, constraints -> later).create(Set.of());
        Random one = new Random(1);
        Random two = new Random(2);

        tieBreak.choose(List.of(b, c));
        tieBreak.onRestart(one);
        tieBreak.choose(List.of(b, c));
        assertThat(first.asked).hasSize(2);
        assertThat(later.asked).isEmpty();

        tieBreak.onRestart(two);
        tieBreak.choose(List.of(b, c));
        assertThat(first.asked).hasSize(2);
        assertThat(later.asked).hasSize(1);
        assertThat(first.told).containsExactly(one, two);
        assertThat(later.told).containsExactly(one, two);
    }

    @Test
    void theDefaultIsMostConstrainedFirstAndThenRandom() {
        TieBreak tieBreak = TieBreak.Factory.DEFAULT.create(constraints());

        tieBreak.onRestart(null);
        assertThat(tieBreak.choose(List.of(d, b, a))).isEqualTo(a);

        tieBreak.onRestart(null);
        assertThat(tieBreak.choose(List.of(d, b, a))).isEqualTo(d);
    }
}
