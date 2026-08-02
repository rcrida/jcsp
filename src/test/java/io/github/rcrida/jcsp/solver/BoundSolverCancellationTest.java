package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import static io.github.rcrida.jcsp.solver.BoundSolverLimitsTest.satisfiable;
import static io.github.rcrida.jcsp.solver.BoundSolverLimitsTest.unsatisfiable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundSolverCancellationTest {

    /**
     * A token cancelled before the call ever starts is detected during {@link
     * PropagationFixpointSolver}'s one-time preprocessing pass -- which runs before {@link
     * DomWdegLubySearch} ever gets control -- and that layer has no distinct single-solution
     * algorithm of its own, so it stays silent (matching {@link BranchAndBoundSolver}/{@link
     * SetBranchingSolver}'s own silent behavior), the same as {@link
     * #getSolutionReturnsEmptyForGenuineUnsat_notCancelled}. {@link SolverCancelledException} is
     * only ever thrown when cancellation happens specifically while {@link DomWdegLubySearch}'s own
     * search is running, after preprocessing has already converged -- see
     * {@link #listenerCancelsSearchOnceNodeThresholdCrossed}.
     */
    @Test
    void getSolutionReturnsEmptySilently_whenCancelledBeforeSearchStarts() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().cancellation(cancellation).build());

        assertThat(solver.getSolution()).isEmpty();
    }

    @Test
    void getSolutionsStreamTruncatesSilentlyWhenCancelled() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().cancellation(cancellation).build());

        assertThat(solver.getSolutions().findFirst()).isEmpty();
    }

    @Test
    void getSolutionReturnsEmptyForGenuineUnsat_notCancelled() {
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(unsatisfiable());

        assertThat(solver.getSolution()).isEmpty();
    }

    @Test
    void listenerCancelsSearchOnceNodeThresholdCrossed() {
        var cancellation = new Cancellation();
        SolverListener listener = new SolverListener() {
            @Override
            public void onNodeExplored(Variable<?> variable, Object value, Assignment assignment) {
                if (assignment.getStatistics().getNodesExplored().get() >= 5) {
                    cancellation.cancel();
                }
            }
        };
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().listener(listener).cancellation(cancellation).build());

        assertThatThrownBy(solver::getSolution).isInstanceOf(SolverCancelledException.class);
        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void cancellationNever_cancelThrowsUnsupportedOperationException() {
        assertThatThrownBy(Cancellation.NEVER::cancel).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void freshCancellation_cancelWorksNormally() {
        var cancellation = new Cancellation();
        assertThat(cancellation.isCancelled()).isFalse();

        cancellation.cancel();

        assertThat(cancellation.isCancelled()).isTrue();
    }
}
