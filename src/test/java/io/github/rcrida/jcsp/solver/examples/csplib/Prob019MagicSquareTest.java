package io.github.rcrida.jcsp.solver.examples.csplib;
import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3×3 magic square: place integers 1–9 in a grid so that every row, column,
 * and main diagonal sums to 15 (the magic constant N(N²+1)/2 for N=3).
 *
 * <pre>
 * One solution:
 *   2 | 7 | 6
 *   9 | 5 | 1
 *   4 | 3 | 8
 * </pre>
 *
 * There are exactly 8 distinct solutions (one fundamental solution under the
 * 8 symmetries of the square).
 */
public class Prob019MagicSquareTest {
    static final int N = 3;

    record MagicSquareProblem(ConstraintSatisfactionProblem csp, Variable<Integer>[][] cells) {}

    static MagicSquareProblem square() {
        return square(N);
    }

    /**
     * Order is a parameter, so larger squares can be built (e.g. for benchmarking); {@link
     * #square()} pins it to {@link #N}. At order {@link #N} loads the real XCSP3 instance file
     * (MagicSquare-03-sum.xml from the XCSP3 MagicSquare series, https://xcsp.org/instances/,
     * unmodified -- see the instance file's own comment); every other order builds the CSP
     * programmatically.
     */
    static MagicSquareProblem square(int n) {
        if (n == N) {
            return xcsp3Square();
        }

        int magic = n * (n * n + 1) / 2;
        String[] indices = new String[n];
        for (int i = 0; i < n; i++) indices[i] = String.valueOf(i + 1);

        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer>[][] cells = builder.create2dVariableArray(indices, indices, "c", IntRangeDomain.of(1, n * n));

        // All cells must be distinct
        val allCells = new HashSet<Variable<Integer>>();
        for (var row : cells)
            allCells.addAll(List.of(row));
        builder.allDiffConstraint(allCells);

        // Row sums
        for (int r = 0; r < n; r++)
            builder.sumConstraint(Set.copyOf(List.of(cells[r])), Operator.EQ, magic);

        // Column sums
        for (int c = 0; c < n; c++) {
            Set<Variable<Integer>> column = new HashSet<>();
            for (int r = 0; r < n; r++) column.add(cells[r][c]);
            builder.sumConstraint(column, Operator.EQ, magic);
        }

        // Main diagonal (top-left to bottom-right)
        Set<Variable<Integer>> mainDiagonal = new HashSet<>();
        for (int i = 0; i < n; i++) mainDiagonal.add(cells[i][i]);
        builder.sumConstraint(mainDiagonal, Operator.EQ, magic);

        // Anti-diagonal (top-right to bottom-left)
        Set<Variable<Integer>> antiDiagonal = new HashSet<>();
        for (int i = 0; i < n; i++) antiDiagonal.add(cells[i][n - 1 - i]);
        builder.sumConstraint(antiDiagonal, Operator.EQ, magic);

        return new MagicSquareProblem(builder.build(), cells);
    }

    @SuppressWarnings("unchecked")
    private static MagicSquareProblem xcsp3Square() {
        var instance = Xcsp3CsplibResource.parse("magic-square-order3.xml");
        Variable<Integer>[][] cells = new Variable[N][N];
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                cells[r][c] = Variable.Factory.INSTANCE.create("x[" + r + "][" + c + "]");
            }
        }
        return new MagicSquareProblem(instance.csp(), cells);
    }

    @Test
    void solution() {
        val problem = square();
        val result = Solver.Factory.INSTANCE.createSolver(problem.csp()).getSolution();
        assertThat(result).hasValueSatisfying(assignment -> {
            assertThat(assignment.isSolution(problem.csp())).isTrue();
            System.out.println("Statistics: " + assignment.getStatistics());
            printSquare(assignment, problem.cells());
        });
    }

    @Test
    void allSolutions() {
        val problem = square();
        val solutions = Solver.Factory.INSTANCE.createSolver(problem.csp()).getSolutions().toList();
        System.out.println("Total magic squares: " + solutions.size());
        assertThat(solutions).hasSize(8);
    }

    @Test
    void allSolutions_withSymmetryBreaking() {
        // Require the first row to be lexicographically <= the last row.
        // This eliminates the top-bottom reflection from each mirrored pair, halving 8 → 4.
        val p = square();
        val cells = p.cells();
        val row0 = List.of(cells[0][0], cells[0][1], cells[0][2]);
        val row2 = List.of(cells[2][0], cells[2][1], cells[2][2]);
        val csp = p.csp().toBuilder()
                .constraint(io.github.rcrida.jcsp.constraints.nary.LexConstraint.of(row0, Operator.LEQ, row2))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(4);
    }

    static void printSquare(Assignment assignment, Variable<Integer>[][] cells) {
        for (var row : cells) {
            val line = new StringBuilder();
            for (var cell : row) {
                if (!line.isEmpty()) line.append(" | ");
                line.append(assignment.getValue(cell).orElseThrow());
            }
            System.out.println(line);
        }
    }
}
