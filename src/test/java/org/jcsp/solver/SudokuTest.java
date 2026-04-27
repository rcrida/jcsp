package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.unary.UnaryValueConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SudokuTest {
    static Domain DOMAIN = new IntRangeDomain(1, 9);
    static String[] ROWS = {"A", "B", "C", "D", "E", "F", "G", "H", "I"};
    static String[] COLUMNS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    static Variable[][] VARIABLES;

    public static ConstraintSatisfactionProblem sudoku() {
        val cspBuilder = ConstraintSatisfactionProblem.builder();
        VARIABLES = cspBuilder.create2dVariableArray(ROWS, COLUMNS, "", DOMAIN);
        for (int i = 0; i < VARIABLES.length; i++) {
            System.out.println(Arrays.toString(VARIABLES[i]));
        }
        // row constraints
        for (int i = 0; i < ROWS.length; i++) {
            val row = Set.of(VARIABLES[i]);
            cspBuilder.allDiffConstraint(row);
        }
        // column constraints
        for (int j = 0; j < COLUMNS.length; j++) {
            int finalJ = j;
            val column = Arrays.stream(VARIABLES)
                    .map(row -> row[finalJ])
                    .collect(Collectors.toSet());
            cspBuilder.allDiffConstraint(column);
        }
        // square constraints
        for (int i = 0; i < ROWS.length; i += 3) {
            for (int j = 0; j < COLUMNS.length; j += 3) {
                val square = new HashSet<Variable>();
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        square.add(VARIABLES[i+r][j+c]);
                    }
                }
                cspBuilder.allDiffConstraint(square);
            }
        }
        // global constraints
        val constraints = new int[][] {
                {0,0,3,0,2,0,6,0,0},
                {9,0,0,3,0,5,0,0,1},
                {0,0,1,8,0,6,4,0,0},
                {0,0,8,1,0,2,9,0,0},
                {7,0,0,0,0,0,0,0,8},
                {0,0,6,7,0,8,2,0,0},
                {0,0,2,6,0,9,5,0,0},
                {8,0,0,2,0,3,0,0,9},
                {0,0,5,0,1,0,3,0,0}
        };
        print2dArray(constraints);
        for (int i = 0; i < ROWS.length; i++) {
            for (int j = 0; j < COLUMNS.length; j++) {
                if (constraints[i][j] != 0) {
                    cspBuilder.constraint(UnaryValueConstraint.of(VARIABLES[i][j], constraints[i][j]));
                }
            }
        }
        return cspBuilder.build();
    }

    static void print2dArray(int[][] array) {
        for (int i = 0; i < array.length; i++) {
            System.out.println(Arrays.toString(array[i]));
        }
    }

    @Test
    void solution() {
        val csp = sudoku();
        assertThat(csp.getSearchSpace()).isEqualTo(new BigInteger("196627050475552913618075908526912116283103450944214766927315415537966391196809"));
        val solver = Solver.Factory.INSTANCE.createSolver();
        val optionalSolution = solver.getSolution(csp).map(this::extractArray);
        optionalSolution.ifPresent(SudokuTest::print2dArray);
    }

    private int[][] extractArray(Assignment assignment) {
        val solution = new int[9][9];
        for (int i = 0; i < ROWS.length; i++) {
            for (int j = 0; j < COLUMNS.length; j++) {
                solution[i][j] = (int) assignment.getValue(VARIABLES[i][j]).orElse(0);
            }
        }
        return solution;
    }

    @Test
    void solutions() {
        val csp = sudoku();
        val solver = Solver.Factory.INSTANCE.createSolver();
        assertThat(solver.getSolutions(csp)).hasSize(1);
    }
}
