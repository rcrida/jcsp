package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.solver.neighbourhood.DecisionSource;
import io.github.rcrida.jcsp.solver.neighbourhood.LargeNeighbourhoodSearch;
import io.github.rcrida.jcsp.solver.neighbourhood.NeighbourhoodGenerator;
import io.github.rcrida.jcsp.solver.neighbourhood.NeighbourhoodRoot;
import io.github.rcrida.jcsp.solver.neighbourhood.NeighbourhoodSchedule;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Value;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@link BranchAndBoundSolver} that also searches the neighbourhood of its incumbent, between the runs of
 * its complete search. {@link BranchAndBoundSolver} on its own is this class without the neighbourhoods and
 * is unchanged by it: this class opens the same per-solve {@link BranchAndBoundSolver.Search} and makes its
 * runs in a different order.
 * <p>
 * A neighbourhood run takes the best solution so far, fixes most of the decisions that produced it, and
 * searches the few that were left free for a better solution, under the objective's cut and a short cutoff,
 * all as a search of the same {@link BranchAndBoundSolver.Search} so that it shares its incumbent and bound.
 * It finds improvements in a stretch of time that the complete search, which spends its restarts on the whole
 * tree, often does not, and it matters most when the instance is too large to prove. The unrestricted runs it
 * is interleaved with are exactly those of the complete search, with the same cutoffs and the same random
 * draws, and are what decides optimality: a neighbourhood run searches part of the tree, so its ending proves
 * nothing and never ends the stream, and a drained stream is a proof as it is for the complete search alone.
 * How many runs of each kind, which decisions each frees and what a run costs are the {@link
 * LargeNeighbourhoodSearch}'s. See ADR-0065.
 * <p>
 * Applies to a problem with no {@link io.github.rcrida.jcsp.domains.BoundedDomain} variable only, since a
 * neighbourhood fixes and frees discrete decisions; for one with a continuous variable it is the complete
 * search alone, as it is with a {@link LargeNeighbourhoodSearch} that has no generators.
 */
@Value
public class NeighbourhoodSearchSolver implements Solver {
    @NonNull BranchAndBoundSolver inner;
    @NonNull LargeNeighbourhoodSearch neighbourhoodSearch;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        BranchAndBoundSolver.Search search = inner.open(csp);
        int[] unrestricted = {0};
        if (!neighbourhoodSearch.isEnabled() || search.hasContinuous) {
            return search.improvements(() -> search.unrestrictedRun(++unrestricted[0]));
        }
        Session session = new Session(search);
        return search.improvements(() -> session.due()
                ? session.run()
                : session.counted(search.unrestrictedRun(++unrestricted[0])));
    }

    /**
     * The neighbourhood runs of one solve: the {@link NeighbourhoodSchedule} that says when one is due, the
     * generators, decision source and root that make one, and the nodes each kind of run has spent.
     * <p>
     * A neighbourhood run keeps its own variable selector and LP gate, and draws from its own random stream,
     * so the unrestricted runs that carry the proof see the state, and make the draws, they would have had
     * without it. Its improvements are written to the shared phase memory, where the unrestricted runs
     * follow them.
     */
    private final class Session {
        private final BranchAndBoundSolver.Search search;
        private final Statistics statistics = inner.getStatistics();
        private final NeighbourhoodSchedule schedule = new NeighbourhoodSchedule(neighbourhoodSearch);
        private final List<NeighbourhoodGenerator> generators;
        private final DecisionSource decisions;
        private final NeighbourhoodRoot root;
        private final AdaptiveVariableSelector selector;
        private final LpGate lpGate;
        private final RestartRandomization randomization = inner.getRestartRandomization().forSearch(1);
        private int runs;
        private long unrestrictedNodes;
        private long neighbourhoodNodes;

        Session(BranchAndBoundSolver.Search search) {
            this.search = search;
            this.generators = neighbourhoodSearch.generators().stream()
                    .map(factory -> factory.create(search.csp, inner.getObjective())).toList();
            Assignment emptyRoot = inner.rootAssignment();
            this.decisions = neighbourhoodSearch.decisions().create(inner.getInference(), inner.getSelectorFactory(), emptyRoot);
            this.root = new NeighbourhoodRoot(inner.getInference(), emptyRoot);
            this.selector = search.newSelector();
            this.lpGate = search.newLpGate();
        }

        /** Whether a neighbourhood run is due: there is a solution to start from and the schedule says so. */
        boolean due() {
            return search.incumbent.solution != null && schedule.due(unrestrictedNodes, neighbourhoodNodes);
        }

        /** {@code run}, an unrestricted run, counting the nodes it spends towards the neighbourhood runs' share. */
        BranchAndBoundSolver.Run counted(BranchAndBoundSolver.Run run) {
            long startNodes = statistics.getNodesExplored();
            return new BranchAndBoundSolver.Run(run.state(),
                    run.found().onClose(() -> unrestrictedNodes += statistics.getNodesExplored() - startNodes));
        }

        /** The next neighbourhood run. */
        BranchAndBoundSolver.Run run() {
            long startNodes = statistics.getNodesExplored();
            Random random = randomFor(++runs);
            int generator = schedule.generator(random);
            BranchAndBoundSolver.RunState state = BranchAndBoundSolver.RunState.restricted(
                    neighbourhoodSearch.repairFailures(), neighbourhoodSearch.repairNodes(), statistics);
            selector.onRestart(random);
            double before = search.incumbent.cost;
            long[] setup = {0};
            Stream<Assignment> found = startFrom(random, generator, state, setup).onClose(() -> {
                long nodes = statistics.getNodesExplored() - startNodes + setup[0];
                neighbourhoodNodes += nodes;
                schedule.record(generator, search.incumbent.cost < before, state.aborted(), nodes);
            });
            return new BranchAndBoundSolver.Run(state, found);
        }

        private Random randomFor(int run) {
            Random random = randomization.randomFor(run);
            return random != null ? random : new Random(run);
        }

        /**
         * The search below the incumbent's neighbourhood: the decisions of the incumbent, all but the freed
         * ones fixed at its values under the objective's cut, and an ordinary bounded search from there. Empty
         * when the incumbent has fewer than two decisions or the fixed part is inconsistent. {@code setup}
         * receives the propagations spent getting there, one to replay each decision and one to fix it.
         */
        private Stream<Assignment> startFrom(Random random, int generator, BranchAndBoundSolver.RunState state,
                                             long[] setup) {
            try {
                Assignment solution = search.incumbent.solution;
                List<Variable<?>> decisionList = decisions.decisionsOf(search.csp, solution);
                setup[0] = 2L * decisionList.size();
                if (decisionList.size() < 2) {
                    return Stream.empty();
                }
                Set<Variable<?>> free = generators.get(generator)
                        .free(decisionList, solution, schedule.freeCount(decisionList.size()), random);
                BranchAndBoundSolver.Bound bound = search.tightened();
                ConstraintSatisfactionProblem tightened =
                        bound == search.rootBound ? search.csp : bound.applyTo(search.csp);
                Set<Variable<?>> cutVariables = bound == search.rootBound ? Set.of() : bound.cut().getVariables();
                return root.fix(tightened, cutVariables, decisionList, free, solution)
                        .map(fixed -> search.restrictedRun(fixed.csp(), fixed.assignment(), bound, selector, lpGate, state)
                                .found())
                        .orElseGet(Stream::empty);
            } catch (SolverCancelledException e) {
                // Cancelled while replaying or fixing, which the search's own handling does not cover.
                return Stream.empty();
            }
        }
    }
}
