package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RaceLocalSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");

    // X ∈ {1,2,3}, Y ∈ {1,2,3}, allDiff — six solutions exist
    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 3))
            .variableDomain(Y, IntRangeDomain.of(1, 3))
            .allDiffConstraint(Set.of(X, Y))
            .build();

    static Assignment infeasible() {
        return Assignment.builder().value(X, 1).value(Y, 1).build();
    }

    @Test
    void findsSolutionAcrossCancellableDelegates() {
        val solver = RaceLocalSolver.builder()
                .delegate(MinConflictsSolver.of(1, 500, csp -> infeasible()))
                .delegate(TabuSearchSolver.of(1, 500, csp -> infeasible()))
                .build();
        val solution = solver.getLocalSolution(CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }

    @Test
    void objectiveOverload_findsSolution() {
        val solver = RaceLocalSolver.builder()
                .delegate(MinConflictsSolver.of(1, 500, csp -> infeasible()))
                .delegate(TabuSearchSolver.of(1, 500, csp -> infeasible()))
                .build();
        val solution = solver.getLocalSolution(CSP, a -> 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }

    @Test
    void returnsEmptyOnlyWhenEveryDelegateFails() {
        val solver = RaceLocalSolver.builder()
                .delegate(MinConflictsSolver.of(1, 0, csp -> infeasible()))
                .delegate(TabuSearchSolver.of(1, 0, csp -> infeasible()))
                .build();
        assertThat(solver.getLocalSolution(CSP)).isEmpty();
    }

    @Test
    void emptyResultFromOneDelegateDoesNotEndTheRace() {
        // One delegate exhausted with nothing (empty), the other a plain non-cancellable
        // LocalSolver that succeeds — the race must wait for and return the real result.
        LocalSolver exhausted = csp -> Optional.empty();
        LocalSolver winner = csp -> Optional.of(Assignment.builder().value(X, 1).value(Y, 2).build());
        val solver = RaceLocalSolver.builder().delegate(exhausted).delegate(winner).build();
        val solution = solver.getLocalSolution(CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }

    @Test
    void nonCancellableDelegateStillParticipates() {
        // A plain LocalSolver (not CancellableLocalSolver) just runs as-is; the race doesn't
        // require every delegate to support cancellation.
        LocalSolver plain = csp -> Optional.of(Assignment.builder().value(X, 1).value(Y, 2).build());
        val solver = RaceLocalSolver.builder().delegate(plain).build();
        val solution = solver.getLocalSolution(CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(CSP)).isTrue();
    }

    @Test
    void exceptionInADelegatePropagates() {
        LocalSolver throwing = csp -> {
            throw new IllegalStateException("boom");
        };
        val solver = RaceLocalSolver.builder().delegate(throwing).build();
        assertThatThrownBy(() -> solver.getLocalSolution(CSP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void emptyDelegateList_throwsAssertionError() {
        val solver = RaceLocalSolver.builder().build();
        assertThatThrownBy(() -> solver.getLocalSolution(CSP)).isInstanceOf(AssertionError.class);
    }

    @Test
    void cancelsEveryCancellableDelegateAfterRaceResolves() {
        var capturedCancellation = new AtomicReference<Cancellation>();
        class RecordingCancellable implements LocalSolver, CancellableLocalSolver {
            @Override
            public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
                return Optional.empty();
            }

            @Override
            public LocalSolver withCancellation(@NonNull Cancellation cancellation) {
                capturedCancellation.set(cancellation);
                return this;
            }
        }
        LocalSolver fastWinner = csp -> Optional.of(Assignment.builder().value(X, 1).value(Y, 2).build());
        val solver = RaceLocalSolver.builder()
                .delegate(fastWinner)
                .delegate(new RecordingCancellable())
                .build();
        assertThat(solver.getLocalSolution(CSP)).isPresent();
        assertThat(capturedCancellation.get()).isNotNull();
        assertThat(capturedCancellation.get().isCancelled()).isTrue();
    }
}
