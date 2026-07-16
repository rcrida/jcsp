package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.SolverLimits;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NonNull;

/**
 * Bundles the configuration knobs {@link Solver.Factory#createSolver} accepts, so that adding a
 * new one doesn't mean adding another overload -- {@code limits} and {@code conflictExplainer}
 * used to be separate parameters (and separate overloads for "with" and "without" each), which
 * doesn't scale as more knobs get added.
 * <p>
 * {@code SolverConfig.builder().build()} reproduces {@code createSolver}'s previous unconfigured
 * defaults exactly: unlimited search, {@link MacAndFixpointConflictExplainer} for nogood learning.
 * <p>
 * {@code conflictExplainer} only affects the satisfaction chain ({@code DomWdegLubySearch}) --
 * the optimization chain's {@code BranchAndBoundSolver} doesn't do nogood learning at all, so it's
 * silently unused there. Pass {@link NullConflictExplainer#INSTANCE} to disable nogood learning
 * (CDCL) entirely, e.g. for problem shapes where learned nogoods rarely get reused.
 */
@Value
@Builder
public class SolverConfig {
    @Builder.Default @NonNull SolverLimits limits = SolverLimits.unlimited();
    @Builder.Default @NonNull ConflictExplainer conflictExplainer = MacAndFixpointConflictExplainer.INSTANCE;
}
