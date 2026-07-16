package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.LimitExceededException;
import io.github.rcrida.jcsp.solver.MacAndFixpointConflictExplainer;
import io.github.rcrida.jcsp.solver.NodeConsistentSolver;
import io.github.rcrida.jcsp.solver.PropagationFixpointSolver;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standalone (non-JUnit, not run by surefire/jacoco) harness to isolate how much of a hard search's
 * wall-clock cost is attributable to {@code FixpointConsistency.of(NogoodConstraint.class)} rescanning
 * every accumulated nogood on every propagation round (see {@code PropagationFixpointSolver.PROPAGATORS}
 * and {@code DomWdegLubySearch.searchOne/searchStream}, which call {@code nogoodStore.apply(csp)} on
 * every node).
 *
 * <p>Both variants explore the exact same fixed node budget ({@link #NODE_LIMIT}) on the exact same
 * hard CSP, wired identically except for one field: the {@link NogoodStore} capacity. {@code default}
 * uses {@link NogoodStore#forProblem} (production sizing); {@code capped} uses a capacity-1 store,
 * which still learns but immediately evicts, so it approximates "no accumulated nogood rescanning cost".
 * A wall-clock gap between the two variants under the same node budget is attributable to nogood-store
 * scanning overhead, not to different search decisions (dom/wdeg weighting and value ordering are
 * unaffected by nogood-store capacity).
 *
 * <p>Run via {@code mvn test-compile} then
 * {@code java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark}.
 */
public final class NogoodPropagationBenchmark {

    private static final long NODE_LIMIT = 300_000L;
    private static final long FORCED_NODE_LIMIT = 500L;
    private static final int TRIALS = 3;

    private NogoodPropagationBenchmark() {
    }

    public static void main(String[] args) {
        record Scenario(String name, ConstraintSatisfactionProblem csp) {
        }
        List<Scenario> scenarios = List.of(
                new Scenario("Golomb ruler order=6 length=16 (UNSAT, one below optimal 17)", golombRuler(6, 16)),
                new Scenario("Golomb ruler order=7 length=24 (UNSAT, one below optimal 25)", golombRuler(7, 24))
        );

        for (Scenario scenario : scenarios) {
            System.out.println();
            System.out.println("=== " + scenario.name() + " ===");
            run("default (NogoodStore.forProblem)", scenario.csp(), NODE_LIMIT, () -> NogoodStore.forProblem(scenario.csp()));
            run("capped (NogoodStore capacity=1)", scenario.csp(), NODE_LIMIT, () -> new NogoodStore(1));
        }

        // Forced-truncation comparison: a node budget small enough that both variants are guaranteed
        // to hit it (rather than complete the proof), so nodesExplored -- and therefore a nodes/sec
        // regression metric -- is directly comparable rather than "n/a (finished under limit)".
        ConstraintSatisfactionProblem order7 = golombRuler(7, 24);
        System.out.println();
        System.out.println("=== Golomb ruler order=7 length=24, forced truncation at " + FORCED_NODE_LIMIT + " nodes ===");
        run("default (NogoodStore.forProblem)", order7, FORCED_NODE_LIMIT, () -> NogoodStore.forProblem(order7));
        run("capped (NogoodStore capacity=1)", order7, FORCED_NODE_LIMIT, () -> new NogoodStore(1));
    }

    private static void run(String label, ConstraintSatisfactionProblem csp, long nodeLimit, java.util.function.Supplier<NogoodStore> storeFactory) {
        List<Long> millis = new ArrayList<>();
        List<Statistics> stats = new ArrayList<>();
        for (int trial = 0; trial < TRIALS; trial++) {
            SolverLimits limits = SolverLimits.ofNodes(nodeLimit);
            Solver chain = buildChain(storeFactory.get(), limits);
            long start = System.nanoTime();
            try {
                chain.getSolution(csp);
            } catch (LimitExceededException e) {
                stats.add(e.getStatistics());
                millis.add((System.nanoTime() - start) / 1_000_000);
                continue;
            }
            // Finished (SAT or genuinely proven UNSAT) before hitting the node limit -- still timed,
            // but no longer an apples-to-apples "same node budget" comparison for this trial.
            millis.add((System.nanoTime() - start) / 1_000_000);
            stats.add(limits.isLimitReached() ? limits.getLimitHitStatistics() : null);
        }
        double avgMillis = millis.stream().mapToLong(Long::longValue).average().orElse(0);
        Statistics first = stats.isEmpty() ? null : stats.get(0);
        String nodes = first == null ? "n/a (finished under limit)" : String.valueOf(first.getNodesExplored().get());
        String nodesPerSec = (first == null || avgMillis == 0) ? "n/a"
                : String.format("%.0f", first.getNodesExplored().get() / (avgMillis / 1000));
        System.out.printf("%-35s avg=%.0fms trials=%s nodesExplored=%-8s nodes/sec=%s%n",
                label, avgMillis, millis, nodes, nodesPerSec);
    }

    /** Mirrors {@code Solver.Factory}'s satisfaction chain, minus the decomposition decorators
     * (not needed here: these Golomb ruler instances are a single dense connected component with
     * treewidth above the tree-decomposition threshold, so those decorators would be pure passthrough). */
    private static Solver buildChain(NogoodStore nogoodStore, SolverLimits limits) {
        DomWdegLubySearch domWdegLubySearch = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(limits)
                .nogoodStore(nogoodStore)
                .conflictExplainer(MacAndFixpointConflictExplainer.INSTANCE)
                .maxRestarts(Integer.MAX_VALUE)
                .build();
        Solver propagationFixpointSolver = PropagationFixpointSolver.builder()
                .inner(domWdegLubySearch)
                .snap(true)
                .build();
        return NodeConsistentSolver.builder().inner(propagationFixpointSolver).build();
    }

    /** Same construction as {@code GolombRulerTest}, parameterized so different orders can be compared. */
    private static ConstraintSatisfactionProblem golombRuler(int n, int maxLength) {
        Variable.Factory f = Variable.Factory.INSTANCE;
        List<Variable<Integer>> marks = new ArrayList<>();
        for (int i = 0; i < n; i++) marks.add(f.create("m" + i));

        var builder = ConstraintSatisfactionProblem.builder();
        marks.forEach(m -> builder.variableDomain(m, IntRangeDomain.of(0, maxLength)));
        builder.equalsConstraint(marks.get(0), 0);
        for (int i = 0; i < n - 1; i++) {
            builder.comparatorConstraint(marks.get(i), Operator.LT, marks.get(i + 1));
        }

        List<Variable<Integer>> diffs = new ArrayList<>();
        Variable<Integer> firstGap = null;
        Variable<Integer> lastGap = null;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Variable<Integer> d = f.create("d" + i + j);
                builder.variableDomain(d, IntRangeDomain.of(1, maxLength));
                builder.linearConstraint(Map.of(marks.get(j), 1, marks.get(i), -1, d, -1), Operator.EQ, 0);
                diffs.add(d);
                if (i == 0 && j == 1) firstGap = d;
                if (i == n - 2 && j == n - 1) lastGap = d;
            }
        }
        builder.allDiffConstraint(Set.copyOf(diffs));
        builder.comparatorConstraint(firstGap, Operator.LT, lastGap);
        return builder.build();
    }
}
