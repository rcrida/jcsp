package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.consistency.arc.MAC;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.solver.tree.TreeSolver;
import io.github.rcrida.jcsp.solver.tree.cutsetconditioning.CutsetConditioningSolver;
import io.github.rcrida.jcsp.solver.tree.decomposition.TreeDecompositionSolver;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposerImpl;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.MinimumDegreeVariableSelector;
import io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.tree.sorter.BFSTopologicalSorter;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Represents a generic interface responsible for solving constraint satisfaction problems (CSPs).
 * A CSP consists of a set of variables, their potential values (domains), and constraints
 * that specify relationships between these variables.
 * <p>
 * Implementations of the Solver interface provide methods to derive solutions for a given CSP,
 * either as a single solution or as a stream of solutions.
 */
public interface Solver {
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);

    default Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    interface Factory {
        /**
         * MAC followed by {@code fixpointPropagation}'s propagator fixpoint.
         * <p>
         * Seeds {@code applyFixpoint}'s round-1 dirty-variable hint from this call's own inputs,
         * rather than passing {@code null} (full first-round scan) as before: {@code problem} is
         * exactly the parent search node's already-converged CSP, since nothing else touches it
         * between when the parent's own inference call finished and this one starts. So the diff
         * between {@code problem} and the post-MAC result (via {@link
         * FixpointPropagation#changedVariables}) is exactly what changed by branching on
         * {@code variable} plus whatever MAC narrowed as a result -- nothing else could have.
         * <p>
         * Doesn't need to separately account for newly-learned nogoods (e.g. one recorded while
         * backtracking an earlier sibling value at this same node): {@code problem} here is always
         * {@code cspWithNogoods} from {@link DomWdegLubySearch}, i.e. already {@code
         * nogoodStore.apply(csp)} -- freshly re-merged with the current nogood set immediately
         * before this call, for every candidate value, not just the first. Neither {@link
         * MAC#apply} nor {@link FixpointPropagation#applyFixpoint} ever changes a CSP's
         * nogood set (both only ever replace domain entries via {@code toBuilder()}), so {@code
         * problem.getNogoods()} and the post-MAC result's are always identical -- there is no
         * "newly learned since problem" case to seed for here.
         * <p>
         * Builds a named {@link Inference} (not a lambda) so it can additionally override {@link
         * Inference#applyWithReason}: a genuine single pass combining propagation and, on failure,
         * explanation, computed inline at the exact point of the wipeout instead of a separate,
         * from-scratch re-derivation. Falls back to the current assignment (matching {@link
         * Inference#applyWithReason}'s own default) whenever neither MAC nor the fixpoint could
         * derive anything tighter -- e.g. a wipeout on a non-singleton neighbour, or a Hall-set
         * violation over ungapped-but-non-singleton domains that even {@link io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint}'s
         * gaplessness gate declines to cite. Parameterized by {@code fixpointPropagation} (which may
         * be {@link FixpointPropagation#FULL} or a per-CSP-filtered instance -- hence this method's
         * own name doesn't say "full") so {@link #FULL_PROPAGATION_INFERENCE} and the per-solve
         * instance built in {@link #createSolver} below share this same body rather than duplicating
         * it.
         */
        static Inference propagationInference(@NonNull FixpointPropagation fixpointPropagation) {
            return new Inference() {
                @Override
                public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem,
                                                                      Variable<?> variable, Assignment assignment) {
                    return MAC.INSTANCE.apply(problem, variable, assignment)
                            .flatMap(afterMac -> fixpointPropagation.applyFixpoint(afterMac,
                                    FixpointPropagation.changedVariables(problem.getVariableDomains(), afterMac.getVariableDomains()),
                                    assignment.listener(), assignment.getStatistics(), assignment.cancellation()));
                }

                @Override
                public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                                          Variable<?> variable, Assignment assignment) {
                    ConsistencyResult macResult = MAC.INSTANCE.applyWithReason(problem, variable, assignment);
                    if (macResult.isInfeasible()) {
                        return macResult.reason() != null ? macResult
                                : ConsistencyResult.infeasible(GroundNogoodConstraint.of(assignment.getValues()));
                    }
                    ConstraintSatisfactionProblem afterMac = macResult.problem();
                    ConsistencyResult fixpointResult = fixpointPropagation.applyFixpointWithReason(afterMac,
                            FixpointPropagation.changedVariables(problem.getVariableDomains(), afterMac.getVariableDomains()),
                            assignment.listener(), assignment.getStatistics(), assignment.cancellation());
                    if (fixpointResult.isInfeasible() && fixpointResult.reason() == null) {
                        return ConsistencyResult.infeasible(GroundNogoodConstraint.of(assignment.getValues()));
                    }
                    return fixpointResult;
                }
            };
        }

        /**
         * The unfiltered, always-everything {@link Inference}, backed by {@link
         * FixpointPropagation#FULL} -- the direct/manual-use fallback referenced by tests that build
         * a terminal solver ({@link DomWdegLubySearch}, {@link BranchAndBoundSolver}) themselves,
         * bypassing {@link #createSolver}. {@link #createSolver} itself never uses this singleton: it
         * builds its own per-solve {@link Inference} from the CSP-filtered {@link FixpointPropagation}
         * computed via {@link FixpointPropagation.Factory#forProblem}.
         */
        Inference FULL_PROPAGATION_INFERENCE = propagationInference(FixpointPropagation.FULL);

        /** Bisection precision for {@link BisectionConditioningSolver} in the optimization chain. */
        double DEFAULT_BISECTION_EPSILON = 1e-3;

        /**
         * Picks between the real reason-deriving {@link #propagationInference} result and a wrapper
         * that never derives one, per {@code SolverConfig.isNogoodLearningEnabled()}. Shared by both
         * chains' wiring below so the choice lives in exactly one place: the terminal solvers
         * ({@link DomWdegLubySearch}, {@link BranchAndBoundSolver}) stay free of any "should I
         * explain" branch of their own -- they just call {@link Inference#applyWithReason} and react
         * to whatever reason comes back.
         */
        static Inference nogoodLearningInference(@NonNull SolverConfig config,
                                                  @NonNull FixpointPropagation fixpointPropagation) {
            Inference base = propagationInference(fixpointPropagation);
            return config.isNogoodLearningEnabled() ? base : Inference.withoutReasonTracking(base);
        }

        /**
         * Builds a solver chain tailored for satisfaction with the given {@link SolverConfig}.
         */
        BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp, @NonNull SolverConfig config);

        /**
         * Builds a solver chain tailored for optimization with the given {@link SolverConfig}.
         */
        BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                 @NonNull ToDoubleFunction<Assignment> objective,
                                 @NonNull SolverConfig config);

        /**
         * Builds a solver chain tailored for satisfaction. The chain is:
         * NodeConsistency → PropagationFixpoint → IndependentSubproblems → TreeDecomposition
         * → CutsetConditioning → TreeSolver / DomWdegLubySearch.
         * <p>
         * For problems with {@link BoundedDomain} variables, the fixpoint snaps non-singleton
         * intervals to their midpoints, giving one concrete solution for underdetermined continuous systems.
         */
        default BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp) {
            return createSolver(csp, SolverConfig.builder().build());
        }

        /**
         * Builds a solver chain tailored for optimization.
         * <p>
         * <b>Discrete CSPs</b> (no {@link BoundedDomain} variables):
         * NodeConsistency → PropagationFixpoint → BranchAndBound. The objective is called on
         * <em>partial</em> assignments for pruning, so it must return a lower bound on the cost
         * of any completion. The lower-bound property {@code objective(partial) ≤ objective(completion)}
         * must hold for all completions.
         * <p>
         * <b>Continuous CSPs</b> (with {@link BoundedDomain} variables):
         * NodeConsistency → PropagationFixpoint → BisectionConditioning → BranchAndBound.
         * {@link BisectionConditioningSolver} applies the objective only to <em>complete</em>
         * assignments; no lower-bound property is required and {@code getValue(v).orElseThrow()} is safe.
         */
        default BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                         @NonNull ToDoubleFunction<Assignment> objective) {
            return createSolver(csp, objective, SolverConfig.builder().build());
        }

        Factory INSTANCE = new Factory() {
            @Override
            public BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull SolverConfig config) {
                val limits = config.getLimits();
                val cancellation = config.getCancellation();
                boolean hasContinuous = csp.getVariableDomains().values().stream()
                        .anyMatch(BoundedDomain.class::isInstance);
                boolean hasSets = csp.getVariableDomains().values().stream()
                        .anyMatch(SetBoundedDomain.class::isInstance);
                val fixpointPropagation = FixpointPropagation.Factory.INSTANCE.forProblem(csp, config.isNogoodLearningEnabled());
                val treeSolver = new TreeSolver(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);
                val inference = nogoodLearningInference(config, fixpointPropagation);
                // Built fresh per sub-problem (not shared) so each independent sub-problem gets its own
                // NogoodStore, correctly sized and scoped to just its own variables -- see
                // IndependentSubproblemSolver's javadoc for why sharing one across sub-problems is unsound.
                // treeSolver is stateless (no accumulated learning) and safe to share across sub-problems.
                Function<ConstraintSatisfactionProblem, Solver> innerFactory = sub -> {
                    val nogoodStore = NogoodStore.forProblem(sub);
                    val domWdegLubySearch = DomWdegLubySearch.builder()
                            .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                            .inference(inference)
                            .limits(limits)
                            .nogoodStore(nogoodStore)
                            .statistics(config.getStatistics())
                            .listener(config.getListener())
                            .cancellation(cancellation)
                            // Effectively unbounded: getSolution() now reaches Luby-restart search directly
                            // (see BoundSolver#getSolution below), so DEFAULT_MAX_RESTARTS's cap would silently
                            // turn SolverLimits.unlimited() into a bounded search. SolverLimits (node/time)
                            // remains the only intended way to bound a search; restarts should never be it.
                            .maxRestarts(Integer.MAX_VALUE)
                            .build();
                    val cutsetConditioningSolver = CutsetConditioningSolver.builder()
                            .inner(domWdegLubySearch)
                            .treeSolver(treeSolver)
                            .build();
                    return TreeDecompositionSolver.builder()
                            .inner(cutsetConditioningSolver)
                            .treeDecomposer(new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE))
                            .treeSolver(treeSolver)
                            .targetTreewidth(7)
                            .build();
                };
                val independentSubproblemSolver = IndependentSubproblemSolver.builder().innerFactory(innerFactory).build();
                Solver afterPropagation = hasSets
                        ? SetBranchingSolver.builder().inner(independentSubproblemSolver).listener(config.getListener())
                                .limits(limits).cancellation(cancellation).statistics(config.getStatistics())
                                .fixpointPropagation(fixpointPropagation).build()
                        : independentSubproblemSolver;
                val propagationFixpointSolver = PropagationFixpointSolver.builder()
                        .inner(afterPropagation)
                        .snap(hasContinuous)
                        .listener(config.getListener())
                        .statistics(config.getStatistics())
                        .cancellation(cancellation)
                        .fixpointPropagation(fixpointPropagation)
                        .build();
                Solver chain = NodeConsistentSolver.builder().inner(propagationFixpointSolver).build();
                return new BoundSolver() {
                    @Override
                    public Stream<Assignment> getSolutions() {
                        limits.resetLimitReached();
                        return chain.getSolutions(csp);
                    }

                    @Override
                    public Optional<Assignment> getSolution() {
                        // Delegates to chain.getSolution(csp) (not getSolutions().findFirst()) so this
                        // actually reaches DomWdegLubySearch.getSolution()'s Luby-restart search rather than
                        // a plain first-element-of-stream traversal; DomWdegLubySearch throws
                        // LimitExceededException itself when limits are hit, so no manual check is needed here.
                        limits.resetLimitReached();
                        return chain.getSolution(csp);
                    }
                };
            }

            @Override
            public BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull ToDoubleFunction<Assignment> objective,
                                            @NonNull SolverConfig config) {
                val limits = config.getLimits();
                val cancellation = config.getCancellation();
                boolean hasSets = csp.getVariableDomains().values().stream()
                        .anyMatch(SetBoundedDomain.class::isInstance);
                val fixpointPropagation = FixpointPropagation.Factory.INSTANCE.forProblem(csp, config.isNogoodLearningEnabled());
                // Handles any BoundedDomain variables itself -- see this class's own Javadoc and
                // ADR-0009 -- rather than being nested inside a BisectionConditioningSolver that runs
                // first, so it's the chain's terminal solver unconditionally.
                Solver terminal = BranchAndBoundSolver.builder()
                        .objective(objective)
                        .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                        .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                        .inference(nogoodLearningInference(config, fixpointPropagation))
                        .limits(limits)
                        .nogoodStore(NogoodStore.forProblem(csp))
                        .statistics(config.getStatistics())
                        .listener(config.getListener())
                        .cancellation(cancellation)
                        .build();
                Solver afterPropagation = hasSets
                        ? SetBranchingSolver.builder().inner(terminal).objective(objective).listener(config.getListener())
                                .limits(limits).cancellation(cancellation).statistics(config.getStatistics())
                                .fixpointPropagation(fixpointPropagation).build()
                        : terminal;
                val propagationFixpointSolver = PropagationFixpointSolver.builder()
                        .inner(afterPropagation)
                        .snap(false)
                        .listener(config.getListener())
                        .statistics(config.getStatistics())
                        .cancellation(cancellation)
                        .fixpointPropagation(fixpointPropagation)
                        .build();
                Solver chain = NodeConsistentSolver.builder().inner(propagationFixpointSolver).build();
                return new BoundSolver() {
                    @Override
                    public Stream<Assignment> getSolutions() {
                        return chain.getSolutions(csp);
                    }

                    @Override
                    public Optional<Assignment> getSolution() {
                        return chain.getSolutions(csp).reduce((a, b) -> b);
                    }
                };
            }
        };
    }
}
