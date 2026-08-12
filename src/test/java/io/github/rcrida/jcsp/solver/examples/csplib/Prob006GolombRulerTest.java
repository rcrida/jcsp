package io.github.rcrida.jcsp.solver.examples.csplib;

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
 * variable so that {@code allDiffConstraint} can enforce distinctness across all of them at once.
 *
 * <p>The CSPLib-sized instance ({@link #N} marks, length {@link #OPTIMAL_LENGTH}) is sourced from
 * a real XCSP3 instance file (see {@link #buildRuler}'s own comment for provenance) rather than
 * built by hand; larger sizes used only for benchmarking ({@link CsplibBenchmarks}) fall back to
 * the programmatic builder, since no instance file exists for them.
 *
 * <p>Rather than optimizing (which would need a hand-verified admissible lower bound
 * for branch-and-bound), optimality of the known length 11 (OEIS A003022) is proven
 * the classic way: the ruler is satisfiable with marks bounded by 11, and unsatisfiable
 * with marks bounded by 10.
 */
public class Prob006GolombRulerTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 5;
    static final int OPTIMAL_LENGTH = 11;

    record RulerProblem(ConstraintSatisfactionProblem csp, List<Variable<Integer>> marks) {}

    /**
     * Order and ruler length are both parameters, so larger instances can be built (e.g. for
     * benchmarking). At order {@link #N}, length {@link #OPTIMAL_LENGTH} or {@code
     * OPTIMAL_LENGTH - 1} loads the matching real XCSP3 instance file (GolombRuler-05-a3.xml from
     * the XCSP3 GolombRuler series, https://xcsp.org/instances/, domain-trimmed to the relevant
     * length with one appended symmetry-breaking constraint -- see the instance files' own
     * comments); every other size builds the CSP programmatically.
     */
    static RulerProblem buildRuler(int n, int maxLength) {
        if (n == N && maxLength == OPTIMAL_LENGTH) {
            return xcsp3Ruler("golomb-ruler-order5-length11.xml", n);
        }
        if (n == N && maxLength == OPTIMAL_LENGTH - 1) {
            return xcsp3Ruler("golomb-ruler-order5-length10.xml", n);
        }

        List<Variable<Integer>> marks = new ArrayList<>();
        for (int i = 0; i < n; i++) marks.add(F.create("m" + i));

        var builder = ConstraintSatisfactionProblem.builder();
        marks.forEach(m -> builder.variableDomain(m, IntRangeDomain.of(0, maxLength)));
        builder.equalsConstraint(marks.get(0), 0);
        for (int i = 0; i < n - 1; i++) {
            builder.comparatorConstraint(marks.get(i), Operator.LT, marks.get(i + 1));
        }

        List<Variable<Integer>> diffs = new ArrayList<>();
        Variable<Integer> firstGap = null;
        Variable<Integer> lastGap = null;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Variable<Integer> d = F.create("d" + i + j);
                builder.variableDomain(d, IntRangeDomain.of(1, maxLength));
                // d = marks[j] - marks[i]
                builder.linearConstraint(Map.of(marks.get(j), 1, marks.get(i), -1), Operator.EQ, d);
                diffs.add(d);
                if (i == 0 && j == 1) firstGap = d;
                if (i == n - 2 && j == n - 1) lastGap = d;
            }
        }
        builder.allDiffConstraint(Set.copyOf(diffs));

        // Symmetry breaking: every ruler's mirror image is also a solution; requiring
        // the first gap to be smaller than the last gap keeps only one of each pair.
        builder.comparatorConstraint(firstGap, Operator.LT, lastGap);

        return new RulerProblem(builder.build(), marks);
    }

    private static RulerProblem xcsp3Ruler(String resourceName, int n) {
        var instance = Xcsp3CsplibResource.parse(resourceName);
        List<Variable<Integer>> marks = IntStream.range(0, n).mapToObj(i -> F.<Integer>create("x[" + i + "]")).toList();
        return new RulerProblem(instance.csp(), marks);
    }

    static void assertValidRuler(Assignment assignment, List<Variable<Integer>> marks) {
        List<Integer> positions = marks.stream().map(m -> assignment.getValue(m).orElseThrow()).toList();
        Set<Integer> distances = new HashSet<>();
        for (int i = 0; i < marks.size(); i++) {
            for (int j = i + 1; j < marks.size(); j++) {
                assertThat(distances.add(positions.get(j) - positions.get(i))).isTrue();
            }
        }
    }

    @Test
    void solvable_atOptimalLength() {
        val problem = buildRuler(N, OPTIMAL_LENGTH);
        val solutions = Solver.Factory.INSTANCE.createSolver(problem.csp()).getSolutions().toList();
        // Exactly two order-5 Golomb rulers of length 11 exist up to reflection:
        // {0,1,4,9,11} and {0,2,7,8,11}.
        assertThat(solutions).hasSize(2);
        solutions.forEach(s -> assertValidRuler(s, problem.marks()));
        solutions.forEach(s -> assertThat(s.getValue(problem.marks().get(N - 1))).hasValue(OPTIMAL_LENGTH));
    }

    @Test
    void unsolvable_belowOptimalLength() {
        assertThat(Solver.Factory.INSTANCE.createSolver(buildRuler(N, OPTIMAL_LENGTH - 1).csp()).getSolution()).isEmpty();
    }
}
