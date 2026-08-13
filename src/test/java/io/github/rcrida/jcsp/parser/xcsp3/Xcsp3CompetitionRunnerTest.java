package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Xcsp3CompetitionRunnerTest {

    @TempDir
    Path tempDir;

    private static final Variable.Factory F = Variable.Factory.INSTANCE;

    private static PrintStream printStreamInto(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    // ---- main() / run() -- the real CLI entry points ----------------------------------------------------------

    @Test void main_parsesFileAndSolvesWithinBudget() throws IOException {
        Path instanceFile = tempDir.resolve("instance.xml");
        Files.writeString(instanceFile, """
                <instance format="XCSP3" type="CSP">
                <variables><var id="x"> 0..2 </var></variables>
                <constraints><intension> ge(x,0) </intension></constraints>
                </instance>
                """);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(printStreamInto(buffer));
        try {
            Xcsp3CompetitionRunner.main(new String[]{instanceFile.toString(), "60"});
        } finally {
            System.setOut(new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        }
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("s SATISFIABLE").contains("v x=");
    }

    // ---- satisfaction chain -------------------------------------------------------------------------------------

    @Test void satisfaction_solvableProblem_reportsSatisfiableWithSolutionLine() {
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, null, false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE, printStreamInto(buffer));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("s SATISFIABLE").contains("v x=");
    }

    @Test void satisfaction_infeasibleProblem_reportsUnsatisfiable() {
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, 1)
                .notEqualsConstraint(x, 2)
                .build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, null, false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE, printStreamInto(buffer));

        assertThat(buffer.toString(StandardCharsets.UTF_8).strip()).isEqualTo("s UNSATISFIABLE");
    }

    @Test void satisfaction_cancelledBeforeSearchCompletes_reportsUnknown() {
        // A pre-cancelled token is caught silently during the chain's one-time preprocessing pass
        // (indistinguishable there from genuine UNSAT -- see solveSatisfaction's own Javadoc), so
        // this needs a listener that cancels only once real backtracking search has started, on a
        // problem with enough variables that the very first search node can't already be a complete
        // solution -- otherwise the search would finish before ever re-checking cancellation.
        List<Variable<Integer>> vars = List.of(F.create("a"), F.create("b"), F.create("c"), F.create("d"));
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> v : vars) {
            builder = builder.variableDomain(v, IntRangeDomain.of(1, 4));
        }
        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                builder = builder.notEqualsConstraint(vars.get(i), vars.get(j));
            }
        }
        Xcsp3Instance instance = new Xcsp3Instance(builder.build(), null, false);
        Cancellation cancellation = new Cancellation();
        SolverListener cancelOnFirstNode = new SolverListener() {
            @Override
            public void onNodeExplored(Variable<?> variable, Object value, Assignment assignment) {
                cancellation.cancel();
            }
        };

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, cancellation, cancelOnFirstNode, printStreamInto(buffer));

        assertThat(buffer.toString(StandardCharsets.UTF_8).strip()).isEqualTo("s UNKNOWN");
    }

    // ---- optimization chain -------------------------------------------------------------------------------------

    @Test void optimization_provenOptimal_reportsOptimumFoundWithBoundAndSolutionLine() {
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, objective, false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE, printStreamInto(buffer));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("o 1").contains("s OPTIMUM FOUND").contains("v x=1");
    }

    @Test void optimization_infeasibleProblem_reportsUnsatisfiable() {
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, 1)
                .notEqualsConstraint(x, 2)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, objective, false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE, printStreamInto(buffer));

        assertThat(buffer.toString(StandardCharsets.UTF_8).strip()).isEqualTo("s UNSATISFIABLE");
    }

    @Test void optimization_cancelledBeforeAnySolutionFound_reportsUnknown() {
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, objective, false);
        Cancellation cancellation = new Cancellation();
        cancellation.cancel();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, cancellation, SolverListener.NONE, printStreamInto(buffer));

        assertThat(buffer.toString(StandardCharsets.UTF_8).strip()).isEqualTo("s UNKNOWN");
    }

    @Test void optimization_cancelledAfterFirstIncumbent_reportsSatisfiableNotProvenOptimal() {
        // A listener that cancels the moment the first improving solution is found -- a deterministic
        // stand-in for "time ran out mid-search" that doesn't rely on real wall-clock timing.
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, objective, false);
        Cancellation cancellation = new Cancellation();
        SolverListener cancelOnFirstIncumbent = new SolverListener() {
            @Override
            public void onIncumbentImproved(Assignment solution, double cost) {
                cancellation.cancel();
            }
        };

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, cancellation, cancelOnFirstIncumbent, printStreamInto(buffer));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("s SATISFIABLE").doesNotContain("OPTIMUM");
    }

    @Test void optimization_maximizeObjective_reportsBoundInOriginalXcsp3Sense() {
        // objective() is minimize-oriented internally (coefficient negated); maximize=true means the
        // "o" line must negate it back to XCSP3's own sense before printing.
        Variable<Integer> x = F.create("x");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, -1.0).build();
        Xcsp3Instance instance = new Xcsp3Instance(csp, objective, true);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Xcsp3CompetitionRunner.solve(instance, Cancellation.NEVER, SolverListener.NONE, printStreamInto(buffer));

        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("o 3").contains("v x=3");
    }
}
