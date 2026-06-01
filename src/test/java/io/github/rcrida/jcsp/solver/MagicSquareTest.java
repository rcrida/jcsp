package io.github.rcrida.jcsp.solver;

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
public class MagicSquareTest {
    static final int N = 3;
    static final int MAGIC = N * (N * N + 1) / 2; // 15
    static final String[] INDICES = {"1", "2", "3"};

    record MagicSquareProblem(ConstraintSatisfactionProblem csp, Variable<Integer>[][] cells) {}

    static MagicSquareProblem square() {
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer>[][] cells = builder.create2dVariableArray(INDICES, INDICES, "c", IntRangeDomain.of(1, N * N));

        // All cells must be distinct
        val allCells = new HashSet<Variable<Integer>>();
        for (var row : cells)
            allCells.addAll(List.of(row));
        builder.allDiffConstraint(allCells);

        // Row sums
        for (int r = 0; r < N; r++)
            builder.sumConstraint(Set.of(cells[r][0], cells[r][1], cells[r][2]), Operator.EQ, MAGIC);

        // Column sums
        for (int c = 0; c < N; c++)
            builder.sumConstraint(Set.of(cells[0][c], cells[1][c], cells[2][c]), Operator.EQ, MAGIC);

        // Main diagonal (top-left to bottom-right)
        builder.sumConstraint(Set.of(cells[0][0], cells[1][1], cells[2][2]), Operator.EQ, MAGIC);

        // Anti-diagonal (top-right to bottom-left)
        builder.sumConstraint(Set.of(cells[0][2], cells[1][1], cells[2][0]), Operator.EQ, MAGIC);

        return new MagicSquareProblem(builder.build(), cells);
    }

    @Test
    void solution() {
        val problem = square();
        val result = Solver.Factory.INSTANCE.createSolver().getSolution(problem.csp());
        assertThat(result).hasValueSatisfying(assignment -> {
            assertThat(assignment.isSolution(problem.csp())).isTrue();
            System.out.println("Statistics: " + assignment.getStatistics());
            printSquare(assignment, problem.cells());
        });
    }

    @Test
    void allSolutions() {
        val problem = square();
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(problem.csp()).toList();
        System.out.println("Total magic squares: " + solutions.size());
        assertThat(solutions).hasSize(8);
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
