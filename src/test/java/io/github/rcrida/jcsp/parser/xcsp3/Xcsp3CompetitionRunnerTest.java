package io.github.rcrida.jcsp.parser.xcsp3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Xcsp3CompetitionRunnerTest {

    /**
     * 10,000 Boolean variables in 5,000 independent {@code x[2i] <= x[2i+1]} pairs: trivially
     * satisfiable, and its single {@code v} solution line comes to about 100KB.
     */
    private static String instanceWithALargeSolution() {
        StringBuilder xml = new StringBuilder("""
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="x" size="[10000]"> 0..1 </array>
                  </variables>
                  <constraints>
                    <group>
                      <intension> le(%0,%1) </intension>
                """);
        for (int i = 0; i < 10_000; i += 2) {
            xml.append("      <args> x[%d] x[%d] </args>\n".formatted(i, i + 1));
        }
        return xml.append("    </group>\n  </constraints>\n</instance>\n").toString();
    }

    @Test
    void aSolutionLargerThanThePipeBufferIsReportedNotMistakenForAHang(@TempDir Path directory)
            throws IOException, InterruptedException {
        // The child writes its solution to stdout, and a pipe holds 64KB. Read after the child
        // exits, a larger one would leave it blocked on that write until the harness killed it and
        // reported "HUNG" for an instance it had solved in a second.
        Files.writeString(directory.resolve("large-solution.xml"), instanceWithALargeSolution());
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        Xcsp3CompetitionRunner.run(directory, 5, 1L, new PrintStream(captured, true, StandardCharsets.UTF_8));

        String report = captured.toString(StandardCharsets.UTF_8);
        assertThat(report).doesNotContain("HUNG");
        assertThat(report).contains("SATISFIABLE");
    }
}
