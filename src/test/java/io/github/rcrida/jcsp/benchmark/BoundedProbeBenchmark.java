package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Instance;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.InconclusiveSearchException;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.solver.ObjectiveCut;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverConfig;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Standalone (non-JUnit, not run by surefire) harness asking "is there a solution costing at most
 * T?" of the satisfaction chain directly, without the incumbent seeder. Usage: {@code
 * main(instancePath, secondsPerProbe, seed, target...)}; each target is cut in with {@link
 * ObjectiveCut#enforce} and answered {@code SAT}, {@code UNSAT proven} or {@code INCONCLUSIVE}.
 */
public final class BoundedProbeBenchmark {
    private BoundedProbeBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path instancePath = Path.of(args[0]);
        long secondsPerProbe = Long.parseLong(args[1]);
        long seed = Long.parseLong(args[2]);

        Xcsp3Instance instance = Xcsp3Parser.parse(instancePath);
        if (!(instance.objective() instanceof LinearObjective objective)) {
            throw new IllegalArgumentException(instancePath + " has no linear objective");
        }
        for (int i = 3; i < args.length; i++) {
            probe(instance.csp(), objective, Long.parseLong(args[i]), secondsPerProbe, seed);
        }
    }

    private static void probe(ConstraintSatisfactionProblem csp, LinearObjective objective, long target,
                              long secondsPerProbe, long seed) throws Exception {
        ConstraintSatisfactionProblem bounded = new ObjectiveCut().enforce(csp, objective, target + 1);
        if (bounded == csp) {
            System.out.printf("<=%d  no exact cut is expressible for this objective%n", target);
            return;
        }
        Cancellation cancellation = new Cancellation();
        Statistics stats = new Statistics();
        SolverConfig config = SolverConfig.builder()
                .cancellation(cancellation)
                .statistics(stats)
                .restartRandomization(RestartRandomization.seeded(seed))
                .build();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(cancellation::cancel, secondsPerProbe, TimeUnit.SECONDS);
        long start = System.nanoTime();
        String verdict;
        try {
            Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(bounded, config).getSolution();
            verdict = solution.isPresent()
                    ? "SAT cost=" + Math.round(objective.applyAsDouble(solution.get()))
                    : "UNSAT proven";
        } catch (InconclusiveSearchException e) {
            verdict = "INCONCLUSIVE (" + e.getClass().getSimpleName() + ")";
        } finally {
            timer.shutdownNow();
        }
        System.out.printf("<=%d  %s  t=%.2fs  nodes=%d  restarts=%d%n", target, verdict,
                (System.nanoTime() - start) / 1e9, stats.getNodesExplored(), stats.getRestarts());
    }
}
