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
     * PropagationFixpointSolver}'s one-time preprocessing pass, before {@link DomWdegLubySearch}
     * ever gets control -- and it throws from there too, rather than returning empty.
     * <p>
     * This test used to assert the opposite, and that was the bug ADR-0043 fixed: preprocessing
     * swallowed the cancellation, so a cancelled solve of a <em>satisfiable</em> problem was
     * indistinguishable from {@link #getSolutionReturnsEmptyForGenuineUnsat_notCancelled}, and
     * {@code Xcsp3ProblemRunner} reported {@code s UNSATISFIABLE} for it. The contract is now that
     * {@link Optional#empty()} means proven unsatisfiable and nothing else.
     */
    @Test
    void getSolutionThrows_whenCancelledBeforeSearchStarts() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().cancellation(cancellation).build());

        assertThatThrownBy(solver::getSolution)
                .isInstanceOf(SolverCancelledException.class)
                .isInstanceOf(InconclusiveSearchException.class);
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
                if (assignment.getStatistics().getNodesExplored() >= 5) {
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
