package io.github.rcrida.jcsp.parser.xcsp3;

import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Drives {@link Xcsp3ProblemRunner} over a whole batch of XCSP3 instances, one fresh {@code java}
 * process per instance via {@link ProcessBuilder} — the same per-instance process isolation a
 * real XCSP3 Competition run uses (see {@link Xcsp3ProblemRunner}'s own Javadoc), rather than
 * calling {@link Xcsp3ProblemRunner#main} in-process across the batch, which would let JIT
 * warm-up, GC state, and any accidental static leak carry over between instances the way a real
 * competition run never does.
 * <p>
 * Not a {@code *Test} class — not run by Surefire, same as {@code
 * io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark}/{@code
 * io.github.rcrida.jcsp.solver.examples.csplib.CsplibBenchmarks}. Run via {@code mvn
 * test-compile} then {@code java -cp target/classes:target/test-classes:$(mvn -q
 * dependency:build-classpath -Dmdep.outputFile=/dev/stdout)
 * io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CompetitionRunner [instanceDirectory] [timeLimitSeconds]}.
 * With no arguments, runs the small real-instance corpus bundled at {@code
 * src/test/resources/xcsp3/competition/} — sourced from {@code xcsp3team/XCSP3-Java-Tools}'s own
 * parser-conformance test fixtures (real competition-distributed {@code .xml.lzma} files, not
 * fabricated ones; MIT-licensed, matching this project's own {@code xcsp3-tools} dependency) —
 * under a {@value #DEFAULT_TIME_LIMIT_SECONDS}-second-per-instance budget. Every {@code s
 * SATISFIABLE}/{@code s OPTIMUM FOUND} result is also independently re-verified against the
 * original instance file via {@code org.xcsp.parser.callbacks.SolutionChecker} (see {@link
 * #crossCheck}) — a mismatch there indicates a real soundness bug (jcsp accepted or produced an
 * assignment that violates the instance's own constraints), not just a performance regression.
 * {@code SolutionChecker} can only validate a claimed solution, so a {@code s UNSATISFIABLE}
 * result is reported {@code "-"} (skipped) in the Check column rather than confirmed or refuted.
 * Each row's Model column (see {@link #modelInfo}) also reports variable/constraint counts both as
 * the source XCSP3 file itself declares them and as the parsed {@code
 * ConstraintSatisfactionProblem} ends up with -- the gap between the two is every synthetic
 * variable/constraint {@link Xcsp3CallbackHandler} adds internally (reification indicators, {@code
 * addIff}'s fresh indicators, etc.).
 */
public final class Xcsp3CompetitionRunner {

    private static final long DEFAULT_TIME_LIMIT_SECONDS = 60;

    /**
     * Extra time beyond {@code timeLimitSeconds} allowed before a child process is forcibly
     * killed. {@link Xcsp3ProblemRunner} enforces its own budget internally via {@link
     * io.github.rcrida.jcsp.solver.Cancellation}, so a well-behaved child should exit at or just
     * after {@code timeLimitSeconds} regardless — this margin only guards against a genuinely
     * wedged JVM (e.g. a bug that leaves search unresponsive to cancellation), which this harness
     * should report as a hang rather than block the whole batch on forever.
     */
    private static final long PROCESS_KILL_MARGIN_SECONDS = 10;

    private Xcsp3CompetitionRunner() {
    }

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        Path directory = args.length >= 1 ? Path.of(args[0]) : bundledInstanceDirectory();
        long timeLimitSeconds = args.length >= 2 ? Long.parseLong(args[1]) : DEFAULT_TIME_LIMIT_SECONDS;
        run(directory, timeLimitSeconds, System.out);
    }

    private static Path bundledInstanceDirectory() throws URISyntaxException {
        URL resource = Xcsp3CompetitionRunner.class.getResource("/xcsp3/competition");
        if (resource == null) {
            throw new IllegalStateException(
                    "Bundled instance directory /xcsp3/competition is missing from the classpath "
                            + "(expected src/test/resources/xcsp3/competition on target/test-classes)");
        }
        return Paths.get(resource.toURI());
    }

    static void run(Path directory, long timeLimitSeconds, PrintStream out) throws IOException, InterruptedException {
        List<Path> instances;
        try (Stream<Path> files = Files.list(directory)) {
            instances = files.filter(Xcsp3CompetitionRunner::isXcsp3Instance).sorted().toList();
        }
        out.printf("%-40s %-10s %-20s %-12s %-24s %s%n", "Instance", "Time(s)", "Result", "Check", "Model (xcsp3->csp)", "Statistics");
        out.println("-".repeat(90));
        int solved = 0, unknown = 0, failed = 0, checkMismatches = 0;
        for (Path instance : instances) {
            Result result = runOne(instance, timeLimitSeconds);
            out.printf("%-40s %-10.2f %-20s %-12s %-24s %s%n", instanceName(instance), result.elapsedSeconds(), result.summary(), result.crossCheck(), result.model(), result.statsLine());
            switch (category(result.summary())) {
                case SOLVED -> solved++;
                case UNKNOWN -> unknown++;
                case FAILED -> failed++;
            }
            if (result.crossCheck().startsWith("MISMATCH") || result.crossCheck().startsWith("CHECK ERROR")) {
                checkMismatches++;
            }
        }
        out.println();
        out.printf("%d solved, %d unknown/timeout, %d failed (of %d)%n", solved, unknown, failed, instances.size());
        if (checkMismatches > 0) {
            out.printf("%d SolutionChecker cross-check MISMATCH(ES) -- see Check column above%n", checkMismatches);
        }
    }

    private static boolean isXcsp3Instance(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".xml") || name.endsWith(".xml.lzma");
    }

    private record Result(double elapsedSeconds, String summary, String statsLine, String crossCheck, String model) {}

    private enum Category {SOLVED, UNKNOWN, FAILED}

    private static Category category(String summary) {
        if (summary.equals("s UNKNOWN")) return Category.UNKNOWN;
        if (summary.startsWith("s SATISFIABLE") || summary.startsWith("s UNSATISFIABLE") || summary.startsWith("s OPTIMUM")) {
            return Category.SOLVED;
        }
        return Category.FAILED;
    }

    /**
     * Spawns {@code java -cp <this JVM's own classpath> Xcsp3ProblemRunner <instance>
     * <timeLimitSeconds>} and captures its output. {@link Process#waitFor(long, TimeUnit)} is
     * called (and enforced with {@link Process#destroyForcibly()} on timeout) <em>before</em>
     * draining the child's output, not after -- reading first would let a genuinely wedged child
     * (unresponsive to its own internal {@link io.github.rcrida.jcsp.solver.Cancellation} budget)
     * block this call forever, since {@link java.io.InputStream#readAllBytes()} has no timeout of
     * its own. Waiting first is safe from the opposite risk (the child blocking on a full pipe
     * while this method isn't draining it) only because {@code -Dorg.slf4j.simpleLogger.defaultLogLevel=error}
     * is passed to the child, keeping its output to {@link Xcsp3ProblemRunner}'s own handful of
     * status/solution lines -- without it, an optimization instance whose solver logs every
     * improving incumbent at INFO (e.g. {@code BranchAndBoundSolver}) can produce well over 100KB
     * of log output, overflow the OS pipe buffer, and block the child's own search thread on that
     * write before it ever reaches its {@link io.github.rcrida.jcsp.solver.Cancellation} check --
     * a genuine deadlock this harness previously (and wrongly) reported as the child having
     * "HUNG", confirmed by reproducing it directly via an isolated single-instance corpus run
     * plus a minimal {@link ProcessBuilder} repro comparing default vs. suppressed child logging.
     */
    private static Result runOne(Path instance, long timeLimitSeconds) throws IOException, InterruptedException {
        String model = modelInfo(instance);

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", classpath, "-Dorg.slf4j.simpleLogger.defaultLogLevel=error",
                Xcsp3ProblemRunner.class.getName(), instance.toString(), String.valueOf(timeLimitSeconds));
        builder.redirectErrorStream(true);

        long startNanos = System.nanoTime();
        Process process = builder.start();
        boolean finished = process.waitFor(timeLimitSeconds + PROCESS_KILL_MARGIN_SECONDS, TimeUnit.SECONDS);
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        if (!finished) {
            process.destroyForcibly();
            return new Result(elapsedSeconds, "HUNG (killed after " + elapsedSeconds + "s)", "(no stats -- process killed)", "-", model);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String summary = firstResultLine(output);
        return new Result(elapsedSeconds, summary, statsLine(output), crossCheck(instance, summary, output), model);
    }

    /**
     * Parses {@code instance} directly in this process -- separately from the {@link
     * Xcsp3ProblemRunner} child process spawned for the timed solve below, and not counted against
     * {@code timeLimitSeconds} -- purely to report model size: how many variables/constraints the
     * source XCSP3 file itself declares ({@link Xcsp3Instance#declaredVariableNames()}/{@link
     * Xcsp3Instance#declaredConstraintCount()}) versus how many the parsed {@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem} actually ends up with ({@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem#getVariableDomains()}/{@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem#getConstraints()}) -- the gap between the
     * two is every synthetic variable/constraint {@link Xcsp3CallbackHandler} adds internally
     * (reification indicators, {@code addIff}'s fresh indicators, etc.). A parse failure here (e.g.
     * {@link UnsupportedXcsp3ConstraintException} for a construct this parser doesn't map) is
     * reported inline rather than propagated -- the child process below independently re-parses the
     * same file and reports its own failure via {@link #firstResultLine}, so this method failing
     * shouldn't abort the whole batch.
     */
    private static String modelInfo(Path instance) {
        try {
            Xcsp3Instance parsed = Xcsp3Parser.parse(instance);
            return "%dv/%dc -> %dv/%dc".formatted(
                    parsed.declaredVariableNames().size(), parsed.declaredConstraintCount(),
                    parsed.csp().getVariableDomains().size(), parsed.csp().getConstraints().size());
        } catch (Exception e) {
            return "PARSE ERROR";
        }
    }

    /**
     * Independently re-verifies a {@code s SATISFIABLE}/{@code s OPTIMUM FOUND} result against the
     * original instance file via {@code org.xcsp.parser.callbacks.SolutionChecker} -- the same
     * checker real XCSP3 Competition entries are validated with (see {@link
     * Xcsp3ProblemRunnerSolutionCheckerTest} for the equivalent single-instance test coverage of
     * this exact mechanism). Skipped ({@code "-"}) for every other outcome: {@code
     * SolutionChecker} can only validate a claimed solution, so there's nothing to check against a
     * {@code s UNSATISFIABLE}/{@code s UNKNOWN}/{@code HUNG} result. {@code SolutionChecker}'s own
     * competition-mode constructor prints "OK"/"INVALID Solution!" directly to {@link System#out}
     * regardless of caller -- temporarily redirected here so it doesn't interleave with this
     * class's own table output; {@link SolutionChecker#violatedCtrs}/{@link
     * SolutionChecker#invalidObjs} (both public fields) are read directly afterward instead of
     * parsing that captured text.
     */
    private static String crossCheck(Path instance, String summary, String output) {
        if (!summary.startsWith("s SATISFIABLE") && !summary.startsWith("s OPTIMUM")) {
            return "-";
        }
        PrintStream realOut = System.out;
        try {
            System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream()));
            SolutionChecker checker = new SolutionChecker(true, instance.toString(),
                    new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
            int errors = checker.violatedCtrs.size() + checker.invalidObjs.size();
            return errors == 0 ? "OK" : "MISMATCH (" + errors + " errors)";
        } catch (Exception e) {
            return "CHECK ERROR: " + e;
        } finally {
            System.setOut(realOut);
        }
    }

    private static String firstResultLine(String output) {
        return output.lines()
                .filter(line -> line.startsWith("s ") || line.contains("Exception"))
                .findFirst()
                .orElse("NO OUTPUT");
    }

    /**
     * {@link Xcsp3ProblemRunner#solve} always prints exactly one {@code c stats: ...} line as its
     * last line of output, regardless of outcome (SAT, UNSAT, UNKNOWN, or OPTIMUM FOUND) -- the one
     * case that line is genuinely absent is a raw, uncaught exception (e.g. {@code
     * UnsupportedXcsp3ConstraintException} from the parser) thrown before {@link
     * Xcsp3ProblemRunner#solve} is ever reached, which {@link #firstResultLine} already reports via
     * its own {@code Exception} match instead.
     */
    private static String statsLine(String output) {
        return output.lines()
                .filter(line -> line.startsWith("c stats: "))
                .findFirst()
                .orElse("(no stats)");
    }

    private static String instanceName(Path instance) {
        String name = instance.getFileName().toString();
        if (name.endsWith(".xml.lzma")) return name.substring(0, name.length() - ".xml.lzma".length());
        if (name.endsWith(".xml")) return name.substring(0, name.length() - ".xml".length());
        return name;
    }
}
