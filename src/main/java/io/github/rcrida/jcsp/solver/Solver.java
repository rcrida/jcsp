package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.consistency.arc.MAC;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.BacktrackingSearch;
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
        /** MAC followed by the full propagator fixpoint (all 17 propagatable constraint types). */
        Inference FULL_PROPAGATION_INFERENCE = (problem, variable, assignment) ->
                MAC.INSTANCE.apply(problem, variable, assignment)
                        .flatMap(PropagationFixpointSolver::applyFixpoint);

        /** Bisection precision for {@link BisectionConditioningSolver} in the optimization chain. */
        double DEFAULT_BISECTION_EPSILON = 1e-3;

        /**
         * Builds a solver chain tailored for satisfaction with the given search limits.
         */
        BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp, @NonNull SolverLimits limits);

        /**
         * Builds a solver chain tailored for optimization with the given search limits.
         */
        BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                 @NonNull ToDoubleFunction<Assignment> objective,
                                 @NonNull SolverLimits limits);

        /**
         * Builds a solver chain tailored for satisfaction. The chain is:
         * NodeConsistency → PropagationFixpoint → IndependentSubproblems → TreeDecomposition
         * → CutsetConditioning → BacktrackingSearch.
         * <p>
         * For problems with {@link BoundedDomain} variables, the fixpoint snaps non-singleton
         * intervals to their midpoints, giving one concrete solution for underdetermined continuous systems.
         */
        default BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp) {
            return createSolver(csp, SolverLimits.unlimited());
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
            return createSolver(csp, objective, SolverLimits.unlimited());
        }

        Factory INSTANCE = new Factory() {
            @Override
            public BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull SolverLimits limits) {
                boolean hasContinuous = csp.getVariableDomains().values().stream()
                        .anyMatch(BoundedDomain.class::isInstance);
                val nogoodStore = new NogoodStore();
                val domWdegLubySearch = DomWdegLubySearch.builder()
                        .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                        .inference(FULL_PROPAGATION_INFERENCE)
                        .limits(limits)
                        .nogoodStore(nogoodStore)
                        .conflictExplainer(MacAndFixpointConflictExplainer.INSTANCE)
                        .build();
                val treeSolver = new TreeSolver(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);
                val cutsetConditioningSolver = CutsetConditioningSolver.builder()
                        .inner(domWdegLubySearch)
                        .treeSolver(treeSolver)
                        .build();
                val treeDecompositionSolver = TreeDecompositionSolver.builder()
                        .inner(cutsetConditioningSolver)
                        .treeDecomposer(new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE))
                        .treeSolver(treeSolver)
                        .targetTreewidth(7)
                        .build();
                val independentSubproblemSolver = IndependentSubproblemSolver.builder().inner(treeDecompositionSolver).build();
                val propagationFixpointSolver = PropagationFixpointSolver.builder()
                        .inner(independentSubproblemSolver)
                        .snap(hasContinuous)
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
                        limits.resetLimitReached();
                        Optional<Assignment> result = getSolutions().findFirst();
                        if (result.isEmpty() && limits.isLimitReached()) {
                            throw new LimitExceededException(limits.getLimitHitStatistics());
                        }
                        return result;
                    }
                };
            }

            @Override
            public BoundSolver createSolver(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull ToDoubleFunction<Assignment> objective,
                                            @NonNull SolverLimits limits) {
                boolean hasContinuous = csp.getVariableDomains().values().stream()
                        .anyMatch(BoundedDomain.class::isInstance);
                val backtrackingSearch = BacktrackingSearch.builder()
                        .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                        .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                        .inference(FULL_PROPAGATION_INFERENCE)
                        .limits(limits)
                        .build();
                val branchAndBound = BranchAndBoundSolver.builder()
                        .inner(backtrackingSearch)
                        .objective(objective)
                        .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                        .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                        .inference(FULL_PROPAGATION_INFERENCE)
                        .limits(limits)
                        .build();
                Solver terminal = hasContinuous
                        ? BisectionConditioningSolver.builder()
                                .inner(branchAndBound)
                                .epsilon(DEFAULT_BISECTION_EPSILON)
                                .objective(objective)
                                .build()
                        : branchAndBound;
                val propagationFixpointSolver = PropagationFixpointSolver.builder()
                        .inner(terminal)
                        .snap(false)
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
