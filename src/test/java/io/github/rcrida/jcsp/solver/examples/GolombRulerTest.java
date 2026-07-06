package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golomb ruler of order 5: place N marks on a ruler so that every pair of marks
 * yields a distinct distance. Each pairwise distance is materialised as an auxiliary
 * variable (defined via {@code linearConstraint}) so that {@code allDiffConstraint}
 * can enforce distinctness across all of them at once.
 *
 * <p>Rather than optimizing (which would need a hand-verified admissible lower bound
 * for branch-and-bound), optimality of the known length 11 (OEIS A003022) is proven
 * the classic way: the ruler is satisfiable with marks bounded by 11, and unsatisfiable
 * with marks bounded by 10.
 */
public class GolombRulerTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 5;
    static final int OPTIMAL_LENGTH = 11;

    static final List<Variable<Integer>> MARKS = IntStream.range(0, N)
            .mapToObj(i -> F.<Integer>create("m" + i))
            .toList();

    static ConstraintSatisfactionProblem buildRuler(int maxLength) {
        var builder = ConstraintSatisfactionProblem.builder();
        MARKS.forEach(m -> builder.variableDomain(m, IntRangeDomain.of(0, maxLength)));
        builder.equalsConstraint(MARKS.get(0), 0);
        for (int i = 0; i < N - 1; i++) {
            builder.comparatorConstraint(MARKS.get(i), Operator.LT, MARKS.get(i + 1));
        }

        List<Variable<Integer>> diffs = new ArrayList<>();
        Variable<Integer> firstGap = null;
        Variable<Integer> lastGap = null;
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                Variable<Integer> d = F.create("d" + i + j);
                builder.variableDomain(d, IntRangeDomain.of(1, maxLength));
                // d = marks[j] - marks[i]  <->  marks[j] - marks[i] - d == 0
                builder.linearConstraint(Map.of(MARKS.get(j), 1, MARKS.get(i), -1, d, -1), Operator.EQ, 0);
                diffs.add(d);
                if (i == 0 && j == 1) firstGap = d;
                if (i == N - 2 && j == N - 1) lastGap = d;
            }
        }
        builder.allDiffConstraint(Set.copyOf(diffs));

        // Symmetry breaking: every ruler's mirror image is also a solution; requiring
        // the first gap to be smaller than the last gap keeps only one of each pair.
        builder.comparatorConstraint(firstGap, Operator.LT, lastGap);

        return builder.build();
    }

    static void assertValidRuler(Assignment assignment) {
        List<Integer> positions = MARKS.stream().map(m -> assignment.getValue(m).orElseThrow()).toList();
        Set<Integer> distances = new HashSet<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                assertThat(distances.add(positions.get(j) - positions.get(i))).isTrue();
            }
        }
    }

    @Test
    void solvable_atOptimalLength() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildRuler(OPTIMAL_LENGTH)).getSolutions().toList();
        // Exactly two order-5 Golomb rulers of length 11 exist up to reflection:
        // {0,1,4,9,11} and {0,2,7,8,11}.
        assertThat(solutions).hasSize(2);
        solutions.forEach(GolombRulerTest::assertValidRuler);
        solutions.forEach(s -> assertThat(s.getValue(MARKS.get(N - 1))).hasValue(OPTIMAL_LENGTH));
    }

    @Test
    void unsolvable_belowOptimalLength() {
        assertThat(Solver.Factory.INSTANCE.createSolver(buildRuler(OPTIMAL_LENGTH - 1)).getSolution()).isEmpty();
    }
}
