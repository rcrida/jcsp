package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RestartNogoodsSearchTest {
    private final Variable.Factory VF = new Variable.Factory();

    /** {@code count} variables pairwise different over {@code values} values. */
    private ConstraintSatisfactionProblem allDifferent(int count, int values) {
        List<Variable<Integer>> vars = new ArrayList<>();
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < count; i++) {
            Variable<Integer> v = VF.create("rn" + count + "_" + values + "_" + i);
            vars.add(v);
            builder.variableDomain(v, IntRangeDomain.of(1, values));
        }
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) builder.notEqualsConstraint(vars.get(i), vars.get(j));
        }
        return builder.build();
    }

    private static DomWdegLubySearch.DomWdegLubySearchBuilder search(RestartNogoods restartNogoods, NogoodStore store,
                                                                       Statistics statistics) {
        return DomWdegLubySearch.builder()
                .lubyUnit(1)
                .maxRestarts(10_000)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                // No reason tracking, so the only nogoods recorded are the restart ones under test.
                .inference(Inference.withoutReasonTracking(Solver.Factory.FULL_PROPAGATION_INFERENCE))
                .restartNogoods(restartNogoods)
                .nogoodStore(store)
                .statistics(statistics);
    }

    @Test
    void anAbandonedRunLeavesTheRefutedBranchesAsNogoods() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();
        var statistics = new Statistics();

        assertThat(search(RestartNogoods.always(8), store, statistics).build().getSolution(csp)).isEmpty();

        assertThat(statistics.getRestarts()).isPositive();
        assertThat(store.size()).isPositive();
        // The store keeps each distinct nogood once, however many restarts re-derive it.
        assertThat(statistics.getNogoodsLearned()).isGreaterThanOrEqualTo(store.size());
    }

    @Test
    void nothingIsRecordedWhenRestartNogoodsAreOff() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();
        var statistics = new Statistics();

        assertThat(search(RestartNogoods.NONE, store, statistics).build().getSolution(csp)).isEmpty();

        assertThat(statistics.getRestarts()).isPositive();
        assertThat(store.size()).isZero();
        assertThat(statistics.getNogoodsLearned()).isZero();
    }

    @Test
    void aNogoodCitesNoMoreDecisionsThanTheCap() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();

        search(RestartNogoods.always(2), store, new Statistics()).build().getSolution(csp);

        assertThat(store.size()).isPositive();
        assertThat(store.apply(csp).getNogoods()).allSatisfy(nogood -> assertThat(nogood.getVariables()).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void theListenerHearsOfEachRecordedNogood() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();
        var heard = new AtomicInteger();
        var listener = new io.github.rcrida.jcsp.solver.listener.SolverListener() {
            @Override
            public void onNogoodLearned(NogoodConstraint nogood) {
                heard.incrementAndGet();
            }
        };

        var statistics = new Statistics();

        search(RestartNogoods.always(8), store, statistics).listener(listener).build().getSolution(csp);

        assertThat(heard.get()).isPositive().isEqualTo((int) statistics.getNogoodsLearned());
    }

    @Test
    void anAdaptiveSearchWhoseNogoodsDoNothingForgetsThemAndStopsRecording() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();
        var statistics = new Statistics();
        // A hit rate no search can reach, judged after the first backtrack.
        var impossible = RestartNogoods.adaptive(8, 1, 1e9);

        assertThat(search(impossible, store, statistics).build().getSolution(csp)).isEmpty();

        assertThat(statistics.getNogoodsLearned()).as("it did record, before judging").isPositive();
        assertThat(store.size()).as("and forgot them all").isZero();
    }

    @Test
    void anAdaptiveSearchWhoseNogoodsDoWorkKeepsThem() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();
        var statistics = new Statistics();
        // Judged after 20 backtracks against a rate any hit at all would clear.
        var lenient = RestartNogoods.adaptive(8, 20, 1e-9);

        assertThat(search(lenient, store, statistics).build().getSolution(csp)).isEmpty();

        assertThat(store.size()).isPositive();
    }

    @Test
    void anAdaptiveSearchThatFinishesBeforeItsWindowIsNeverJudged() {
        var csp = allDifferent(5, 4);
        var store = new NogoodStore();

        search(RestartNogoods.adaptive(8, 1_000_000, 1e9), store, new Statistics()).build().getSolution(csp);

        assertThat(store.size()).as("kept, since the window was never reached").isPositive();
    }

    @Test
    void remembersRefutationsWithoutChangingWhatTheSearchFinds() {
        // Random small problems, mostly tight enough to need restarts: whatever the plain search
        // decides, the nogood-keeping one must decide the same, and any solution it returns must be one.
        Random random = new Random(20261021L);
        for (int trial = 0; trial < 40; trial++) {
            var csp = randomProblem(random, trial);
            var plain = search(RestartNogoods.NONE, new NogoodStore(), new Statistics()).build().getSolution(csp);
            var remembering = search(RestartNogoods.always(8), new NogoodStore(), new Statistics()).build().getSolution(csp);

            assertThat(remembering.isPresent()).as("trial %d", trial).isEqualTo(plain.isPresent());
            remembering.ifPresent(solution -> assertThat(solution.isSolution(csp)).isTrue());
        }
    }

    private ConstraintSatisfactionProblem randomProblem(Random random, int trial) {
        int count = 6;
        List<Variable<Integer>> vars = new ArrayList<>();
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < count; i++) {
            Variable<Integer> v = VF.create("rp" + trial + "_" + i);
            vars.add(v);
            builder.variableDomain(v, IntRangeDomain.of(1, 3));
        }
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                switch (random.nextInt(4)) {
                    case 0 -> builder.notEqualsConstraint(vars.get(i), vars.get(j));
                    case 1 -> builder.comparatorConstraint(vars.get(i), io.github.rcrida.jcsp.constraints.Operator.LT, vars.get(j));
                    default -> { }
                }
            }
        }
        return builder.build();
    }
}
