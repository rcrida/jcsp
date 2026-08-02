package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.FixpointPropagation;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverCancelledException;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * Demonstrates using {@link SolverListener#onPropagatorProgress} plus {@link Cancellation} to
     * surface just the *next* single deduction a human solving the puzzle by hand could make --
     * not a guess. Deliberately runs {@link FixpointPropagation#applyFixpointWithReason} directly
     * rather than the full search-capable {@link Solver.Factory#createSolver}: a real backtracking
     * guess isn't a legitimate "hint", so only propagation (which never guesses) is a sound source
     * of one, and each returned {@link Clue} is verified below to actually narrow toward -- never
     * away from -- the puzzle's known unique solution. See {@link #getClue} for how a genuine
     * contradiction (propagation alone proving the current state unsatisfiable) is distinguished
     * from simply having nothing left to deduce.
     */
    @Test
    void listenForPropagationIncrementToHintNextStep() {
        var csp = killerSudoku();
        for (int i = 1; i <= 10; i++) {
            val clue = getClue(csp);
            assertThat(clue).as("hint %d: propagation alone should still be able to deduce something", i).isNotNull();
            System.out.println(clue);

            Domain<Integer> domainBeforeThisHint = csp.getDomain(clue.variable());
            assertThat(clue.domain().size())
                    .as("hint %d should strictly narrow %s's domain", i, clue.variable())
                    .isLessThan(domainBeforeThisHint.size());
            assertThat(clue.domain().contains(solutionValueFor(clue.variable())))
                    .as("hint %d for %s must still contain the puzzle's known solution value", i, clue.variable())
                    .isTrue();

            csp = csp.toBuilder()
                    .variableDomain(clue.variable(), clue.domain())
                    .build();
        }
    }

    @Test
    void getClue_detectsGenuineUnsatisfiability_andExplainsWhy() {
        // Force two cells in the same row to the same singleton value -- a direct violation of
        // allDiffConstraint(row) that AllDiff's own GAC propagation (Regin 1994) detects immediately
        // as a Hall violation, with no search/guessing needed. Both cited cells are already
        // singleton, so AllDiffConstraint#explainInfeasible derives a real, ground NogoodConstraint
        // (not the "no explanation" fallback) -- getClue() must surface both the contradiction and
        // that explanation, rather than silently returning null the way it would for a puzzle state
        // that's merely stuck (needs a guess) rather than actually impossible.
        var csp = killerSudoku();
        var contradictory = csp.toBuilder()
                .variableDomain(VARIABLES[0][0], IntRangeDomain.of(5, 5))
                .variableDomain(VARIABLES[0][1], IntRangeDomain.of(5, 5))
                .build();

        assertThatThrownBy(() -> getClue(contradictory))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unsatisfiable")
                .hasMessageContaining("<(r0c0, r0c1), nogood(r0c0!=5 OR r0c1!=5)>")
                .hasMessageNotContaining("no explanation was derivable");
    }

    private static int solutionValueFor(Variable<Integer> variable) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (VARIABLES[r][c].equals(variable)) return SOLUTION[r][c];
            }
        }
        throw new AssertionError("variable not part of the killer sudoku grid: " + variable);
    }

    private record Clue(ConstraintConsistency propagator, Variable<Integer> variable, Domain<Integer> domain) {
        @Override
        public String toString() {
            return String.format("%s -> %s=%s", propagator, variable, domain);
        }
    }

    /**
     * Runs the propagator fixpoint directly (bypassing the decorator chain entirely) via {@link
     * FixpointPropagation#applyFixpointWithReason}, and relies on {@link SolverCancelledException}
     * -- thrown the moment {@link #getListener} cancels on the very first domain-narrowing event --
     * to stop after exactly one deduction rather than running the whole fixpoint to convergence.
     * Three distinct outcomes, not conflated into a single {@code null}:
     * <ul>
     *   <li>A deduction was captured: {@link SolverCancelledException} unwinds the call, {@code clue}
     *       holds it.</li>
     *   <li>The fixpoint converges with nothing left to narrow: returns normally, {@code
     *       isInfeasible()} is {@code false}, {@code clue} stays unset -- propagation alone is stuck
     *       (a guess would be needed), which {@link #listenForPropagationIncrementToHintNextStep}
     *       asserts against explicitly.</li>
     *   <li>Propagation itself proves the current domains contradictory: returns normally with
     *       {@code isInfeasible()} {@code true} -- fails loudly here, citing {@link
     *       ConsistencyResult#reason()} (a {@link io.github.rcrida.jcsp.constraints.nary.NogoodConstraint}
     *       explaining which variables/values are jointly impossible) when the responsible
     *       propagator's own {@code explainInfeasible} could derive one, rather than silently
     *       returning {@code null} and leaving a caller to misread it as "no more hints, try a
     *       guess" -- a contradiction is never fixed by guessing.</li>
     * </ul>
     */
    private static Clue getClue(ConstraintSatisfactionProblem csp) {
        val cancellation = new Cancellation();
        val clue = new AtomicReference<Clue>();
        val listener = getListener(clue, cancellation);
        ConsistencyResult result;
        try {
            result = FixpointPropagation.applyFixpointWithReason(csp, null, listener, new Statistics(), cancellation);
        } catch (SolverCancelledException expected) {
            // Deliberate: getListener() cancels as soon as it captures the first deduction.
            return clue.get();
        }
        if (result.isInfeasible()) {
            throw new AssertionError(result.reason() != null
                    ? "Propagation alone proved this puzzle state unsatisfiable: " + result.reason()
                    : "Propagation alone proved this puzzle state unsatisfiable, but no explanation "
                            + "was derivable from the responsible propagator");
        }
        return clue.get();
    }

    private static @NonNull SolverListener getListener(AtomicReference<Clue> clueAtomicReference, Cancellation cancellation) {
        return new SolverListener() {
            @Override
            public void onPropagatorProgress(ConstraintConsistency propagator, Map<Variable<?>, Domain<?>> domainsBefore, Map<Variable<?>, Domain<?>> domainsAfter, double domainSumBefore, double domainSumAfter) {
                val changed = domainsAfter.entrySet().stream()
                        .filter(e -> !e.getValue().equals(domainsBefore.get(e.getKey())))
                        .toList();
                val min = changed.stream()
                        .min(Comparator.comparing(e -> e.getValue().size()))
                        .orElseThrow();
                clueAtomicReference.compareAndSet(null, new Clue(propagator, (Variable<Integer>) min.getKey(), (Domain<Integer>) min.getValue()));
                cancellation.cancel();
            }
        };
    }
}
