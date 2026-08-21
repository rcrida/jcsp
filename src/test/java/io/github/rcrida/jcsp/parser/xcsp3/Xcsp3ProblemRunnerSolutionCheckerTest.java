package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import org.junit.jupiter.api.Test;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Independently re-verifies {@link Xcsp3ProblemRunner}'s own output against the original XCSP3
 * instance file via {@code org.xcsp.parser.callbacks.SolutionChecker} -- the same checker real
 * XCSP3 Competition entries are validated with. Unlike every other test in this package (which
 * checks a solution against jcsp's own understanding of the constraints it built), this drives a
 * completely independent constraint re-derivation from the raw XML, so it can catch a class of bug
 * neither this project's own test suite nor the solver itself can: a constraint or propagator that
 * jcsp and {@code xcsp3-tools} both mis-parse the <em>same</em> way would still pass every other
 * test here, but {@code SolutionChecker}'s own {@code XCallbacks2} implementation is independent
 * code re-evaluating the constraint from scratch against the printed solution values.
 * <p>
 * {@code SolutionChecker}'s competition-mode constructor parses exactly the {@code s}/{@code v}
 * transcript {@link Xcsp3ProblemRunner} already prints, so no adapter is needed beyond capturing
 * that output into a stream. It can only validate a <em>claimed</em> solution, though -- it has no
 * way to confirm a {@code s UNSATISFIABLE} claim, since there's no solution to check in that case.
 */
class Xcsp3ProblemRunnerSolutionCheckerTest {

    private static Path resource(String name) throws URISyntaxException {
        return Paths.get(Xcsp3ProblemRunnerSolutionCheckerTest.class.getResource("/xcsp3/" + name).toURI());
    }

    private static SolutionChecker check(Path instanceFile, String transcript) throws Exception {
        return new SolutionChecker(true, instanceFile.toString(),
                new ByteArrayInputStream(transcript.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void sendMoreMoney_solutionPassesIndependentChecker() throws Exception {
        Path instanceFile = resource("send-more-money.xml");
        Xcsp3Instance instance = Xcsp3Parser.parse(instanceFile);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3ProblemRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String transcript = buffer.toString(StandardCharsets.UTF_8);
        assertThat(transcript).contains("s SATISFIABLE");

        SolutionChecker checker = check(instanceFile, transcript);
        assertThat(checker.violatedCtrs).isEmpty();
        assertThat(checker.invalidObjs).isEmpty();
    }

    @Test void smallKnapsack_solutionPassesIndependentChecker() throws Exception {
        Path instanceFile = resource("small-knapsack.xml");
        Xcsp3Instance instance = Xcsp3Parser.parse(instanceFile);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3ProblemRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String transcript = buffer.toString(StandardCharsets.UTF_8);
        assertThat(transcript).contains("s OPTIMUM FOUND");

        SolutionChecker checker = check(instanceFile, transcript);
        assertThat(checker.violatedCtrs).isEmpty();
        assertThat(checker.invalidObjs).isEmpty();
    }

    @Test void deliberatelyWrongSolution_isCaughtByChecker() throws Exception {
        // Negative control: confirms the checker actually detects a real violation rather than
        // trivially passing everything (e.g. from a malformed transcript it silently ignores).
        Path instanceFile = resource("small-knapsack.xml");
        String wrongTranscript = """
                s SATISFIABLE
                v <instantiation><list> x1 x2 x3 </list><values> 0 0 0 </values></instantiation>
                """;

        SolutionChecker checker = check(instanceFile, wrongTranscript);
        assertThat(checker.violatedCtrs).isNotEmpty();
    }

    @Test void main_endToEnd_solutionPassesIndependentChecker(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        // Exercises the real CLI entry point (main -> run -> solve -> printSolution), not just the
        // solve() overload the other tests call directly, so the declaredVariableNames plumbing is
        // checked all the way from Xcsp3Parser.parse through to stdout.
        Path instanceFile = resource("send-more-money.xml");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            Xcsp3ProblemRunner.main(new String[]{instanceFile.toString(), "60"});
        } finally {
            System.setOut(original);
        }
        String transcript = buffer.toString(StandardCharsets.UTF_8);
        assertThat(transcript).contains("s SATISFIABLE");

        SolutionChecker checker = check(instanceFile, transcript);
        assertThat(checker.violatedCtrs).isEmpty();
        assertThat(checker.invalidObjs).isEmpty();
    }
}
