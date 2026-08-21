package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.solver.BoundSolver;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverCancelledException;
import io.github.rcrida.jcsp.solver.SolverConfig;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Minimal XCSP3-competition-style CLI for a single instance: parses it, solves it under a
 * wall-clock time budget enforced via {@link Cancellation}, and prints {@code s}/{@code o}/{@code
 * v} status lines in the convention used by the XCSP3 Competition and related SAT/CP competitions.
 * A real competition invokes exactly this shape once per instance, in a fresh process each time —
 * {@code Xcsp3CompetitionRunner} (test sources, not part of the published library) drives a whole
 * batch of instances by {@code exec}-ing this class's {@link #main} once per file, mirroring that
 * same per-instance process isolation rather than reusing one JVM across the batch.
 */
public final class Xcsp3ProblemRunner {

    private Xcsp3ProblemRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path instanceFile = Path.of(args[0]);
        long timeLimitSeconds = Long.parseLong(args[1]);
        run(Xcsp3Parser.parse(instanceFile), timeLimitSeconds, System.out);
    }

    /**
     * Enforces {@code timeLimitSeconds} by cancelling a fresh {@link Cancellation} from a
     * background timer once it elapses, then delegates to {@link #solve}.
     */
    static void run(Xcsp3Instance instance, long timeLimitSeconds, PrintStream out) {
        Cancellation cancellation = new Cancellation();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(cancellation::cancel, timeLimitSeconds, TimeUnit.SECONDS);
        try {
            solve(instance, cancellation, SolverListener.NONE, out);
        } finally {
            timer.shutdownNow();
        }
    }

    /**
     * {@code stats} is printed as a {@code c} (comment) line after the result, regardless of
     * outcome (SAT, UNSAT, UNKNOWN, or OPTIMUM FOUND) -- {@code c}-prefixed lines are the standard
     * SAT/CP competition convention for extra, parser-ignorable information alongside the {@code
     * s}/{@code o}/{@code v} status lines, so this doesn't change this class's own output contract.
     */
    static void solve(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener, PrintStream out) {
        Statistics stats = new Statistics();
        if (instance.objective() == null) {
            solveSatisfaction(instance, cancellation, listener, stats, out);
        } else {
            solveOptimization(instance, cancellation, listener, stats, out);
        }
        out.println("c stats: " + stats);
    }

    /**
     * {@link BoundSolver#getSolution()} throws {@link SolverCancelledException} only when {@code
     * cancellation} fires during the terminal search itself (see {@link BoundSolver}'s own
     * Javadoc) -- a {@code cancellation} that's already cancelled before this call, or that fires
     * during the chain's one-time preprocessing pass, is caught upstream and surfaces as a plain
     * {@link Optional#empty()}, indistinguishable here from genuine {@code UNSATISFIABLE}. Only the
     * thrown case is reported as {@code UNKNOWN}.
     */
    private static void solveSatisfaction(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener, Statistics stats, PrintStream out) {
        SolverConfig config = SolverConfig.builder().cancellation(cancellation).listener(listener).statistics(stats).build();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(instance.csp(), config);
        try {
            Optional<Assignment> solution = solver.getSolution();
            if (solution.isEmpty()) {
                out.println("s UNSATISFIABLE");
                return;
            }
            out.println("s SATISFIABLE");
            printSolution(instance, solution.get(), out);
        } catch (SolverCancelledException e) {
            out.println("s UNKNOWN");
        }
    }

    /**
     * Unlike the satisfaction chain, {@link BranchAndBoundSolver} never throws on cancellation --
     * {@link BoundSolver#getSolutions()} is consumed directly here so that whether the stream
     * drained to completion without {@code cancellation} ever firing can be used as the proof that
     * the last improving solution is a genuine, search-complete optimum ({@code OPTIMUM FOUND})
     * rather than just the best incumbent found before time ran out ({@code SATISFIABLE}).
     */
    private static void solveOptimization(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener, Statistics stats, PrintStream out) {
        SolverConfig config = SolverConfig.builder().cancellation(cancellation).listener(listener).statistics(stats).build();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective(), config);
        List<Assignment> improving = solver.getSolutions().collect(Collectors.toList());
        boolean provenOptimal = !cancellation.isCancelled();
        if (improving.isEmpty()) {
            out.println(provenOptimal ? "s UNSATISFIABLE" : "s UNKNOWN");
            return;
        }
        Assignment best = improving.get(improving.size() - 1);
        double value = instance.objective().applyAsDouble(best);
        out.println("o " + Math.round(instance.maximize() ? -value : value));
        out.println(provenOptimal ? "s OPTIMUM FOUND" : "s SATISFIABLE");
        printSolution(instance, best, out);
    }

    /**
     * Emits the solution as a single {@code v}-prefixed XCSP3 {@code <instantiation>} line -- the
     * format {@code org.xcsp.parser.callbacks.SolutionChecker} (in its {@code -cm}/competition-mode
     * constructor) parses directly back out of a captured {@code s}/{@code v} transcript, letting a
     * caller independently re-verify this class's own output against the original instance file.
     * Restricted to {@link Xcsp3Instance#declaredVariableNames()} -- {@link
     * ConstraintSatisfactionProblem#getVariableDomains()} also carries every synthetic variable
     * {@link Xcsp3CallbackHandler} creates internally (reification indicators, {@code addIff}'s
     * fresh indicators, etc.), which have no counterpart in the original file for the checker to
     * resolve. {@code getVariableDomains().keySet()} isn't ordered by declaration, so this iterates
     * {@link Xcsp3Instance#declaredVariableNames()} itself (declaration order) and looks each
     * variable up by name instead.
     */
    private static void printSolution(Xcsp3Instance instance, Assignment solution, PrintStream out) {
        List<String> names = instance.declaredVariableNames().stream().toList();
        Variable.Factory factory = Variable.Factory.INSTANCE;
        String list = String.join(" ", names);
        String values = names.stream()
                .map(name -> String.valueOf(solution.getValue(factory.<Object>create(name)).orElseThrow()))
                .collect(Collectors.joining(" "));
        out.println("v <instantiation><list> " + list + " </list><values> " + values + " </values></instantiation>");
    }
}
