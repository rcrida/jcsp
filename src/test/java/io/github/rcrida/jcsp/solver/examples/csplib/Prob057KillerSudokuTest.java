package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Killer Sudoku (CSPLib prob057): a standard 9x9 Sudoku (row/column/box all-different) with no
 * given digits, plus a partition of all 81 cells into "cages" — each cage's cells must sum to a
 * fixed total and, like a row/column/box, contain no repeated digit. The puzzle is solvable from
 * the cage sums alone.
 * <p>
 * Cage layout and sums are transcribed from CSPLib's reference MiniZinc model
 * ({@code Problems/prob057/models/killer_sudoku2.mzn} in the csplib/csplib GitHub repo), which is
 * itself the classic example puzzle from Wikipedia's Killer Sudoku article
 * ({@code en.wikipedia.org/wiki/File:Killersudoku_color.svg}). Every cage's sum was cross-checked
 * by hand against that model's documented solution grid before being encoded here.
 */
public class Prob057KillerSudokuTest {
    static final Domain<Integer> DOMAIN = IntRangeDomain.of(1, 9);
    static Variable<Integer>[][] VARIABLES;

    /** {row, col} pairs (0-indexed) per cage, and each cage's target sum. */
    record Cage(int sum, int[][] cells) {}

    static final Cage[] CAGES = {
            new Cage(3, new int[][]{{0,0},{0,1}}),
            new Cage(15, new int[][]{{0,2},{0,3},{0,4}}),
            new Cage(22, new int[][]{{0,5},{1,4},{1,5},{2,4}}),
            new Cage(4, new int[][]{{0,6},{1,6}}),
            new Cage(16, new int[][]{{0,7},{1,7}}),
            new Cage(15, new int[][]{{0,8},{1,8},{2,8},{3,8}}),
            new Cage(25, new int[][]{{1,0},{1,1},{2,0},{2,1}}),
            new Cage(17, new int[][]{{1,2},{1,3}}),
            new Cage(9, new int[][]{{2,2},{2,3},{3,3}}),
            new Cage(8, new int[][]{{2,5},{3,5},{4,5}}),
            new Cage(20, new int[][]{{2,6},{2,7},{3,6}}),
            new Cage(17, new int[][]{{3,7},{4,6},{4,7}}),
            new Cage(6, new int[][]{{3,0},{4,0}}),
            new Cage(14, new int[][]{{3,1},{3,2}}),
            new Cage(17, new int[][]{{3,4},{4,4},{5,4}}),
            new Cage(13, new int[][]{{4,1},{4,2},{5,1}}),
            new Cage(20, new int[][]{{4,3},{5,3},{6,3}}),
            new Cage(12, new int[][]{{4,8},{5,8}}),
            new Cage(27, new int[][]{{5,0},{6,0},{7,0},{8,0}}),
            new Cage(6, new int[][]{{5,2},{6,1},{6,2}}),
            new Cage(20, new int[][]{{5,5},{6,5},{6,6}}),
            new Cage(6, new int[][]{{5,6},{5,7}}),
            new Cage(10, new int[][]{{6,4},{7,3},{7,4},{8,3}}),
            new Cage(14, new int[][]{{6,7},{6,8},{7,7},{7,8}}),
            new Cage(8, new int[][]{{7,1},{8,1}}),
            new Cage(16, new int[][]{{7,2},{8,2}}),
            new Cage(15, new int[][]{{7,5},{7,6}}),
            new Cage(13, new int[][]{{8,4},{8,5},{8,6}}),
            new Cage(17, new int[][]{{8,7},{8,8}}),
    };

    /** The published solution to CSPLib prob057's reference instance, used only to verify our own result. */
    static final int[][] SOLUTION = {
            {2,1,5,6,4,7,3,9,8},
            {3,6,8,9,5,2,1,7,4},
            {7,9,4,3,8,1,6,5,2},
            {5,8,6,2,7,4,9,3,1},
            {1,4,2,5,9,3,8,6,7},
            {9,7,3,8,1,6,4,2,5},
            {8,2,1,7,3,9,5,4,6},
            {6,5,9,4,2,8,7,1,3},
            {4,3,7,1,6,5,2,8,9},
    };

    @SuppressWarnings("unchecked")
    public static ConstraintSatisfactionProblem killerSudoku() {
        val builder = ConstraintSatisfactionProblem.builder();
        VARIABLES = new Variable[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                VARIABLES[r][c] = Variable.Factory.INSTANCE.create("r" + r + "c" + c);
                builder.variableDomain(VARIABLES[r][c], DOMAIN);
            }
        }
        for (int r = 0; r < 9; r++) {
            val row = new HashSet<Variable<Integer>>();
            for (int c = 0; c < 9; c++) row.add(VARIABLES[r][c]);
            builder.allDiffConstraint(row);
        }
        for (int c = 0; c < 9; c++) {
            val column = new HashSet<Variable<Integer>>();
            for (int r = 0; r < 9; r++) column.add(VARIABLES[r][c]);
            builder.allDiffConstraint(column);
        }
        for (int br = 0; br < 9; br += 3) {
            for (int bc = 0; bc < 9; bc += 3) {
                val box = new HashSet<Variable<Integer>>();
                for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) box.add(VARIABLES[br + r][bc + c]);
                builder.allDiffConstraint(box);
            }
        }
        for (Cage cage : CAGES) {
            Set<Variable<Integer>> cageVars = new HashSet<>();
            for (int[] cell : cage.cells()) cageVars.add(VARIABLES[cell[0]][cell[1]]);
            builder.allDiffConstraint(cageVars);
            builder.sumConstraint(cageVars, Operator.EQ, cage.sum());
        }
        return builder.build();
    }

    @Test
    void solvesToTheKnownUniqueSolution() {
        val csp = killerSudoku();
        val solver = Solver.Factory.INSTANCE.createSolver(csp);
        val solution = solver.getSolution();
        assertThat(solution).isPresent();
        Assignment assignment = solution.get();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertThat(assignment.getValue(VARIABLES[r][c])).hasValue(SOLUTION[r][c]);
            }
        }
    }

    @Test
    void solutionIsUnique() {
        val csp = killerSudoku();
        val solver = Solver.Factory.INSTANCE.createSolver(csp);
        assertThat(solver.getSolutions()).hasSize(1);
    }
}
