package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.solver.BoundSolver;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverCancelledException;
import io.github.rcrida.jcsp.solver.SolverConfig;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimal XCSP3-competition-style CLI for a single instance: parses it, solves it under a
 * wall-clock time budget enforced via {@link Cancellation}, and prints {@code s}/{@code o}/{@code
 * v} status lines in the convention used by the XCSP3 Competition and related SAT/CP competitions.
 * A real competition invokes exactly this shape once per instance, in a fresh process each time —
 * {@code Xcsp3CompetitionRunner} (test sources, not part of the published library) drives a whole
 * batch of instances by {@code exec}-ing this class's {@link #main} once per file, mirroring that
 * same per-instance process isolation rather than reusing one JVM across the batch.
 * <p>
 * {@link #main}'s optional third argument pins a {@link RestartRandomization} seed rather than
 * leaving {@code SolverConfig#getRestartRandomization()} at its own random-per-call default. A real
 * competition run has no earlier run to compare against, so this argument defaults to absent
 * (unset), preserving the library's normal restart-diversification behaviour for genuine end
 * users; {@code Xcsp3CompetitionRunner} passes a fixed literal seed when it execs this class
 * specifically because its own job (unlike a one-off competition run) is comparing solved/unknown
 * counts across separate runs of possibly-different code, the same reproducibility need {@code
 * NogoodPropagationBenchmark}/{@code CsplibBenchmarks} already pin a fixed seed for. A fixed seed
 * only removes the dom/wdeg tie-break axis of run-to-run variance, not all of it: {@code AC3}'s own
 * arc-processing order is independently salted once per JVM process and untouched by this either
 * way, so residual variance across separate {@code java} launches (what {@code
 * Xcsp3CompetitionRunner} always uses, one subprocess per instance) is expected and accepted, the
 * same documented limitation {@code NogoodPropagationBenchmark#compareRestartRandomization} exists
 * to characterize.
 */
public final class Xcsp3ProblemRunner {

    private Xcsp3ProblemRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path instanceFile = Path.of(args[0]);
        long timeLimitSeconds = Long.parseLong(args[1]);
        RestartRandomization restartRandomization = args.length >= 3
                ? RestartRandomization.seeded(Long.parseLong(args[2]))
                : null;
        run(Xcsp3Parser.parse(instanceFile), timeLimitSeconds, restartRandomization, System.out);
    }

    /**
     * Enforces {@code timeLimitSeconds} by cancelling a fresh {@link Cancellation} from a
     * background timer once it elapses, then delegates to {@link #solve}. {@code null}
     * {@code restartRandomization} leaves {@link SolverConfig}'s own random-per-call default in
     * place -- see this class's own Javadoc for when a caller should pass a fixed seed instead.
     */
    static void run(Xcsp3Instance instance, long timeLimitSeconds,
                     @Nullable RestartRandomization restartRandomization, PrintStream out) {
        Cancellation cancellation = new Cancellation();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(cancellation::cancel, timeLimitSeconds, TimeUnit.SECONDS);
        try {
            solve(instance, cancellation, SolverListener.NONE, restartRandomization, out);
        } finally {
            timer.shutdownNow();
        }
    }

    /**
     * {@code c search-space-before:} is {@link ConstraintSatisfactionProblem#getSearchSpace()} on
     * the original, undecomposed {@code instance.csp()} -- the declared size, unaffected by however
     * much search progresses. {@code c search-space-after:} is {@code stats}'s own {@link
     * Statistics#getCurrentSearchSpace()}, printed only when present -- i.e. only when a
     * {@link Cancellation}/timeout actually stopped search before it completed, in which case it's
     * the live, propagation-narrowed search space at the exact point that happened. Omitted
     * entirely for a completed solve (SAT, UNSAT, or OPTIMUM FOUND): {@code
     * search-space-before}'s already-printed declared value is the whole answer there, with no
     * narrower "current" state to report separately. {@code stats} is likewise printed as its own
     * {@code c} line at the end. {@code c}-prefixed lines are the standard SAT/CP competition
     * convention for extra, parser-ignorable information alongside the {@code s}/{@code o}/{@code
     * v} status lines, so this doesn't change this class's own output contract. {@code
     * Xcsp3CompetitionRunner} (test sources) parses these lines back out of a captured
     * child-process transcript rather than re-parsing the instance itself, tolerating {@code
     * search-space-after}'s absence.
     */
    static void solve(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener, PrintStream out) {
        solve(instance, cancellation, listener, null, out);
    }

    static void solve(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener,
                       @Nullable RestartRandomization restartRandomization, PrintStream out) {
        out.println("c search-space-before: " + instance.csp().getSearchSpace());
        Statistics stats = new Statistics();
        if (instance.objective() == null) {
            solveSatisfaction(instance, cancellation, listener, restartRandomization, stats, out);
        } else {
            solveOptimization(instance, cancellation, listener, restartRandomization, stats, out);
        }
        out.println("c stats: " + stats);
        stats.getRootSearchSpace().ifPresent(space -> out.println("c search-space-at-root: " + space));
        stats.getRemainingSearchSpace().ifPresent(space -> out.println("c search-space-remaining: " + space));
        stats.getCurrentSearchSpace().ifPresent(searchSpace -> out.println("c search-space-after: " + searchSpace));
    }

    private static SolverConfig configFor(
            Cancellation cancellation, SolverListener listener, @Nullable RestartRandomization restartRandomization, Statistics stats) {
        var builder = SolverConfig.builder().cancellation(cancellation).listener(listener).statistics(stats);
        if (restartRandomization != null) {
            builder.restartRandomization(restartRandomization);
        }
        return builder.build();
    }

    /**
     * {@link BoundSolver#getSolution()} throws {@link SolverCancelledException} only when {@code
     * cancellation} fires during the terminal search itself (see {@link BoundSolver}'s own
     * Javadoc) -- a {@code cancellation} that's already cancelled before this call, or that fires
     * during the chain's one-time preprocessing pass, is caught upstream and surfaces as a plain
     * {@link Optional#empty()}, indistinguishable here from genuine {@code UNSATISFIABLE}. Only the
     * thrown case is reported as {@code UNKNOWN}.
     */
    private static void solveSatisfaction(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener,
                                           @Nullable RestartRandomization restartRandomization, Statistics stats, PrintStream out) {
        SolverConfig config = configFor(cancellation, listener, restartRandomization, stats);
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
     * {@link BoundSolver#getSolutions()}'s stream simply truncates once {@code cancellation} fires,
     * so whether it drained to completion (checked only after full consumption, since a stream can't
     * be asked mid-iteration whether more elements remain) is the proof that the last improving
     * solution is a genuine, search-complete optimum ({@code OPTIMUM FOUND}) rather than just the
     * best incumbent found before time ran out ({@code SATISFIABLE}). Consumed via {@link
     * Stream#forEach} rather than {@code collect} (or an explicit {@link java.util.Iterator}) so
     * each improving solution's {@code o} line prints as soon as {@link BranchAndBoundSolver} finds
     * it, matching the real XCSP3/SAT competition convention of progressive {@code o} lines during
     * search. {@code forEach} matters specifically here, not just as a style choice: {@link
     * BranchAndBoundSolver#search}'s recursive, per-search-tree-level {@code flatMap} chain is
     * genuinely lazy end to end, but converting a deeply-nested {@code flatMap} pipeline like that
     * into a pull-based {@link java.util.Iterator} (what {@code Stream#iterator} does internally) is
     * a real, confirmed JDK limitation -- it buffers a large prefix of results (in this codebase's
     * case, every improving solution the whole search ever finds) before yielding even the first one
     * to the caller, silently defeating the "print each as found" goal despite the underlying
     * pipeline itself never being restructured into anything eager. {@code forEach} is a push-based
     * terminal operation that matches the pipeline's own {@code Sink}-based evaluation model, so it
     * doesn't go through that buffering translation at all -- confirmed via a minimal JDK-only
     * reproduction of {@code search}'s exact recursive shape (12 nested {@code flatMap} levels):
     * {@code iterator()} buffered ~16,800 results for ~200ms before yielding the first one, while
     * {@code forEach} delivered each within a fraction of a millisecond of its own production.
     */
    private static void solveOptimization(Xcsp3Instance instance, Cancellation cancellation, SolverListener listener,
                                           @Nullable RestartRandomization restartRandomization, Statistics stats, PrintStream out) {
        SolverConfig config = configFor(cancellation, listener, restartRandomization, stats);
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective(), config);
        Assignment[] best = new Assignment[1];
        solver.getSolutions().forEach(solution -> {
            best[0] = solution;
            double value = instance.objective().applyAsDouble(solution);
            out.println("o " + Math.round(instance.maximize() ? -value : value));
        });
        boolean provenOptimal = !cancellation.isCancelled();
        if (best[0] == null) {
            out.println(provenOptimal ? "s UNSATISFIABLE" : "s UNKNOWN");
            return;
        }
        out.println(provenOptimal ? "s OPTIMUM FOUND" : "s SATISFIABLE");
        printSolution(instance, best[0], out);
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
