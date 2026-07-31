package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.solver.listener.LocalSolverListener;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Bundles configuration for {@link LocalSolver.Factory#createLocalSolver}, mirroring
 * {@link SolverConfig}'s role for {@link Solver.Factory#createSolver}. Deliberately minimal today
 * (just {@link #listener}): local search has no existing analogue of {@link SolverConfig}'s
 * {@code limits}/{@code nogoodLearningEnabled}/{@code statistics} -- each attempt currently seeds
 * its own independent {@link io.github.rcrida.jcsp.assignments.Statistics} via
 * {@link io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory}, not a shared
 * token -- retrofitting that is a separate, larger change, out of scope here.
 */
@Value
@Builder
public class LocalSolverConfig {
    @Builder.Default @NonNull LocalSolverListener listener = LocalSolverListener.NONE;
}
