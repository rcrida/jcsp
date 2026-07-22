package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for a real bug: {@code Solver.Factory}'s satisfaction chain used to build one
 * {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch} (and one shared
 * {@link io.github.rcrida.jcsp.assignments.NogoodStore}) and reuse it for every independent
 * sub-problem {@code IndependentSubproblemSolver} discovers. A nogood learned solving one sub-problem
 * would get recorded into the shared store, then crash ({@code IllegalArgumentException}: unknown
 * variables) as soon as a *different*, disjoint-variable sub-problem tried to apply it. Needs
 * sub-problems hard enough to actually learn a nogood — trivial ones (e.g. two tiny AllDiffs) never
 * exercised this, which is why it went unnoticed. Golomb rulers reliably need backtracking even at
 * small orders.
 */
public class IndependentSubproblemNogoodIsolationTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ConstraintSatisfactionProblem buildRuler(String prefix, int order, int maxLength) {
        List<Variable<Integer>> marks = IntStream.range(0, order)
                .mapToObj(i -> F.<Integer>create(prefix + "_m" + i))
                .toList();
        var builder = ConstraintSatisfactionProblem.builder();
        marks.forEach(m -> builder.variableDomain(m, IntRangeDomain.of(0, maxLength)));
        builder.equalsConstraint(marks.get(0), 0);
        for (int i = 0; i < order - 1; i++) {
            builder.comparatorConstraint(marks.get(i), Operator.LT, marks.get(i + 1));
        }
        List<Variable<Integer>> diffs = new ArrayList<>();
        Variable<Integer> firstGap = null;
        Variable<Integer> lastGap = null;
        for (int i = 0; i < order; i++) {
            for (int j = i + 1; j < order; j++) {
                Variable<Integer> d = F.create(prefix + "_d" + i + j);
                builder.variableDomain(d, IntRangeDomain.of(1, maxLength));
                builder.linearConstraint(Map.of(marks.get(j), 1, marks.get(i), -1), Operator.EQ, d);
                diffs.add(d);
                if (i == 0 && j == 1) firstGap = d;
                if (i == order - 2 && j == order - 1) lastGap = d;
            }
        }
        builder.allDiffConstraint(Set.copyOf(diffs));
        builder.comparatorConstraint(firstGap, Operator.LT, lastGap);
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ConstraintSatisfactionProblem buildIndependentCopies(int copies, int order, int maxLength) {
        var builder = ConstraintSatisfactionProblem.builder();
        for (int k = 0; k < copies; k++) {
            var sub = buildRuler("c" + k, order, maxLength);
            for (var entry : sub.getVariableDomains().entrySet()) {
                builder.variableDomainEntry((Variable) entry.getKey(), entry.getValue());
            }
            sub.getConstraints().forEach(builder::constraint);
        }
        return builder.build();
    }

    @Test
    void twoIndependentGolombRulers_getSolutions_sequentialPath() {
        var csp = buildIndependentCopies(2, 5, 11);
        var result = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().findFirst();
        assertThat(result).isPresent();
    }

    @Test
    void twoIndependentGolombRulers_getSolution_parallelPath() {
        var csp = buildIndependentCopies(2, 5, 11);
        var result = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(result).isPresent();
    }

    @Test
    void eightIndependentGolombRulers_getSolutions_sequentialPath() {
        var csp = buildIndependentCopies(8, 5, 11);
        var result = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().findFirst();
        assertThat(result).isPresent();
    }
}
