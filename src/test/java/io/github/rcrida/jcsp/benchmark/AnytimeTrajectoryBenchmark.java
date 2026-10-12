package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.solver.ObjectiveCut;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Instance;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.InconclusiveSearchException;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverConfig;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Standalone (non-JUnit, not run by surefire) harness that prints an optimization solve's anytime
 * trajectory: one line per improving solution with elapsed seconds, objective and cumulative nodes,
 * then node and backtrack counts bucketed by variable-name prefix and a node count per assignment
 * depth. Usage: {@code main(instancePath, seconds, seed)}.
 */
public final class AnytimeTrajectoryBenchmark {
    private AnytimeTrajectoryBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path instancePath = Path.of(args[0]);
        long seconds = Long.parseLong(args[1]);
        long seed = Long.parseLong(args[2]);

        boolean satisfactionOnly = args.length > 3 && args[3].equals("sat");
        Xcsp3Instance instance = Xcsp3Parser.parse(instancePath);
        if (instance.objective() == null && !satisfactionOnly) {
            throw new IllegalArgumentException(instancePath + " has no objective");
        }

        Map<String, LongAdder> nodesByPrefix = new ConcurrentHashMap<>();
        Map<String, LongAdder> backtracksByPrefix = new ConcurrentHashMap<>();
        Map<Integer, LongAdder> nodesByDepth = new ConcurrentHashMap<>();
        SolverListener listener = new SolverListener() {
            @Override
            public void onNodeExplored(Variable<?> variable, Object value, Assignment assignment) {
                nodesByPrefix.computeIfAbsent(prefix(variable), k -> new LongAdder()).increment();
                nodesByDepth.computeIfAbsent(assignment.getValues().size(), k -> new LongAdder()).increment();
            }

            @Override
            public void onBacktrack(Variable<?> variable, Assignment assignment) {
                backtracksByPrefix.computeIfAbsent(prefix(variable), k -> new LongAdder()).increment();
            }
        };

        Cancellation cancellation = new Cancellation();
        Statistics stats = new Statistics();
        SolverConfig config = SolverConfig.builder()
                .cancellation(cancellation)
                .statistics(stats)
                .listener(listener)
                .restartRandomization(RestartRandomization.seeded(seed))
                .build();

        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(cancellation::cancel, seconds, TimeUnit.SECONDS);
        long start = System.nanoTime();
        int[] improving = new int[1];
        try {
            if (satisfactionOnly) {
                try {
                    ConstraintSatisfactionProblem problem = instance.csp();
                    if (args.length > 4) {
                        problem = new ObjectiveCut().enforce(problem, (LinearObjective) instance.objective(),
                                Long.parseLong(args[4]) + 1);
                    }
                    System.out.println(Solver.Factory.INSTANCE.createSolver(problem, config).getSolution()
                            .map(solution -> "SAT").orElse("UNSAT proven"));
                } catch (InconclusiveSearchException e) {
                    System.out.println("INCONCLUSIVE (" + e.getClass().getSimpleName() + ")");
                }
            } else Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective(), config)
                    .getSolutions()
                    .forEach(solution -> {
                        improving[0]++;
                        System.out.printf("o %d  t=%.2fs  nodes=%d%n",
                                Math.round(instance.objective().applyAsDouble(solution)),
                                (System.nanoTime() - start) / 1e9, stats.getNodesExplored());
                    });
        } finally {
            timer.shutdownNow();
        }
        System.out.printf("%s after %.2fs, %d improving solutions%n",
                cancellation.isCancelled() ? "CUT OFF" : "DRAINED (optimality proven)",
                (System.nanoTime() - start) / 1e9, improving[0]);

        System.out.println("\nnodes / backtracks by variable-name prefix:");
        new TreeMap<>(nodesByPrefix).forEach((prefix, nodes) -> System.out.printf("  %-24s %12d %12d%n",
                prefix, nodes.sum(), backtracksByPrefix.getOrDefault(prefix, new LongAdder()).sum()));
        System.out.println("\nnodes by assignment depth:");
        new TreeMap<>(nodesByDepth).forEach((depth, nodes) -> System.out.printf("  %5d %12d%n", depth, nodes.sum()));
        System.out.println("\n" + stats);
    }

    private static String prefix(Variable<?> variable) {
        String name = variable.getName();
        int bracket = name.indexOf('[');
        return bracket < 0 ? name : name.substring(0, bracket);
    }
}
