package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TabuSearchSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");
    static final Variable<Integer> Z = F.create("z");
    static final Variable<Integer> W = F.create("w");
    static final Variable<Integer> V = F.create("v");

    // X ∈ {1,2,3}, Y ∈ {1,2,3}, allDiff — six solutions exist
    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 3))
            .variableDomain(Y, IntRangeDomain.of(1, 3))
            .allDiffConstraint(Set.of(X, Y))
            .build();

    // X, Y ∈ {1,2} with a notEquals constraint: only two moves are ever available, so the
    // solver repeatedly reconsiders whichever value it just moved away from — this forces the
    // tabu memory (and its aspiration override) to actually engage every attempt.
    static final ConstraintSatisfactionProblem TWO_VALUE_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 2))
            .variableDomain(Y, IntRangeDomain.of(1, 2))
            .notEqualsConstraint(X, Y)
            .build();

    // X, Y, Z ∈ {1,2}, pairwise notEquals: pigeonhole makes this unsatisfiable, so the search
    // never exits early and keeps reconsidering the same variables under a long tenure —
    // exercising both the tabu-block and aspiration-override branches, plus the constraint
    // weight escalation in updateWeights (which trivial single-constraint CSPs resolve before
    // ever revisiting a variable, so never actually exercise those branches).
    static final ConstraintSatisfactionProblem TRIANGLE_INFEASIBLE_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 2))
            .variableDomain(Y, IntRangeDomain.of(1, 2))
            .variableDomain(Z, IntRangeDomain.of(1, 2))
            .notEqualsConstraint(X, Y)
            .notEqualsConstraint(Y, Z)
            .notEqualsConstraint(X, Z)
            .build();

    // Five variables all initialised to the same value is maximally conflicting (10 violated
    // pairs out of C(5,2)) and takes several genuine min-conflicts steps to resolve — unlike the
    // two/three-variable single-constraint CSPs above, which resolve in one move and never let
    // total weight actually improve over time.
    static final ConstraintSatisfactionProblem FIVE_VAR_ALLDIFF_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 5))
            .variableDomain(Y, IntRangeDomain.of(1, 5))
            .variableDomain(Z, IntRangeDomain.of(1, 5))
            .variableDomain(W, IntRangeDomain.of(1, 5))
            .variableDomain(V, IntRangeDomain.of(1, 5))
            .allDiffConstraint(Set.of(X, Y, Z, W, V))
            .build();

    static Assignment allOnes() {
        return Assignment.builder().value(X, 1).value(Y, 1).value(Z, 1).value(W, 1).value(V, 1).build();
    }

    static Assignment infeasible() {
        return Assignment.builder().value(X, 1).value(Y, 1).build();
    }

    @Test
    void getLocalSolution_findsSolution() {
        val solver = TabuSearchSolver.of(1, 500, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP)).isPresent();
    }

    @Test
    void getLocalSolution_returnsEmptyWhenMaxStepsExhausted() {
        val solver = TabuSearchSolver.of(1, 0, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP)).isEmpty();
    }

    @Test
    void getLocalSolution_withObjective_returnsEmptyWhenMaxStepsExhausted() {
        val solver = TabuSearchSolver.of(1, 0, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP, a -> 0.0)).isEmpty();
    }

    @Test
    void getLocalSolution_withObjective_returnsLowestCostSolution() {
        val solver = TabuSearchSolver.of(30, 50, csp -> infeasible());
        Optional<Assignment> result = solver.getLocalSolution(CSP,
                a -> a.getValue(X).orElse(Integer.MAX_VALUE).doubleValue());
        assertThat(result).isPresent();
        assertThat(result.get().getValue(X)).hasValue(1);
    }

    @Test
    void getLocalSolution_withObjective_doesNotUpdateBestWhenCostNotImproving() {
        val solver = TabuSearchSolver.of(1, 500, csp -> infeasible());
        Optional<Assignment> result = solver.getLocalSolution(CSP, a -> 1.0);
        assertThat(result).isPresent();
    }

    @Test
    void zeroTenure_neverBlocksAMove_stillFindsSolution() {
        // tabuTenure=0 means every recorded entry has already expired by the time it would be
        // checked (step < step+0 is always false) — exercises the "not tabu" path unconditionally.
        val solver = TabuSearchSolver.builder()
                .maxAttempts(1).maxSteps(500)
                .initialAssignmentFactory(csp -> infeasible())
                .tabuTenure(0)
                .build();
        assertThat(solver.getLocalSolution(CSP)).isPresent();
    }

    @Test
    void longTenureOnTwoValueDomain_forcesTabuAndAspirationToEngage_stillFindsSolution() {
        // Domain size 2 with a long tenure: after the first move, the value just vacated is
        // tabu for the rest of the run — the only way back to it is the aspiration override,
        // so both the "blocked" and "allowed via aspiration" branches are exercised.
        val solver = TabuSearchSolver.builder()
                .maxAttempts(20).maxSteps(200)
                .initialAssignmentFactory(csp -> Assignment.builder().value(X, 1).value(Y, 1).build())
                .tabuTenure(1000)
                .build();
        val solution = solver.getLocalSolution(TWO_VALUE_CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(TWO_VALUE_CSP)).isTrue();
    }

    @Test
    void longTenureOnTwoValueDomain_objectiveOverload_stillFindsSolution() {
        val solver = TabuSearchSolver.builder()
                .maxAttempts(20).maxSteps(200)
                .initialAssignmentFactory(csp -> Assignment.builder().value(X, 1).value(Y, 1).build())
                .tabuTenure(1000)
                .build();
        val solution = solver.getLocalSolution(TWO_VALUE_CSP, a -> 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(TWO_VALUE_CSP)).isTrue();
    }

    @Test
    void infeasibleTriangleOverTwoValues_engagesTabuMemoryAndWeightEscalation() {
        val solver = TabuSearchSolver.builder()
                .maxAttempts(20).maxSteps(300)
                .initialAssignmentFactory(csp -> Assignment.builder().value(X, 1).value(Y, 1).value(Z, 1).build())
                .tabuTenure(5)
                .build();
        assertThat(solver.getLocalSolution(TRIANGLE_INFEASIBLE_CSP)).isEmpty();
    }

    @Test
    void infeasibleTriangleOverTwoValues_objectiveOverload_engagesTabuMemoryAndWeightEscalation() {
        val solver = TabuSearchSolver.builder()
                .maxAttempts(20).maxSteps(300)
                .initialAssignmentFactory(csp -> Assignment.builder().value(X, 1).value(Y, 1).value(Z, 1).build())
                .tabuTenure(5)
                .build();
        assertThat(solver.getLocalSolution(TRIANGLE_INFEASIBLE_CSP, a -> 0.0)).isEmpty();
    }

    @Test
    void fiveVariableAllDiff_multiStepResolution_engagesAspirationOverride() {
        // A short tenure relative to the number of steps this problem genuinely needs means a
        // recently-vacated value can still be the historically-best total weight by the time it's
        // reconsidered, forcing aspiration to override the tabu block on the way to a solution.
        val solver = TabuSearchSolver.builder()
                .maxAttempts(50).maxSteps(100)
                .initialAssignmentFactory(csp -> allOnes())
                .tabuTenure(3)
                .build();
        assertThat(solver.getLocalSolution(FIVE_VAR_ALLDIFF_CSP)).isPresent();
    }

    @Test
    void fiveVariableAllDiff_objectiveOverload_engagesAspirationOverride() {
        val solver = TabuSearchSolver.builder()
                .maxAttempts(50).maxSteps(100)
                .initialAssignmentFactory(csp -> allOnes())
                .tabuTenure(3)
                .build();
        assertThat(solver.getLocalSolution(FIVE_VAR_ALLDIFF_CSP, a -> 0.0)).isPresent();
    }

    // The exact combination isAdmissible needs to return true — a tabu'd candidate whose total
    // weight nonetheless beats the historical best (the aspiration override) — is vanishingly rare
    // to trigger via real search dynamics (weight escalation means a later total weight almost
    // never beats an earlier one), so it's tested directly against exact inputs instead.
    @Test
    void isAdmissible_blockedWhenTabuAndNotImproving() {
        val entry = new TabuSearchSolver.TabuEntry(1, 100);
        assertThat(TabuSearchSolver.isAdmissible(entry, 1, 5, 5.0, 3.0)).isFalse();
    }

    @Test
    void isAdmissible_aspirationOverridesTabuWhenStrictlyImproving() {
        val entry = new TabuSearchSolver.TabuEntry(1, 100);
        assertThat(TabuSearchSolver.isAdmissible(entry, 1, 5, 2.0, 3.0)).isTrue();
    }
}
