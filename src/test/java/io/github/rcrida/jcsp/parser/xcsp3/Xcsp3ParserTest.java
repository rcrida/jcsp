package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
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

    // ---- allDifferent ---------------------------------------------------------------------------------

    @Test void allDifferent_buildsAllDiffConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<allDifferent> x y </allDifferent>");
        assertThat(solutions(instance.csp())).hasSize(2);
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

    @Test void countWithVariableTarget_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"k\"> 0..3 </var>",
                "<count><list> x y z </list><values> 1 </values><condition> (eq,k) </condition></count>"))
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

    @Test void lexWithMoreThanTwoLists_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 0..1 </var><var id=\"y1\"> 0..1 </var><var id=\"z1\"> 0..1 </var>",
                "<lex><list> x1 </list><list> y1 </list><list> z1 </list><operator> le </operator></lex>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
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

    @Test void objectiveNonSumType_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"maximum\"><list> x y </list></minimize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void objectiveSumWithNegativeCoefficient_throwsUnsupported() {
        // A negative coefficient (or negative-domain variable) means neither LinearObjective's
        // "unassigned contributes 0" convention nor buildSumObjective's own domain-max fill for
        // maximize is a sound lower bound, so this is rejected rather than silently mis-pruning.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<minimize type=\"sum\"><list> x y </list><coeffs> -1 2 </coeffs></minimize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void objectiveMaximizeSumWithNegativeDomain_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> -3..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> ge(x,2) </intension>",
                "<maximize type=\"sum\"><list> x y </list></maximize>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
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
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..3 </var>",
                "<instantiation><list> x </list><values> 2 </values></instantiation>"))
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
