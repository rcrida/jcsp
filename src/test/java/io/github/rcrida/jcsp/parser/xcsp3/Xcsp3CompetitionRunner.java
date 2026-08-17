package io.github.rcrida.jcsp.parser.xcsp3;

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
 * under a {@value #DEFAULT_TIME_LIMIT_SECONDS}-second-per-instance budget.
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
        out.printf("%-40s %-10s %s%n", "Instance", "Time(s)", "Result");
        out.println("-".repeat(90));
        int solved = 0, unknown = 0, failed = 0;
        for (Path instance : instances) {
            Result result = runOne(instance, timeLimitSeconds);
            out.printf("%-40s %-10.2f %s%n", instanceName(instance), result.elapsedSeconds(), result.summary());
            switch (category(result.summary())) {
                case SOLVED -> solved++;
                case UNKNOWN -> unknown++;
                case FAILED -> failed++;
            }
        }
        out.println();
        out.printf("%d solved, %d unknown/timeout, %d failed (of %d)%n", solved, unknown, failed, instances.size());
    }

    private static boolean isXcsp3Instance(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".xml") || name.endsWith(".xml.lzma");
    }

    private record Result(double elapsedSeconds, String summary) {}

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
            return new Result(elapsedSeconds, "HUNG (killed after " + elapsedSeconds + "s)");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(elapsedSeconds, firstResultLine(output));
    }

    private static String firstResultLine(String output) {
        return output.lines()
                .filter(line -> line.startsWith("s ") || line.contains("Exception"))
                .findFirst()
                .orElse("NO OUTPUT");
    }

    private static String instanceName(Path instance) {
        String name = instance.getFileName().toString();
        if (name.endsWith(".xml.lzma")) return name.substring(0, name.length() - ".xml.lzma".length());
        if (name.endsWith(".xml")) return name.substring(0, name.length() - ".xml".length());
        return name;
    }
}
