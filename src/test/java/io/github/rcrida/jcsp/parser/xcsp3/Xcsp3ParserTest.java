package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Xcsp3ParserTest {

    @TempDir
    Path tempDir;

    private Xcsp3Instance parseXml(String variablesXml, String constraintsXml) throws IOException {
        return parseXml(variablesXml, constraintsXml, null);
    }

    private Xcsp3Instance parseXml(String variablesXml, String constraintsXml, String objectivesXml) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<instance format=\"XCSP3\" type=\"").append(objectivesXml == null ? "CSP" : "COP").append("\">\n");
        xml.append("<variables>\n").append(variablesXml).append("\n</variables>\n");
        xml.append("<constraints>\n").append(constraintsXml).append("\n</constraints>\n");
        if (objectivesXml != null) {
            xml.append("<objectives>\n").append(objectivesXml).append("\n</objectives>\n");
        }
        xml.append("</instance>\n");
        Path file = tempDir.resolve("instance.xml");
        Files.writeString(file, xml.toString());
        return Xcsp3Parser.parse(file);
    }

    private static Set<Assignment> solutions(ConstraintSatisfactionProblem csp) {
        return Solver.Factory.INSTANCE.createSolver(csp).getSolutions().collect(Collectors.toSet());
    }

    private static int digitOf(Assignment assignment, String name) {
        return assignment.getValue(Variable.Factory.INSTANCE.<Integer>create(name)).orElseThrow();
    }

    // ---- variable domains -------------------------------------------------------------------------

    @Test void rangeDomain_solvesWithinBounds() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var>",
                "<intension> eq(x,2) </intension>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp()).getSolution();
        assertThat(solution).isPresent();
        assertThat((Object) solution.get().getValues().values().iterator().next()).isEqualTo(2);
    }

    @Test void explicitValueListDomain_excludesGaps() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1 3 5 </var>",
                "<intension> eq(x,3) </intension>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp()).getSolution();
        assertThat(solution).isPresent();
        assertThat((Object) solution.get().getValues().values().iterator().next()).isEqualTo(3);
    }

    @Test void rangeDomainTooLargeToMaterialize_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2000000 </var>",
                "<intension> eq(x,1) </intension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- intension ----------------------------------------------------------------------------------

    @Test void intension_buildsPredicateConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> eq(add(x,y),10) </intension>");
        for (Assignment solution : solutions(instance.csp())) {
            int x = (int) solution.getValues().values().stream().toList().get(0);
            int total = solution.getValues().values().stream().mapToInt(v -> (int) v).sum();
            assertThat(total).isEqualTo(10);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- reification ------------------------------------------------------------------------------------------------

    @Test void intensionFullyReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(x,2) </intension>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, b=%d", x, b).isEqualTo(x == 2);
        }
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void intensionHalfReifiedFrom_indicatorImpliesConstraintOnly() throws IOException {
        // hreifiedFrom="b" is b -> constraint, not the other direction: b=0 leaves x unconstrained
        // (4 solutions), b=1 forces x=2 (1 solution) -- 5 total, not the 4 a full reification gives.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension hreifiedFrom=\"b\"> eq(x,2) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(5);
        for (Assignment a : solutions) {
            if (digitOf(a, "b") == 1) assertThat(digitOf(a, "x")).isEqualTo(2);
        }
    }

    @Test void intensionHalfReifiedTo_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension hreifiedTo=\"b\"> eq(x,2) </intension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void sumFullyReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<sum reifiedBy=\"b\"><list> x y </list><condition> (le,2) </condition></sum>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x + y <= 2);
        }
        assertThat(solutions(instance.csp())).hasSize(9);
    }

    @Test void sumWithVariableTargetReified_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"k\"> 0..4 </var><var id=\"b\"> 0..1 </var>",
                "<sum reifiedBy=\"b\"><list> x y </list><condition> (le,k) </condition></sum>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void twoConstraintsReifiedBySameIndicator_shareOneBooleanBridgeVariable() throws IOException {
        // Both constraints are reified by "b"; the boolean bridge variable created for "b" should
        // be memoized and reused (not one auxiliary per occurrence), matching shiftVariable's
        // memoization pattern for a shared index variable.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(x,2) </intension><intension reifiedBy=\"b\"> eq(y,2) </intension>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x == 2 && y == 2);
        }
    }

    @Test void allDifferentFullyReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<allDifferent reifiedBy=\"b\"><list> x y z </list></allDifferent>");
        int trueCount = 0;
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            int b = digitOf(a, "b");
            boolean allDiff = x != y && y != z && x != z;
            assertThat(b == 1).as("x=%d, y=%d, z=%d, b=%d", x, y, z, b).isEqualTo(allDiff);
            if (allDiff) trueCount++;
        }
        assertThat(trueCount).isEqualTo(6); // 3! permutations of {0,1,2}
    }

    @Test void cardinalityFullyReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<cardinality reifiedBy=\"b\"><list> x y </list><values closed=\"false\"> 0 1 </values>"
                        + "<occurs> 1 1 </occurs></cardinality>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x != y);
        }
    }

    @Test void orderedFullyReified_indicatorTracksWholeChainTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<ordered reifiedBy=\"b\"><list> x y z </list><operator> lt </operator></ordered>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, z=%d, b=%d", x, y, z, b).isEqualTo(x < y && y < z);
        }
    }

    // ---- extension (table) --------------------------------------------------------------------------

    @Test void extensionSupport_buildsTuplesConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><supports> (0,1)(2,0) </supports></extension>");
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    @Test void extensionConflict_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><conflicts> (0,0) </conflicts></extension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void extensionStarredTuples_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><supports> (0,*)(1,1) </supports></extension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void unaryExtensionPositive_restrictsVariableToListedValues() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var>",
                "<extension><list> x </list><supports> 2 4 </supports></extension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(2);
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isIn(2, 4);
        }
    }

    @Test void unaryExtensionNegative_excludesListedValues() throws IOException {
        // Unlike the n-ary conflict-table form (rejected -- see extensionConflict_throwsUnsupported
        // -- since materializing its complement over several variables could blow up
        // combinatorially), a unary restriction is just a predicate over one variable's own domain,
        // so negation is cheap and safe to support directly.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var>",
                "<extension><list> x </list><conflicts> 1 2 </conflicts></extension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(2);
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isIn(0, 3);
        }
    }

    @Test void unaryExtensionWithRangeNotation_expandsBeforeReachingCallback() throws IOException {
        // "0..2" is TypeFlag.UNCLEAN_TUPLES, not STARRED_TUPLES/SMART_TUPLES -- xcsp3-tools has
        // already expanded it into a plain int[] by the time buildCtrExtension is called, so this
        // must not be rejected the way a genuine starred/smart tuple is.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var>",
                "<extension><list> x </list><supports> 0..2 </supports></extension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(3);
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isBetween(0, 2);
        }
    }

    // ---- allDifferent ---------------------------------------------------------------------------------

    @Test void allDifferent_buildsAllDiffConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<allDifferent> x y </allDifferent>");
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    @Test void allDifferentMatrix_buildsRowAndColumnAllDiffConstraints() throws IOException {
        // 2x2 Latin square over {0,1}: only 2 arrangements keep every row and every column
        // all-different -- [[0,1],[1,0]] and [[1,0],[0,1]].
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2][2]\"> 0..1 </array>",
                "<allDifferent><matrix> x[][] </matrix></allDifferent>");
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    @Test void allDifferentMatrixReified_indicatorTracksConstraintTruthValue() throws IOException {
        // Reified via AndConstraint: one AllDiffConstraint per row plus one per column, conjoined.
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2][2]\"> 0..1 </array><var id=\"b\"> 0..1 </var>",
                "<allDifferent reifiedBy=\"b\"><matrix> x[][] </matrix></allDifferent>");
        int trueCount = 0;
        for (Assignment a : solutions(instance.csp())) {
            int x00 = digitOf(a, "x[0][0]");
            int x01 = digitOf(a, "x[0][1]");
            int x10 = digitOf(a, "x[1][0]");
            int x11 = digitOf(a, "x[1][1]");
            boolean latinSquare = x00 != x01 && x10 != x11 && x00 != x10 && x01 != x11;
            int b = digitOf(a, "b");
            assertThat(b == 1).as("matrix=[[%d,%d],[%d,%d]], b=%d", x00, x01, x10, x11, b).isEqualTo(latinSquare);
            if (latinSquare) trueCount++;
        }
        assertThat(trueCount).isEqualTo(2); // [[0,1],[1,0]] and [[1,0],[0,1]]
    }

    // ---- allEqual -----------------------------------------------------------------------------------

    @Test void allEqual_buildsAllEqualConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<allEqual> x y z </allEqual>");
        assertThat(solutions(instance.csp())).hasSize(3); // (0,0,0), (1,1,1), (2,2,2)
    }

    @Test void allEqualReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<allEqual reifiedBy=\"b\"> x y </allEqual>");
        for (Assignment a : solutions(instance.csp())) {
            boolean equal = digitOf(a, "x") == digitOf(a, "y");
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d", digitOf(a, "x"), digitOf(a, "y")).isEqualTo(equal);
        }
        assertThat(solutions(instance.csp())).hasSize(4); // all 2x2 combinations, just with b tracking equality
    }

    // ---- sum ------------------------------------------------------------------------------------------------

    @Test void sumUnweighted_buildsSumConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><condition> (eq,5) </condition></sum>");
        for (Assignment solution : solutions(instance.csp())) {
            int total = solution.getValues().values().stream().mapToInt(v -> (int) v).sum();
            assertThat(total).isEqualTo(5);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void sumWeighted_buildsLinearConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><coeffs> 2 3 </coeffs><condition> (eq,10) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void sumVariableCoefficients_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"c1\"> 1..2 </var><var id=\"c2\"> 1..2 </var>",
                "<sum><list> x y </list><coeffs> c1 c2 </coeffs><condition> (eq,10) </condition></sum>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- count / among -----------------------------------------------------------------------------------------

    @Test void countSingleValue_buildsCountConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 1 </values><condition> (eq,2) </condition></count>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countMultipleValues_buildsAmongConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 1 2 </values><condition> (eq,2) </condition></count>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countWithVariableTarget_linksAuxiliaryToRealTarget() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"k\"> 0..3 </var>",
                "<count><list> x y z </list><values> 1 </values><condition> (eq,k) </condition></count>");
        for (Assignment a : solutions(instance.csp())) {
            long actual = Stream.of(digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "z")).filter(v -> v == 1).count();
            assertThat(digitOf(a, "k")).isEqualTo((int) actual);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countWithVariableTargetMultipleValues_buildsAmongVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"k\"> 0..3 </var>",
                "<count><list> x y z </list><values> 1 2 </values><condition> (eq,k) </condition></count>");
        for (Assignment a : solutions(instance.csp())) {
            long actual = Stream.of(digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "z")).filter(v -> v == 1 || v == 2).count();
            assertThat(digitOf(a, "k")).isEqualTo((int) actual);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countWithVariableTargetReified_indicatorTracksConditionTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"k\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<count reifiedBy=\"b\"><list> x y </list><values> 1 </values><condition> (eq,k) </condition></count>");
        for (Assignment a : solutions(instance.csp())) {
            long actual = Stream.of(digitOf(a, "x"), digitOf(a, "y")).filter(v -> v == 1).count();
            int k = digitOf(a, "k");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("count=%d, k=%d, b=%d", actual, k, b).isEqualTo(actual == k);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countWithSetCondition_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 1 </values><condition> (in,1..3) </condition></count>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- nValues --------------------------------------------------------------------------------------------

    @Test void nValuesWithConstantCondition_buildsNValueConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var><var id=\"x4\"> 1..3 </var>",
                "<nValues><list> x1 x2 x3 x4 </list><condition> (eq,3) </condition></nValues>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void nValuesWithVariableCondition_buildsNValueConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var><var id=\"k\"> 1..4 </var>",
                "<nValues><list> x1 x2 x3 </list><condition> (eq,k) </condition></nValues>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void nValuesWithSetCondition_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<nValues><list> x1 x2 x3 </list><condition> (in,1..3) </condition></nValues>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- cardinality (global cardinality constraint) ---------------------------------------------------------

    @Test void cardinalityFixedOccurs_buildsGlobalCardinalityConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> 1 2 3 </values>"
                        + "<occurs> 1 1 1 </occurs></cardinality>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cardinalityClosedCoveringEveryDomain_buildsGlobalCardinalityConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"true\"> 1 2 3 </values>"
                        + "<occurs> 1 1 1 </occurs></cardinality>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cardinalityClosedNotCoveringEveryDomain_throwsUnsupported() {
        // x1's domain (1..4) isn't fully covered by values {1,2,3}.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..4 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"true\"> 1 2 3 </values>"
                        + "<occurs> 1 1 1 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithVariableOccurs_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"o1\"> 0..3 </var><var id=\"o2\"> 0..3 </var><var id=\"o3\"> 0..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> 1 2 3 </values>"
                        + "<occurs> o1 o2 o3 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithOccursRange_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> 1 2 3 </values>"
                        + "<occurs> 0..1 1..3 2..3 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithVariableValuesAndOccurs_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"v1\"> 1..3 </var><var id=\"v2\"> 1..3 </var><var id=\"v3\"> 1..3 </var>"
                        + "<var id=\"o1\"> 0..3 </var><var id=\"o2\"> 0..3 </var><var id=\"o3\"> 0..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> v1 v2 v3 </values>"
                        + "<occurs> o1 o2 o3 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithVariableValuesAndFixedOccurs_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"v1\"> 1..3 </var><var id=\"v2\"> 1..3 </var><var id=\"v3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> v1 v2 v3 </values>"
                        + "<occurs> 1 1 1 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithVariableValuesAndOccursRange_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"v1\"> 1..3 </var><var id=\"v2\"> 1..3 </var><var id=\"v3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> v1 v2 v3 </values>"
                        + "<occurs> 0..1 1..3 2..3 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- element --------------------------------------------------------------------------------------------------

    @Test void elementVariableArray_buildsElementVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var><var id=\"v\"> 0..5 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><value> v </value></element>");
        for (Assignment solution : solutions(instance.csp())) {
            assertThat(solution).isNotNull();
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void elementConstantArray_buildsElementConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"i\"> 0..2 </var><var id=\"v\"> 0..9 </var>",
                "<element><list startIndex=\"0\"> 4 5 6 </list><index> i </index><value> v </value></element>");
        for (Assignment solution : solutions(instance.csp())) {
            assertThat(solution).isNotNull();
        }
        assertThat(solutions(instance.csp())).hasSize(3);
    }

    @Test void elementSharedIndexAcrossConstraints_reusesOneShiftedAuxiliaryVariable() throws IOException {
        // Both element constraints shift the same startIndex="0" index variable i by the same
        // offset; shiftVariable's memoization should build exactly one auxiliary variable for it
        // rather than one per occurrence: 6 declared vars (a[0..2], i, v1, v2) plus 1 shared
        // auxiliary shifted index, not 2.
        Xcsp3Instance instance = parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var><var id=\"v1\"> 0..5 </var><var id=\"v2\"> 0..5 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><value> v1 </value></element>"
                        + "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><value> v2 </value></element>");
        assertThat(instance.csp().getVariableDomains()).hasSize(7);
    }

    @Test void elementWithConstantCondition_throwsUnsupported() {
        // <value> 5 </value> against a constant (rather than a variable) yields a ConditionVal,
        // which elementResult rejects -- jcsp's element/elementVariable constraints always need a
        // real result *variable*, not a fixed constant.
        assertThatThrownBy(() -> parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><value> 5 </value></element>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void elementWithRankModifier_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var><var id=\"v\"> 0..5 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index rank=\"first\"> i </index><value> v </value></element>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void elementWithNonEqCondition_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var><var id=\"v\"> 0..5 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><condition> (ne,v) </condition></element>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- minimum / maximum ------------------------------------------------------------------------------------------

    @Test void minimumFixedBound_buildsMinConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<minimum><list> x y </list><condition> (eq,2) </condition></minimum>");
        for (Assignment a : solutions(instance.csp())) {
            assertThat(Math.min(digitOf(a, "x"), digitOf(a, "y"))).isEqualTo(2);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void minimumVariableTarget_buildsMinVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"k\"> 0..5 </var>",
                "<minimum><list> x y </list><condition> (eq,k) </condition></minimum>");
        for (Assignment a : solutions(instance.csp())) {
            assertThat(digitOf(a, "k")).isEqualTo(Math.min(digitOf(a, "x"), digitOf(a, "y")));
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void minimumReified_indicatorTracksConditionTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<minimum reifiedBy=\"b\"><list> x y </list><condition> (eq,0) </condition></minimum>");
        for (Assignment a : solutions(instance.csp())) {
            boolean expected = Math.min(digitOf(a, "x"), digitOf(a, "y")) == 0;
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d", digitOf(a, "x"), digitOf(a, "y")).isEqualTo(expected);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void maximumFixedBound_buildsMaxConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<maximum><list> x y </list><condition> (eq,4) </condition></maximum>");
        for (Assignment a : solutions(instance.csp())) {
            assertThat(Math.max(digitOf(a, "x"), digitOf(a, "y"))).isEqualTo(4);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void maximumVariableTarget_buildsMaxVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"k\"> 0..5 </var>",
                "<maximum><list> x y </list><condition> (eq,k) </condition></maximum>");
        for (Assignment a : solutions(instance.csp())) {
            assertThat(digitOf(a, "k")).isEqualTo(Math.max(digitOf(a, "x"), digitOf(a, "y")));
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- regular ------------------------------------------------------------------------------------------------------

    @Test void regular_buildsRegularConstraint_acceptsOnlyMatchingSequence() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<regular><list> x y </list>"
                        + "<transitions> (q0,0,q1)(q1,1,q2) </transitions>"
                        + "<start> q0 </start><final> q2 </final></regular>");
        Set<Assignment> sols = solutions(instance.csp());
        assertThat(sols).hasSize(1);
        Assignment solution = sols.iterator().next();
        assertThat(digitOf(solution, "x")).isEqualTo(0);
        assertThat(digitOf(solution, "y")).isEqualTo(1);
    }

    @Test void regular_selfLoopAndMultipleFinalStates_acceptsBothLengths() throws IOException {
        // Language: 0*1 or 0*11 -- any number of leading 0s (self-loop on q0), then either
        // "1" (accepting at q1) or "11" (accepting at q2), exercising a state visited via a
        // self-loop (q0) plus more than one accepting state.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<regular><list> x y </list>"
                        + "<transitions> (q0,0,q0)(q0,1,q1)(q1,1,q2) </transitions>"
                        + "<start> q0 </start><final> q1 q2 </final></regular>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            boolean accepted = (digitOf(a, "x") == 0 && digitOf(a, "y") == 1) || (digitOf(a, "x") == 1 && digitOf(a, "y") == 1);
            assertThat(accepted).as("x=%d, y=%d", digitOf(a, "x"), digitOf(a, "y")).isTrue();
        }
        assertThat(sols).hasSize(2); // (0,1) and (1,1)
    }

    @Test void regular_outOfOrderTransitionsAndUnreachableFinalState_stillNumbersStatesCorrectly() throws IOException {
        // Transitions listed out of BFS order: the first transition's start (q1) hasn't been seen
        // yet via startState or any earlier transition's end, exercising the "newly discovered
        // state" branch when scanning a transition's start (not just its end). "qz" in <final> is
        // never referenced by any transition, exercising the same "newly discovered state" branch
        // when scanning finalStates. Language: q0 --1--> q1 --0--> q2(final); qz is unreachable and
        // contributes no extra accepted sequences.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<regular><list> x y </list>"
                        + "<transitions> (q1,0,q2)(q0,1,q1) </transitions>"
                        + "<start> q0 </start><final> q2 qz </final></regular>");
        Set<Assignment> sols = solutions(instance.csp());
        assertThat(sols).hasSize(1);
        Assignment solution = sols.iterator().next();
        assertThat(digitOf(solution, "x")).isEqualTo(1);
        assertThat(digitOf(solution, "y")).isEqualTo(0);
    }

    @Test void regularReified_indicatorTracksAcceptance() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<regular reifiedBy=\"b\"><list> x y </list>"
                        + "<transitions> (q0,0,q1)(q1,1,q2) </transitions>"
                        + "<start> q0 </start><final> q2 </final></regular>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            boolean accepted = digitOf(a, "x") == 0 && digitOf(a, "y") == 1;
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d", digitOf(a, "x"), digitOf(a, "y")).isEqualTo(accepted);
        }
        assertThat(sols).hasSize(4); // all 2x2 combinations, just with b tracking acceptance
    }

    // ---- channel --------------------------------------------------------------------------------------------------

    @Test void channelTwoArrays_buildsInverseConstraint_acceptsEveryPermutationPair() throws IOException {
        // Every permutation of {0,1,2} pairs with its (unique) true inverse permutation --
        // 3! = 6 solutions, none of them requiring x to be self-inverse (unlike the single-array form).
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[3]\"> 0..2 </array><array id=\"y\" size=\"[3]\"> 0..2 </array>",
                "<channel><list> x[] </list><list> y[] </list></channel>");
        assertThat(solutions(instance.csp())).hasSize(6);
    }

    @Test void channelTwoArrays_differentStartIndices_shiftsIndependently() throws IOException {
        // list1 already 1-based (startIndex 1, offset 0 -- shiftVariable's identity branch) while
        // list2 is 0-based (startIndex 0, offset 1 -- the real shift branch), exercising both
        // shiftVariable branches within the same constraint.
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2]\"> 1..2 </array><array id=\"y\" size=\"[2]\"> 0..1 </array>",
                "<channel><list startIndex=\"1\"> x[] </list><list startIndex=\"0\"> y[] </list></channel>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            // x[i] (1-based value, i 0-based) == j <-> y[j] (0-based value, j 0-based) == i
            int x0 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("x[0]")).orElseThrow();
            int x1 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("x[1]")).orElseThrow();
            int y0 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("y[0]")).orElseThrow();
            int y1 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("y[1]")).orElseThrow();
            int[] x = {x0, x1};
            int[] y = {y0, y1};
            for (int i = 0; i < 2; i++) {
                assertThat(y[x[i] - 1]).isEqualTo(i);
            }
        }
        assertThat(sols).hasSize(2);
    }

    @Test void channelSingleArray_buildsSelfInverseConstraint_acceptsOnlyInvolutions() throws IOException {
        // Involutions of {0,1,2}: identity, and each of the 3 single-swaps with the third element
        // fixed -- 4 solutions (OEIS A000085).
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[3]\"> 0..2 </array>",
                "<channel><list startIndex=\"0\"> x[] </list></channel>");
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void channelTwoArraysReified_indicatorTracksInverseRelation() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2]\"> 0..1 </array><array id=\"y\" size=\"[2]\"> 0..1 </array><var id=\"b\"> 0..1 </var>",
                "<channel reifiedBy=\"b\"><list> x[] </list><list> y[] </list></channel>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int x0 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("x[0]")).orElseThrow();
            int x1 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("x[1]")).orElseThrow();
            int y0 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("y[0]")).orElseThrow();
            int y1 = a.getValue(Variable.Factory.INSTANCE.<Integer>create("y[1]")).orElseThrow();
            boolean channelled = (x0 == 0 && y0 == 0 && x1 == 1 && y1 == 1) || (x0 == 1 && y1 == 0 && x1 == 0 && y0 == 1);
            assertThat(digitOf(a, "b") == 1).as("x=[%d,%d], y=[%d,%d]", x0, x1, y0, y1).isEqualTo(channelled);
        }
        assertThat(sols).hasSize(16); // all 2x2x2 combinations of x,y,b, just with b tracking the relation
    }

    @Test void channelWithSingleHotIndexValue_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<array id=\"x\" size=\"[3]\"> 0..1 </array><var id=\"v\"> 0..2 </var>",
                "<channel><list> x[] </list><value> v </value></channel>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- ordered / lex -----------------------------------------------------------------------------------------------

    @Test void ordered_buildsPairwiseComparatorChain() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<ordered><list> x y z </list><operator> lt </operator></ordered>");
        assertThat(solutions(instance.csp())).hasSize(1);
    }

    @Test void lex_buildsLexConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 0..1 </var><var id=\"x2\"> 0..1 </var><var id=\"y1\"> 0..1 </var><var id=\"y2\"> 0..1 </var>",
                "<lex><list> x1 x2 </list><list> y1 y2 </list><operator> le </operator></lex>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void lexWithMoreThanTwoLists_decomposesIntoConsecutivePairwiseChain() throws IOException {
        // x1<=y1<=z1 over {0,1}: exactly the 4 non-decreasing chains (000, 001, 011, 111).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 0..1 </var><var id=\"y1\"> 0..1 </var><var id=\"z1\"> 0..1 </var>",
                "<lex><list> x1 </list><list> y1 </list><list> z1 </list><operator> le </operator></lex>");
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void lexWithMoreThanTwoListsReified_indicatorTracksWholeChainTruthValue() throws IOException {
        // Reified via AndConstraint: two pairwise LexConstraints (x1<=y1, y1<=z1), conjoined.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 0..1 </var><var id=\"y1\"> 0..1 </var><var id=\"z1\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<lex reifiedBy=\"b\"><list> x1 </list><list> y1 </list><list> z1 </list><operator> le </operator></lex>");
        int trueCount = 0;
        for (Assignment a : solutions(instance.csp())) {
            int x1 = digitOf(a, "x1");
            int y1 = digitOf(a, "y1");
            int z1 = digitOf(a, "z1");
            int b = digitOf(a, "b");
            boolean chainHolds = x1 <= y1 && y1 <= z1;
            assertThat(b == 1).as("x1=%d, y1=%d, z1=%d, b=%d", x1, y1, z1, b).isEqualTo(chainHolds);
            if (chainHolds) trueCount++;
        }
        assertThat(trueCount).isEqualTo(4); // 000, 001, 011, 111
    }

    // ---- cumulative -----------------------------------------------------------------------------------------------------

    @Test void cumulative_buildsCumulativeConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"s1\"> 0..3 </var><var id=\"s2\"> 0..3 </var>",
                "<cumulative><origins> s1 s2 </origins><lengths> 2 2 </lengths><heights> 1 1 </heights>"
                        + "<condition> (le,1) </condition></cumulative>");
        for (Assignment solution : solutions(instance.csp())) {
            assertThat(solution).isNotNull();
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cumulativeNonLeCondition_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"s1\"> 0..3 </var><var id=\"s2\"> 0..3 </var>",
                "<cumulative><origins> s1 s2 </origins><lengths> 2 2 </lengths><heights> 1 1 </heights>"
                        + "<condition> (lt,2) </condition></cumulative>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cumulativeWithVariableCapacity_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"s1\"> 0..3 </var><var id=\"s2\"> 0..3 </var><var id=\"c\"> 0..2 </var>",
                "<cumulative><origins> s1 s2 </origins><lengths> 2 2 </lengths><heights> 1 1 </heights>"
                        + "<condition> (le,c) </condition></cumulative>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- slide ----------------------------------------------------------------------------------------------------------

    @Test void slideOfSum_buildsOneSumConstraintPerWindow() throws IOException {
        // Like group, xcsp3-tools' default loadSlide expands the sliding template (here a sum with
        // a <=2 condition, arity 3 inferred from %0 %1 %2) into repeated ordinary buildCtrSum calls
        // before Xcsp3CallbackHandler ever sees it -- one per window of 3 consecutive elements
        // (offset 1 by default): [x0,x1,x2], [x1,x2,x3], [x2,x3,x4], [x3,x4,x5].
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[6]\"> 0..1 </array>",
                "<slide><list> x[] </list><sum><list> %0 %1 %2 </list>"
                        + "<condition> (le,2) </condition></sum></slide>");
        assertThat(instance.csp().getConstraints()).hasSize(4);
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- circuit --------------------------------------------------------------------------------------------------------

    @Test void circuit_buildsCircuitConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"s1\"> 0..3 </var><var id=\"s2\"> 0..3 </var><var id=\"s3\"> 0..3 </var><var id=\"s4\"> 0..3 </var>",
                "<circuit><list startIndex=\"0\"> s1 s2 s3 s4 </list></circuit>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void circuitWithStartIndexOne_needsNoShift() throws IOException {
        // startIndex="1" already matches jcsp's own 1-indexed circuitConstraint convention, so
        // shiftVariable's offset==0 identity branch applies instead of building an auxiliary
        // offset-linked variable.
        Xcsp3Instance instance = parseXml(
                "<var id=\"s1\"> 1..4 </var><var id=\"s2\"> 1..4 </var><var id=\"s3\"> 1..4 </var><var id=\"s4\"> 1..4 </var>",
                "<circuit><list startIndex=\"1\"> s1 s2 s3 s4 </list></circuit>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- binPacking -----------------------------------------------------------------------------------------------------

    @Test void binPacking_buildsBinPackingConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"b1\"> 0..1 </var><var id=\"b2\"> 0..1 </var>",
                "<binPacking><list> b1 b2 </list><sizes> 3 4 </sizes><condition> (le,4) </condition></binPacking>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void binPackingNonLeCondition_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"b1\"> 0..1 </var><var id=\"b2\"> 0..1 </var>",
                "<binPacking><list> b1 b2 </list><sizes> 3 4 </sizes><condition> (lt,4) </condition></binPacking>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void binPackingNonZeroIndexedBins_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"b1\"> 1..2 </var><var id=\"b2\"> 1..2 </var>",
                "<binPacking><list> b1 b2 </list><sizes> 3 4 </sizes><condition> (le,4) </condition></binPacking>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void binPackingWithVariableCapacity_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"b1\"> 0..1 </var><var id=\"b2\"> 0..1 </var><var id=\"c\"> 0..10 </var>",
                "<binPacking><list> b1 b2 </list><sizes> 3 4 </sizes><condition> (le,c) </condition></binPacking>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- instantiation ----------------------------------------------------------------------------------------

    @Test void instantiation_pinsListedVariablesToListedValues() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<instantiation><list> x y </list><values> 2 3 </values></instantiation>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(1);
        Assignment only = solutions.iterator().next();
        assertThat(digitOf(only, "x")).isEqualTo(2);
        assertThat(digitOf(only, "y")).isEqualTo(3);
    }

    @Test void instantiationInconsistentWithOtherConstraints_isUnsatisfiable() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<instantiation><list> x y </list><values> 2 2 </values></instantiation>"
                        + "<intension> ne(x,y) </intension>");
        assertThat(solutions(instance.csp())).isEmpty();
    }

    @Test void instantiationReified_indicatorTracksConstraintTruthValue() throws IOException {
        // Reified via AndConstraint: one UnaryValueConstraint per pinned variable, conjoined.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"b\"> 0..1 </var>",
                "<instantiation reifiedBy=\"b\"><list> x </list><values> 2 </values></instantiation>");
        Set<Assignment> solutions = solutions(instance.csp());
        int trueCount = 0;
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, b=%d", x, b).isEqualTo(x == 2);
            if (x == 2) trueCount++;
        }
        assertThat(trueCount).isEqualTo(1);
        assertThat(solutions).hasSize(6); // x in {0..5}, b determined each time
    }

    // ---- objectives -----------------------------------------------------------------------------------------------------

    @Test void objectiveMinimizeVariable_solvesToLowerBound() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> ge(x,3) </intension>",
                "<minimize> x </minimize>");
        assertThat(instance.objective()).isNotNull();
        assertThat(instance.maximize()).isFalse();
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat((Object) solution.get().getValues().values().iterator().next()).isEqualTo(3);
    }

    @Test void objectiveMaximizeVariable_solvesToUpperBound() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> le(x,6) </intension>",
                "<maximize> x </maximize>");
        assertThat(instance.maximize()).isTrue();
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat((Object) solution.get().getValues().values().iterator().next()).isEqualTo(6);
    }

    @Test void objectiveMinimizeSum_buildsLinearObjective() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"sum\"><list> x y </list><coeffs> 1 1 </coeffs></minimize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        int total = solution.get().getValues().values().stream().mapToInt(v -> (int) v).sum();
        assertThat(total).isEqualTo(2);
    }

    @Test void generalExpressionMinimizeObjective_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize> add(x,y) </minimize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void generalExpressionMaximizeObjective_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<maximize> add(x,y) </maximize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- operator coverage (mapOperator / mapOrderingOperator) -------------------------------------------------------------

    @Test void sumLessThan_usesLtOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><condition> (lt,3) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void sumLessOrEqual_usesLeqOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><condition> (le,3) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void sumGreaterThan_usesGtOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><condition> (gt,3) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void sumNotEqual_usesNeqOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<sum><list> x y </list><condition> (ne,3) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void orderedGreaterOrEqual_usesGeqOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<ordered><list> x y </list><operator> ge </operator></ordered>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void orderedGreaterThan_usesGtOperator() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<ordered><list> x y </list><operator> gt </operator></ordered>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- variable-target (ConditionVar) sum/linear conditions --------------------------------------------------------------

    @Test void sumWithVariableTarget_usesConditionVar() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..10 </var>",
                "<sum><list> x y </list><condition> (eq,z) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void linearWithVariableTarget_usesConditionVar() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..20 </var>",
                "<sum><list> x y </list><coeffs> 2 3 </coeffs><condition> (eq,z) </condition></sum>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    // ---- more objective array forms (unweighted, weighted-maximize, non-sum aggregation) -----------------------------------

    @Test void objectiveMinimizeSumArray_unweighted() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"sum\"><list> x y </list></minimize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
    }

    @Test void objectiveMaximizeSumArray_unweighted() throws IOException {
        // x<=6, y<=9 (unconstrained): true optimum is x=6,y=9,sum=15 -- asserting the exact value
        // (not just that some solution was found) is load-bearing here, since an unsound partial-
        // assignment lower bound would let BranchAndBoundSolver prune the true optimum away silently.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> le(x,6) </intension>",
                "<maximize type=\"sum\"><list> x y </list></maximize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        // objective() is already negated for minimization (maximize()==true); negate back to XCSP3's own sense.
        assertThat(-instance.objective().applyAsDouble(solution.get())).isEqualTo(15.0);
    }

    @Test void objectiveMaximizeWeightedSumArray() throws IOException {
        // x<=5, y<=9 (unconstrained): true optimum is x=5,y=9, value=2*5+3*9=37.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> le(x,5) </intension>",
                "<maximize type=\"sum\"><list> x y </list><coeffs> 2 3 </coeffs></maximize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat(-instance.objective().applyAsDouble(solution.get())).isEqualTo(37.0);
    }

    @Test void objectiveMinimizeMaximumArray_solvesToTrueMinimizedMax() throws IOException {
        // x in [2,9] (via ge(x,2)), y in [0,9] unconstrained: max(x,y) can be pushed no lower than
        // x's own lower bound of 2 (achieved at x=2, y<=2), regardless of y.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"maximum\"><list> x y </list></minimize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat(instance.objective().applyAsDouble(solution.get())).isEqualTo(2.0);
    }

    @Test void objectiveMaximizeMaximumArray_solvesToTrueMaximizedMax() throws IOException {
        // x in [0,6] (via le(x,6)), y in [0,9] unconstrained: max(x,y) can reach y's own upper
        // bound of 9 regardless of x.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> le(x,6) </intension>",
                "<maximize type=\"maximum\"><list> x y </list></maximize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        // objective() is already negated for minimization (maximize()==true); negate back to XCSP3's own sense.
        assertThat(-instance.objective().applyAsDouble(solution.get())).isEqualTo(9.0);
    }

    @Test void objectiveMaximumArrayWeighted_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"maximum\"><list> x y </list><coeffs> 2 3 </coeffs></minimize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void objectiveNonSumNonMaximumType_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"minimum\"><list> x y </list></minimize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void objectiveMinimizeSumWithNegativeCoefficient_solvesToTrueOptimum() throws IOException {
        // Minimizing -1*x + 2*y is minimized by taking x as large as possible (x=9) and y as small
        // as possible (y=0): value=-9. A negative coefficient like this used to be rejected by a
        // non-negativity restriction; LpModelBuilder's LP relaxation (unlocked by buildSumObjective
        // negating coefficients into a genuine LinearObjective rather than negating a lambda's
        // result) is directly sound for it, so no restriction is needed any more.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"sum\"><list> x y </list><coeffs> -1 2 </coeffs></minimize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat(instance.objective().applyAsDouble(solution.get())).isEqualTo(-9.0);
    }

    @Test void objectiveMaximizeSumWithNegativeDomain_solvesToTrueOptimum() throws IOException {
        // x's declared domain dips negative (-3..9); maximizing x+y picks x=9 (domain max), y=9.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..9 </var><var id=\"y\"> 0..9 </var>",
                "",
                "<maximize type=\"sum\"><list> x y </list></maximize>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        assertThat(-instance.objective().applyAsDouble(solution.get())).isEqualTo(18.0);
    }

    // ---- more intension operators --------------------------------------------------------------------------------------------

    @Test void intensionSubtraction() throws IOException {
        // sub(x,y) nested inside mod(...,3) rather than directly under eq(...) -- xcsp3-tools'
        // canonizer otherwise rewrites a top-level "eq(sub(x,y),k)" into an equivalent add-based
        // form ("eq(add(y,k),x)"), which would never actually exercise the SUB operator branch.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> eq(mod(sub(x,y),3),1) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionMultiplication() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..5 </var><var id=\"y\"> 1..5 </var>",
                "<intension> eq(mul(x,y),6) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionDivision() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..9 </var>",
                "<intension> eq(div(x,3),2) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionModulo() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> eq(mod(x,3),1) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionNegationAndAbs() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -5..5 </var>",
                "<intension> eq(abs(neg(x)),3) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionDist() throws IOException {
        // dist(x,y) is |x - y|; appears in real-world instances (e.g. xcsp.org's n-Queens "m1"
        // model uses ne(k,dist(q[i],q[j])) for the diagonal-attack rule).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<intension> eq(dist(x,y),3) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionNotEqual() throws IOException {
        // A relational root directly comparing two variables (ne/lt below) survives
        // xcsp3-tools' canonization unchanged; ge/gt do not (always rewritten to le/lt with
        // swapped operands), which is covered separately in IntensionExpressionEvaluatorTest.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ne(x,y) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionLessThan() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> lt(x,y) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionNot() throws IOException {
        // not(x) over a bare boolean-domain variable survives canonization (not(relop) forms get
        // rewritten away into the relop's own inverse, e.g. not(gt(x,5)) becomes le(x,5)).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var>",
                "<intension> not(x) </intension>");
        assertThat(solutions(instance.csp())).containsExactly(Assignment.of(Map.of(Variable.Factory.INSTANCE.create("x"), 0)));
    }

    @Test void intensionAnd() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> and(gt(x,1),lt(y,8)) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionOr() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> or(gt(x,8),lt(y,2)) </intension>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void intensionSetMembership_evaluatesInAndNotin() throws IOException {
        // The Hanoi-05 CSPLib instance uses in(x[0],set(1,2)) directly; notin is folded in here too
        // for the same coverage in one XML fixture.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<intension> and(in(x,set(1,2)),notin(y,set(1,2))) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isIn(1, 2);
            assertThat(digitOf(a, "y")).isNotIn(1, 2);
        }
    }

    // ---- intension binary-relation recognition (BinaryComparatorConstraint / BinaryOffsetConstraint) ------------------

    @Test void intensionBareBinaryComparison_recognizedAsBinaryComparatorConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> lt(x,y) </intension>");
        assertThat(instance.csp().getConstraints()).hasSize(1);
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(BinaryComparatorConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isLessThan(digitOf(a, "y"));
        }
    }

    @Test void intensionBinaryOffsetWithEquals_recognizedAsBinaryOffsetConstraint() throws IOException {
        // eq is commutative, and xcsp3-tools' own canonizer normalizes it (and add(...)'s own
        // operand order) into one consistent shape regardless of how the XML is written --
        // eq(y,add(x,3)), eq(add(x,3),y), and eq(y,add(3,x)) all reach buildCtrIntension as the
        // exact same tree (confirmed empirically: the compound add(...) node canonicalizes to the
        // left, the bare variable to the right), so one test covers all three source forms; a
        // previous version of this test file had three near-identical tests for them before that
        // was confirmed empirically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..9 </var>",
                "<intension> eq(y,add(x,3)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(BinaryOffsetConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "y")).isEqualTo(digitOf(a, "x") + 3);
        }
    }

    @Test void intensionBinaryOffsetWithLessThan_flipsOperatorCorrectly() throws IOException {
        // x < y+2 -- unlike eq/ne (see intensionBinaryOffsetWithEquals's own comment), the
        // canonizer never reorders an order-sensitive operator's own operands (doing so would also
        // require flipping the operator, which it doesn't do at this level), so the bare variable
        // genuinely stays on the left here -- BinaryOffsetConstraint's fixed "left + offset <op>
        // right" shape needs LT flipped to GT to rearrange onto y as left.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> lt(x,add(y,2)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(BinaryOffsetConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isLessThan(digitOf(a, "y") + 2);
        }
    }

    @Test void intensionBinaryOffsetWithLessOrEqual_flipsOperatorCorrectly() throws IOException {
        // x <= y+2 -- the bare variable is on the left, so LEQ flips to GEQ when rearranged.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> le(x,add(y,2)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(BinaryOffsetConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isLessThanOrEqualTo(digitOf(a, "y") + 2);
        }
    }

    @Test void intensionBinaryOffsetWithNotEqual_recognizedAsBinaryOffsetConstraint() throws IOException {
        // ne canonicalizes the same way eq does (see intensionBinaryOffsetWithEquals's own
        // comment): the compound add(...) node ends up on the left, the bare variable on the
        // right, so this hits recognizeBinaryRelation's rightVar-present branch directly with no
        // flip involved.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ne(x,add(y,2)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(BinaryOffsetConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isNotEqualTo(digitOf(a, "y") + 2);
        }
    }

    @Test void intensionAddOfNonVariableExpressionThenConstant_fallsBackToPredicateConstraint() throws IOException {
        // add(mul(x,2), 5): the second operand is a constant, but the first isn't a plain variable,
        // so the offset shape can't match even though a constant is present. (add(5,mul(x,2)),
        // constant written first, reaches buildCtrIntension as this exact same canonicalized tree --
        // see intensionBinaryOffsetWithEquals's own comment -- so it needs no separate test.)
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"z\"> 0..15 </var>",
                "<intension> eq(z,add(mul(x,2),5)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") * 2 + 5);
        }
    }

    @Test void intensionBinaryComparisonReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> lt(x,y) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x < y);
        }
    }

    @Test void intensionConjunctionOfNotEqualAndLessThan_fallsBackToPredicateConstraint() throws IOException {
        // and(ne(x,y), lt(y,z)): the root operator is AND, not a relational one, so
        // recognizeBinaryRelation never matches and this falls all the way through to
        // IntensionExpressionEvaluator -- unlike a bare top-level ne/lt (recognized directly as a
        // BinaryComparatorConstraint/BinaryOffsetConstraint), NE/LT nested inside a larger boolean
        // expression is the real, reachable shape that exercises applyOperator's own NE/LT cases.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..3 </var>",
                "<intension> and(ne(x,y),lt(y,z)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isNotEqualTo(digitOf(a, "y"));
            assertThat(digitOf(a, "y")).isLessThan(digitOf(a, "z"));
        }
    }

    @Test void intensionEqualsWithMultiplication_neitherSideMatchesAddShape_fallsBackToPredicateConstraint() throws IOException {
        // Right side (y) is a plain var, left side is mul(x,2) -- not add(...), so the offset
        // pattern can't match even though one side is a bare variable.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..9 </var>",
                "<intension> eq(mul(x,2),y) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "y")).isEqualTo(digitOf(a, "x") * 2);
        }
    }

    @Test void intensionAddOfVariableAndNestedExpression_fallsBackToPredicateConstraint() throws IOException {
        // add(x,mul(y,2)): xcsp3-tools' canonizer reorders add's own two operands by complexity,
        // same as it does for eq/ne's operands (see intensionBinaryOffsetWithEquals's own comment) --
        // the compound mul(y,2) canonicalizes to the front, the bare variable x to the back, so
        // asVariable(sons[0]) is absent (mul(...) isn't a plain variable) even though
        // asConstant(sons[1]) does find a leaf (just not a LONG one).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,mul(y,2))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") * 2);
        }
    }

    @Test void intensionAddOfTwoVariablesNoConstant_fallsBackToPredicateConstraint() throws IOException {
        // add(x,y) has two sons but neither is a constant, so it doesn't match the
        // "var + constant" offset shape even though the add itself is binary.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,y)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y"));
        }
    }

    @Test void intensionAddOfTwoNestedExpressions_neitherOperandIsALeaf_fallsBackToPredicateConstraint() throws IOException {
        // add(mul(x,2),mul(y,3)): both operands are compound expressions, so the canonizer's
        // complexity-based reordering has no simpler leaf to demote to the back -- sons[1] stays a
        // non-leaf MUL node, which is what exercises asConstant's own "not a leaf at all" branch
        // (distinct from intensionAddOfVariableAndNestedExpression's "leaf, but not LONG" case above).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..20 </var>",
                "<intension> eq(z,add(mul(x,2),mul(y,3))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") * 2 + digitOf(a, "y") * 3);
        }
    }

    @Test void intensionAddWithThreeOperands_fallsBackToPredicateConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,y,2)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") + 2);
        }
    }

    @Test void intensionNeitherSideIsBareVariable_fallsBackToPredicateConstraint() throws IOException {
        // Both sides are mul(...) expressions -- neither is a plain variable, and unlike
        // eq(add(x,1),add(y,2)) (which xcsp3-tools' own canonizer simplifies into an equivalent
        // add-based form BinaryOffsetConstraint would still recognize), multiplication has no
        // linear rewrite into that shape.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var>",
                "<intension> eq(mul(x,2),mul(y,3)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") * 2).isEqualTo(digitOf(a, "y") * 3);
        }
    }

    @Test void intensionChainedEquality_fallsBackToPredicateConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> eq(x,y,z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void intensionUnsupportedOperator_throwsWhenEvaluated() throws IOException {
        // buildCtrIntension only builds a lazy Predicate<Assignment> -- the unsupported operator
        // isn't detected until the predicate is actually evaluated against a candidate assignment
        // during search, not at parse time.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<intension> eq(min(x,y),0) </intension>");
        assertThatThrownBy(() -> solutions(instance.csp()))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // ---- unsupported construct falls through to the library's own default --------------------------------------------------

    @Test void completelyUnrecognisedConstruct_throwsRuntimeException() {
        // mdd (task #54) has no buildCtrMDD override at all, so this falls through to
        // XCallbacks2's own default (unimplementedCase), not UnsupportedXcsp3ConstraintException --
        // channel used to be this test's example, but it's now a recognised, mapped construct.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<mdd><list> x y </list><transitions> (r,0,n1)(n1,1,t) </transitions></mdd>"))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- Xcsp3Parser's checked-exception wrapping path ------------------------------------------------------------------------

    @Test void malformedXml_wrapsCheckedExceptionAsIOException() throws IOException {
        // A missing file surfaces as the library's own RuntimeException (Utilities.control), which
        // Xcsp3Parser passes through unwrapped -- only a genuine checked exception (e.g. a real XML
        // well-formedness error from the underlying SAX parser) exercises the IOException-wrapping path.
        Path file = tempDir.resolve("malformed.xml");
        Files.writeString(file, "<instance format=\"XCSP3\" type=\"CSP\"><variables><var id=\"x\"> 0..3 <</variables></instance>");
        assertThatThrownBy(() -> Xcsp3Parser.parse(file))
                .isInstanceOf(IOException.class);
    }
}
