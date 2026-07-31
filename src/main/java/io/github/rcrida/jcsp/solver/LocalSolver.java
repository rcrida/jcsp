package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.node.NodeConsistency;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.BinPackingConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.DiffnConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint;
import io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.NValueConstraint;
import io.github.rcrida.jcsp.constraints.nary.PartitionConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.RegularConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DivisionConstraint;
import io.github.rcrida.jcsp.constraints.binary.SubsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DisjointConstraint;
import io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.solver.listener.LocalSolverListener;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Defines an interface for solving constraint satisfaction problems (CSPs) using local search techniques.
 * Implementations of this interface aim to find assignments of values to variables that satisfy the
 * constraints of a given CSP.
 * <p>
 * Implementations may include various strategies for local search such as hill climbing, simulated annealing,
 * or the min-conflicts heuristic.
 */
public interface LocalSolver {
    Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp);

    /**
     * Returns the feasible assignment with the lowest objective value found across all attempts.
     * Each attempt runs a repair search for up to {@code maxSteps} steps; on finding a feasible
     * assignment the objective is evaluated and the attempt ends. The default ignores the objective
     * and delegates to the satisfaction search.
     */
    default Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                   @NonNull ToDoubleFunction<Assignment> objective) {
        return getLocalSolution(csp);
    }

    interface Factory {
        /**
         * Runs once, before the repair search starts, in the same order as {@link
         * FixpointPropagation#PROPAGATORS} except without the
         * outer fixpoint loop and without {@link io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint}'s GAC (Régin's algorithm):
         * that propagator is comparatively expensive (a bipartite-matching computation), and
         * repair-based search doesn't recoup that cost the way a single one-shot pass through
         * {@link PropagationFixpointSolver} does — {@link MinConflictsSolver}/{@link
         * TabuSearchSolver} never re-run propagation mid-search, they just need a decent starting
         * assignment and cheap per-step conflict scoring, and will happily walk through and repair
         * an all-different violation on their own rather than needing it ruled out up front.
         * {@link AtLeastNConstraint}/{@link AtMostNConstraint} are included here specifically
         * because those boolean constraints have no binary decomposition for AC3 to lean on
         * otherwise, so this is their only source of propagation in the local-search chain.
         */
        List<ConstraintConsistency> PREPROCESSORS = List.of(
                NodeConsistency.INSTANCE,
                FixpointConsistency.of(UnaryComparatorConstraint.class),
                FixpointConsistency.of(BinaryComparatorConstraint.class),
                FixpointConsistency.of(BinaryOffsetConstraint.class),
                FixpointConsistency.of(AbsoluteDifferenceConstraint.class),
                AC3.INSTANCE,
                FixpointConsistency.of(SumBoundConstraint.class),
                FixpointConsistency.of(SumVariableConstraint.class),
                FixpointConsistency.of(LinearBoundConstraint.class),
                FixpointConsistency.of(LinearVariableConstraint.class),
                FixpointConsistency.of(CountConstraint.class),
                FixpointConsistency.of(InverseConstraint.class),
                FixpointConsistency.of(AmongConstraint.class),
                FixpointConsistency.of(AtLeastNConstraint.class),
                FixpointConsistency.of(AtMostNConstraint.class),
                FixpointConsistency.of(AtMostOneConstraint.class),
                FixpointConsistency.of(BinPackingConstraint.class),
                FixpointConsistency.of(CumulativeConstraint.class),
                FixpointConsistency.of(GlobalCardinalityConstraint.class),
                FixpointConsistency.of(NValueConstraint.class),
                FixpointConsistency.of(LexConstraint.class),
                FixpointConsistency.of(MaxConstraint.class),
                FixpointConsistency.of(MinConstraint.class),
                FixpointConsistency.of(NaryElementConstraint.class),
                FixpointConsistency.of(NaryTuplesConstraint.class),
                FixpointConsistency.of(ProductConstraint.class),
                FixpointConsistency.of(DivisionConstraint.class),
                FixpointConsistency.of(CircuitConstraint.class),
                FixpointConsistency.of(DiffnConstraint.class),
                FixpointConsistency.of(RegularConstraint.class),
                FixpointConsistency.of(IncreasingConstraint.class),
                FixpointConsistency.of(DecreasingConstraint.class),
                FixpointConsistency.of(ReifiedConstraint.class),
                FixpointConsistency.of(ImplicationConstraint.class),
                FixpointConsistency.of(SubsetConstraint.class),
                FixpointConsistency.of(DisjointConstraint.class),
                FixpointConsistency.of(IntersectionCardinalityConstraint.class),
                FixpointConsistency.of(PartitionConstraint.class)
        );

        /**
         * Runs {@link #PREPROCESSORS} once each (no fixpoint loop, unlike {@link FixpointPropagation}),
         * notifying {@code listener} of any propagator that narrows the domain-sum -- same
         * before/after-sum-comparison idiom as {@link FixpointPropagation#logIfDomainSumReduced},
         * gated on {@code listener != LocalSolverListener.NONE} so the extra {@link
         * FixpointPropagation#domainSum} computation is skipped entirely when no listener is
         * registered.
         */
        private static Optional<ConstraintSatisfactionProblem> applyPreprocessors(
                @NonNull ConstraintSatisfactionProblem csp, @NonNull LocalSolverListener listener) {
            var current = Optional.of(csp);
            for (var p : PREPROCESSORS) {
                if (current.isEmpty()) return current;
                var before = current.get();
                current = p.apply(before);
                if (listener != LocalSolverListener.NONE) {
                    current.ifPresent(after -> {
                        double beforeSum = FixpointPropagation.domainSum(before);
                        double afterSum = FixpointPropagation.domainSum(after);
                        if (afterSum < beforeSum) {
                            listener.onPropagatorProgress(p, before.getVariableDomains(), after.getVariableDomains(), beforeSum, afterSum);
                        }
                    });
                }
            }
            return current;
        }

        Factory INSTANCE = new Factory() {
            @Override
            public LocalSolver createLocalSolver(int maxAttempts, int maxSteps,
                                                  @NonNull InitialAssignmentFactory initialAssignmentFactory,
                                                  @NonNull LocalSolverConfig config) {
                // Race min-conflicts against tabu search rather than committing to one — a routing
                // heuristic for this exact pair was tried and falsified before for a different pair of
                // solvers (BacktrackingSearch vs DomWdegLubySearch), so this avoids needing to predict
                // which strategy suits a given problem shape.
                val raced = IndependentSubproblemLocalSolver.builder()
                        .delegate(RaceLocalSolver.builder()
                                .delegate(MinConflictsSolver.builder()
                                        .maxAttempts(maxAttempts).maxSteps(maxSteps)
                                        .initialAssignmentFactory(initialAssignmentFactory)
                                        .listener(config.getListener()).build())
                                .delegate(TabuSearchSolver.builder()
                                        .maxAttempts(maxAttempts).maxSteps(maxSteps)
                                        .initialAssignmentFactory(initialAssignmentFactory)
                                        .listener(config.getListener()).build())
                                .build())
                        .build();
                val walkSat = IndependentSubproblemLocalSolver.builder()
                        .delegate(WalkSATSolver.builder()
                                .maxAttempts(maxAttempts).maxSteps(maxSteps)
                                .initialAssignmentFactory(initialAssignmentFactory)
                                .listener(config.getListener()).build())
                        .build();
                val lns = IndependentSubproblemLocalSolver.builder()
                        .delegate(LargeNeighborhoodSolver.builder()
                                .maxAttempts(maxAttempts).maxSteps(maxSteps)
                                .initialAssignmentFactory(initialAssignmentFactory)
                                .listener(config.getListener()).build())
                        .build();
                return new LocalSolver() {
                    @Override
                    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
                        return applyPreprocessors(csp, config.getListener()).flatMap(r -> {
                            boolean allBoolean = r.getVariableDomains().values().stream()
                                    .allMatch(d -> d.isSingleton() || WalkSATSolver.canFlip(d));
                            boolean noCountingConstraints = r.getConstraints().stream()
                                    .noneMatch(c -> c instanceof ExactlyOneConstraint
                                            || c instanceof AtLeastNConstraint);
                            return (allBoolean && noCountingConstraints ? walkSat : raced).getLocalSolution(r);
                        });
                    }

                    @Override
                    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                                 @NonNull ToDoubleFunction<Assignment> objective) {
                        return applyPreprocessors(csp, config.getListener()).flatMap(r -> {
                            boolean hasExactlyOne = r.getConstraints().stream()
                                    .anyMatch(c -> c instanceof ExactlyOneConstraint);
                            return (hasExactlyOne ? lns : raced).getLocalSolution(r, objective);
                        });
                    }
                };
            }
        };

        /**
         * Builds a {@link LocalSolver} configured via {@link LocalSolverConfig} (e.g. a
         * {@link LocalSolverListener} to observe repair-search progress).
         */
        LocalSolver createLocalSolver(int maxAttempts, int maxSteps,
                                       @NonNull InitialAssignmentFactory initialAssignmentFactory,
                                       @NonNull LocalSolverConfig config);

        /**
         * @deprecated use {@link #createLocalSolver(int, int, InitialAssignmentFactory, LocalSolverConfig)}.
         */
        @Deprecated
        default LocalSolver createLocalSolver(int maxAttempts, int maxSteps,
                                              @NonNull InitialAssignmentFactory initialAssignmentFactory) {
            return createLocalSolver(maxAttempts, maxSteps, initialAssignmentFactory, LocalSolverConfig.builder().build());
        }
    }
}
