package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.SquareVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.AbsoluteDifferenceVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.AndConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.nary.RelationLogicConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.constraints.unary.SquareConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryInSetConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
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

    private static String stringOf(Assignment assignment, String name) {
        return assignment.getValue(Variable.Factory.INSTANCE.<String>create(name)).orElseThrow();
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

    @Test void intensionXor_solutionsHaveExactlyOneTrue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var>",
                "<intension> xor(x,y) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(2);
        for (Assignment solution : found) {
            assertThat(digitOf(solution, "x") + digitOf(solution, "y")).isEqualTo(1);
        }
    }

    @Test void intensionIff_solutionsAllEqual() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var>",
                "<intension> iff(x,y) </intension>");
        for (Assignment solution : solutions(instance.csp())) {
            assertThat(digitOf(solution, "x")).isEqualTo(digitOf(solution, "y"));
        }
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    @Test void intensionIffOfGroundEqualities_routesThroughReifiedUnaryComparator() throws IOException {
        // Mario-easy-4.xml.lzma's own shape: iff(eq(s,i), eq(g,0)) -- neither operand is a plain
        // boolean variable, so this doesn't match intensionIff_solutionsAllEqual's bare-variable
        // case above; ChannelRecognizer's full-dispatch operand resolution reaches
        // GroundRelationRecognizer for each side, routing it through a pair of ReifiedConstraints
        // instead of the generic (unpropagated) PredicateConstraint.
        // x==1 iff y==2, x,y in {0,1,2}: satisfying pairs are (0,0),(0,1),(1,2),(2,0),(2,1) --
        // x!=1 (2 choices) paired with each y!=2 (2 choices) = 4, plus x==1,y==2 = 1, total 5.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<intension> iff(eq(x,1),eq(y,2)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x == 1).as("x=%d, y=%d", x, y).isEqualTo(y == 2);
        }
        assertThat(solutions).hasSize(5);
    }

    @Test void intensionIffOfGroundEqualities_neOperandRecognized() throws IOException {
        // ne(x,1) iff eq(y,2), x,y in {0,1,2}: confirms recognizeGroundRelation's NEQ handling, not
        // just EQ. Hand-enumerated: x=0 (x!=1 true) needs y=2 -> (0,2); x=1 (x!=1 false) needs
        // y!=2 -> (1,0),(1,1); x=2 (x!=1 true) needs y=2 -> (2,2). 4 solutions total.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<intension> iff(ne(x,1),eq(y,2)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x != 1).as("x=%d, y=%d", x, y).isEqualTo(y == 2);
        }
        assertThat(solutions).hasSize(4);
    }

    @Test void intensionIffOneSideProperBinaryRelation_recognizedTogetherWithGroundEquality() throws IOException {
        // lt(x,y) iff eq(z,0): confirms recognizeRelation's reuse of recognizeBinaryRelation for a
        // proper two-variable relation on one side, not just ground equalities on both sides. For
        // every one of the 9 (x,y) pairs exactly one z in {0,1} satisfies the biconditional -- 9
        // solutions total.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..1 </var>",
                "<intension> iff(lt(x,y),eq(z,0)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            assertThat(x < y).as("x=%d, y=%d, z=%d", x, y, z).isEqualTo(z == 0);
        }
        assertThat(solutions).hasSize(9);
    }

    @Test void intensionIffReified_indicatorTracksWholeBiconditionalTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> iff(eq(x,1),eq(y,2)) </intension>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo((x == 1) == (y == 2));
        }
        assertThat(solutions(instance.csp())).hasSize(9); // every (x,y) combo, b determined each time
    }

    @Test void intensionIffOneSideCompoundSum_recognizedViaFullDispatch() throws IOException {
        // eq(add(y,z),3) has a compound add(y,z) expression as its left operand -- neither
        // recognizeBinaryRelation (needs var op var or var op (var+const), not var+var op const)
        // nor recognizeGroundRelation (needs a bare variable on at least one side) can match it
        // directly, but ChannelRecognizer's operand resolution now dispatches through the FULL
        // recognizer chain (not IffRecognizer's old narrow binary/ground pair), so SumOrLinearRecognizer
        // catches it instead -- genuinely improved recognition, not a regression to work around.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<intension> iff(eq(x,1),eq(add(y,z),3)) </intension>");
        assertThat(instance.csp().getConstraints())
                .anyMatch(c -> c instanceof ReifiedConstraint rc && rc.getBody() instanceof SumBoundConstraint<?>);
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            assertThat(x == 1).as("x=%d, y=%d, z=%d", x, y, z).isEqualTo(y + z == 3);
        }
        assertThat(solutions).hasSize(16); // (x,y,z) in {0..2}^3: verified by brute-force enumeration
    }

    @Test void intensionIffLeftSideCompoundSum_recognizedViaFullDispatch() throws IOException {
        // Mirrors intensionIffOneSideCompoundSum_recognizedViaFullDispatch but with the compound
        // operand on the LEFT instead of the right -- eq(add(x,y),1) resolves via SumOrLinearRecognizer
        // reached through full dispatch on tree.sons[0] specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"w\"> 0..1 </var>",
                "<intension> iff(eq(add(x,y),1),eq(w,1)) </intension>");
        assertThat(instance.csp().getConstraints())
                .anyMatch(c -> c instanceof ReifiedConstraint rc && rc.getBody() instanceof SumBoundConstraint<?>);
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int w = digitOf(a, "w");
            assertThat(x + y == 1).as("x=%d, y=%d, w=%d", x, y, w).isEqualTo(w == 1);
        }
        assertThat(solutions).hasSize(9); // (x,y,w) in {0..2}x{0..2}x{0..1}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandOrderingOperator_secondOperandNotConstant_falls_backToGeneric() throws IOException {
        // lt(x,add(y,z)) as the iff's left operand: recognizeBinaryRelation declines (add(y,z) is
        // var+var, not var+const), and recognizeGroundRelation's own operator guard now *accepts*
        // LT (unlike before this method also handled ordering operators) but still declines here
        // since add(y,z) isn't a bare constant either -- exercises the leftVar-present-but-
        // asConstant-absent path specifically (distinct from the sons.length disjunct exercised by
        // the n-ary iff test below).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"w\"> 0..1 </var>",
                "<intension> iff(lt(x,add(y,z)),eq(w,1)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            int w = digitOf(a, "w");
            assertThat(x < y + z).as("x=%d, y=%d, z=%d, w=%d", x, y, z, w).isEqualTo(w == 1);
        }
        assertThat(solutions).hasSize(27); // (x,y,z,w) in {0..2}^3x{0..1}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandOrderingOperator_variableFirst_recognizedViaUnaryComparator() throws IOException {
        // le(x,5) iff eq(y,1): variable-first ordering literal (e.g. as lt(x,6) canonicalizes to)
        // -- exercises recognizeGroundRelation's leftVar.isPresent() branch with an ordering
        // operator, routing through UnaryComparatorConstraint directly (no flip needed).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..8 </var><var id=\"y\"> 0..2 </var>",
                "<intension> iff(le(x,5),eq(y,1)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x <= 5).as("x=%d, y=%d", x, y).isEqualTo(y == 1);
        }
        assertThat(solutions).hasSize(12); // (x,y) in {0..8}x{0..2}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandOrderingOperator_constantFirst_recognizedViaFlippedUnaryComparator() throws IOException {
        // le(0,x) iff eq(y,1): the real corpus shape (e.g. MagicSequence-style
        // iff(le(0,p[i]),le(0,s[i])) clauses) -- the constant stays first since xcsp3-tools'
        // canonizer doesn't reorder a genuine le/lt the way it reorders eq/ne. Recognized via
        // recognizeGroundRelation's rightVar.isPresent() branch, reinterpreted via flip(LEQ)=GEQ
        // into UnaryComparatorConstraint.of(x, GEQ, 0).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..5 </var><var id=\"y\"> 0..2 </var>",
                "<intension> iff(le(0,x),eq(y,1)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x >= 0).as("x=%d, y=%d", x, y).isEqualTo(y == 1);
        }
        assertThat(solutions).hasSize(12); // (x,y) in {-3..5}x{0..2}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandConstantFirstCompoundSum_recognizedViaFullDispatch() throws IOException {
        // le(add(a,b),x) iff eq(y,1): sons[0]=add(a,b) is neither a variable nor a constant, so
        // ChannelRecognizer's own bare-variable fallback declines for it -- but full dispatch on
        // that operand reaches SumOrLinearRecognizer (le(add(a,b),x) recognizes as a
        // SumVariableConstraint, target x a plain variable), where IffRecognizer's old narrow
        // binary/ground pair would have declined outright.
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 0..2 </var><var id=\"b\"> 0..2 </var><var id=\"x\"> 0..4 </var><var id=\"y\"> 0..1 </var>",
                "<intension> iff(le(add(a,b),x),eq(y,1)) </intension>");
        assertThat(instance.csp().getConstraints())
                .anyMatch(c -> c instanceof ReifiedConstraint rc && rc.getBody() instanceof SumVariableConstraint<?>);
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment s : solutions) {
            int a = digitOf(s, "a");
            int b = digitOf(s, "b");
            int x = digitOf(s, "x");
            int y = digitOf(s, "y");
            assertThat(a + b <= x).as("a=%d, b=%d, x=%d, y=%d", a, b, x, y).isEqualTo(y == 1);
        }
        assertThat(solutions).hasSize(45); // (a,b,x,y) in {0..2}x{0..2}x{0..4}x{0..1}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandBareVariable_recognizedViaBareVariableFallback() throws IOException {
        // eq(x,1) iff y: a bare boolean variable as the SECOND operand. Unlike a compound
        // sub-expression (add(...), etc.), a bare variable leaf doesn't get reordered ahead of a
        // relational EQ node by xcsp3-tools' complexity-based canonizer (confirmed via a throwaway
        // probe), so this survives as tree.sons = [eq(x,1), y]. IffRecognizer's own narrow
        // recognizeRelation never accepted a bare-variable operand at all, so this used to fall all
        // the way back to the generic PredicateConstraint -- ChannelRecognizer's own bare-variable
        // fallback (shared with its eq/ne channel shape) now resolves it directly via
        // booleanIndicatorFor, with no reification of a separate relation needed for that side.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0 1 </var>",
                "<intension> iff(eq(x,1),y) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x == 1).as("x=%d, y=%d", x, y).isEqualTo(y == 1);
        }
        assertThat(solutions).hasSize(3); // (x,y) in {0..2}x{0,1}: verified by brute-force enumeration
    }

    @Test void intensionIffOperandNaryEquality_recognizeGroundRelationSonsLengthGuardDeclines() throws IOException {
        // eq(x,y,z) (n-ary equality, 3 operands) as the iff's left operand: recognizeBinaryRelation
        // and recognizeGroundRelation both decline outright (their own guards require exactly 2
        // sons), but ChannelRecognizer's own operand resolution dispatches through the full
        // recognizer chain, where NaryEqualityRecognizer now resolves this shape into an
        // AndConstraint of its own, reified into a fresh indicator -- no longer falls through to
        // ChannelRecognizer's bare-variable asVariable fallback the way an unrecognizable operand
        // would.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"w\"> 0..1 </var>",
                "<intension> iff(eq(x,y,z),eq(w,1)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            int w = digitOf(a, "w");
            assertThat(x == y && y == z).as("x=%d, y=%d, z=%d, w=%d", x, y, z, w).isEqualTo(w == 1);
        }
        assertThat(solutions).hasSize(27); // (x,y,z,w) in {0..2}^3x{0..1}: verified by brute-force enumeration
    }

    @Test void intensionIffThreeOperands_sonsLengthGuardFallsBackToGeneric() throws IOException {
        // XCSP3's iff is a generalized n-ary biconditional (IntensionExpressionEvaluator's own IFF
        // case is allEqual(operands), not just a pairwise check) -- confirmed via a throwaway probe
        // that xcsp3-tools accepts a 3-operand iff node. ChannelRecognizer's own tree.sons.length
        // != 2 guard only ever matches exactly two operands (a left and a right indicator to
        // compare), so a 3-operand iff must decline and fall through to the pre-existing generic
        // path, which already handles n-ary IFF correctly.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> iff(eq(x,1),eq(y,2),eq(z,3)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            // n-ary iff is allEqual, not a pairwise chain: all three booleans must match each other.
            boolean p = x == 1, q = y == 2, r = z == 3;
            assertThat(p == q && q == r).as("x=%d, y=%d, z=%d", x, y, z).isTrue();
        }
        assertThat(solutions).hasSize(28); // (x,y,z) in {0..3}^3: verified by brute-force enumeration
    }

    @Test void intensionBooleanProductChannel_routesThroughMinVariableConstraint() throws IOException {
        // x,y in {0,1}: eq(mul(x,y),t) is exactly boolean AND -- xcsp3-tools' canonizer always puts
        // mul(...) first, t second (confirmed empirically against a real BIBD instance's own
        // channeling constraints), so this exercises recognizeBooleanProductChannel rather than
        // falling back to PredicateConstraint, which has no propagation at all.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var><var id=\"t\"> 0 1 </var>",
                "<intension> eq(mul(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(MinVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(4);
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionBooleanProductChannel_secondOperandNonBooleanDomain_fallsThroughToProductOfPair() throws IOException {
        // y's domain is 0..2, not confined to {0,1} -- a*b == min(a,b) doesn't hold in general there
        // (e.g. min(2,3)=2 but 2*3=6), so recognizeBooleanProductChannel must decline even though the
        // tree shape (eq(mul(var,var),var)) matches exactly. Falls through to the more general
        // recognizeProductOfPair instead of all the way to PredicateConstraint, since the shape still
        // matches "mul(var,var) op var".
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0..2 </var><var id=\"t\"> 0..2 </var>",
                "<intension> eq(mul(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionBooleanProductChannel_firstOperandNonBooleanDomain_fallsThroughToProductOfPair() throws IOException {
        // Same as above with the roles reversed -- x (the mul node's first operand) is the
        // non-boolean side this time, exercising isBooleanDomain(a) rather than isBooleanDomain(b).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0 1 </var><var id=\"t\"> 0..2 </var>",
                "<intension> eq(mul(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionBooleanProductChannel_operandDomainNotStartingAtZero_fallsThroughToProductOfPair() throws IOException {
        // x's domain is {1,2} -- bounds[0] != 0, so isBooleanDomain's first comparison short-circuits
        // to false without needing to check the upper bound too (distinct from the 0..2 case above,
        // whose lower bound is 0 but whose upper bound is what fails).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1 2 </var><var id=\"y\"> 0 1 </var><var id=\"t\"> 0..2 </var>",
                "<intension> eq(mul(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionBooleanProductChannel_mulOperandIsCompoundExpression_fallsBackToPredicateConstraint() throws IOException {
        // add(x,1) canonicalizes to mul's first operand ahead of the bare variable y (same
        // complexity-based reordering as everywhere else), so asVariable(mulNode.sons[0]) finds a
        // compound node rather than a plain variable -- exercises a.isEmpty() specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0 1 </var><var id=\"t\"> 0..4 </var>",
                "<intension> eq(mul(add(x,1),y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo((digitOf(a, "x") + 1) * digitOf(a, "y"));
        }
    }

    @Test void intensionBooleanProductChannel_threeOperandMul_routesThroughProductVariableConstraintInstead() throws IOException {
        // mul(x,y,z) canonicalizes with the compound node first (same complexity-based reordering
        // as the two-operand case), but its arity is 3, not the 2 BooleanProductChannelRecognizer
        // requires -- exercises that recognizer's own sons.length != 2 rejection specifically. It
        // no longer falls all the way to PredicateConstraint, though: ProductRecognizer (registered
        // right after) now handles mul of any arity two or more, so this three-distinct-factor case
        // is picked up there instead -- with no propagation for a {0,1}-domain factor (per
        // ProductConstraint/ProductVariableConstraint's own strictly-positive-minimum restriction),
        // same as PredicateConstraint would have given, but as a real, correctly-typed constraint
        // object rather than an opaque predicate.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var><var id=\"z\"> 0 1 </var><var id=\"t\"> 0..1 </var>",
                "<intension> eq(mul(x,y,z),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y") * digitOf(a, "z"));
        }
    }

    // ---- intension mul(...) recognition (ProductVariableConstraint/ProductConstraint) -------------

    @Test void intensionProductOfPair_variableTarget_routesThroughProductVariableConstraint() throws IOException {
        // x,y not confined to {0,1} -- recognizeBooleanProductChannel's own identity doesn't apply
        // here -- and the target is a plain variable: the general-domain generalization this method
        // exists for.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..4 </var><var id=\"y\"> 1..4 </var><var id=\"t\"> 1..16 </var>",
                "<intension> eq(mul(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionProductOfThreeFactors_routesThroughProductVariableConstraint() throws IOException {
        // mul(x,y,z) with three distinct non-boolean factors: ProductRecognizer's own arity
        // generalization (an earlier version, ProductOfPairRecognizer, required exactly two).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..3 </var><var id=\"t\"> 1..27 </var>",
                "<intension> eq(mul(x,y,z),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(digitOf(a, "x") * digitOf(a, "y") * digitOf(a, "z"));
        }
    }

    @Test void intensionProductOfPair_constantTarget_routesThroughProductConstraint() throws IOException {
        // le(mul(x,y),12): target is a fixed constant, not a variable -- exercises the
        // asConstant(tree.sons[1]) branch, and LEQ instead of EQ.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..4 </var><var id=\"y\"> 1..4 </var>",
                "<intension> le(mul(x,y),12) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") * digitOf(a, "y")).isLessThanOrEqualTo(12);
        }
    }

    @Test void intensionProductOfPair_neOperator_declinedFallsBackToPredicateConstraint() throws IOException {
        // ne(mul(x,y),z): NEQ isn't one of ProductVariableConstraint/ProductConstraint's own
        // propagating operators (EQ/LEQ/GEQ, mirroring their own PROPAGATING_OPERATORS), so
        // recognition must decline even though the tree shape otherwise matches exactly.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..9 </var>",
                "<intension> ne(mul(x,y),z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "z")).isNotEqualTo(digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionProductOfPairLeqOperand_mulAsSecondOperand_routesThroughProductVariableConstraint() throws IOException {
        // le(x,mul(y,z)): unlike eq (whose canonizer always reorders a compound operand ahead of a
        // bare variable, confirmed empirically), le doesn't get complexity-reordered -- swapping its
        // operands would require also flipping the operator, which the canonizer only does to
        // eliminate ge/gt, not for plain complexity ordering -- so tree.sons[0] genuinely stays the
        // bare variable x here (tree.sons[1] is mul(y,z)). ProductRecognizer now checks sons[1] for
        // a mul(...) too (added for the ge/gt-swap case, e.g. ge(mul(...),c) -> le(c,mul(...))), and
        // that same check also picks up this genuinely-distinct le-with-leaf-first shape, correctly
        // flipping the operator (LEQ -> GEQ) since x<=y*z is the same relation as y*z>=x. This tree
        // also still exercises recognizeDistanceOfPair's own leaf-check further down the chain (it
        // still declines here, just no longer reaching the generic PredicateConstraint fallback
        // afterward -- ProductRecognizer, registered after it, now claims the tree instead).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..20 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..5 </var>",
                "<intension> le(x,mul(y,z)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x")).isLessThanOrEqualTo(digitOf(a, "y") * digitOf(a, "z"));
        }
    }

    @Test void intensionProductOfPairNaryEquality_sonsLengthGuardDeclines() throws IOException {
        // eq(mul(x,y),z,w): a 3-operand top-level eq (XCSP3's eq is a generalized n-ary allEqual,
        // the same generalization intensionIffOperandNaryEquality exercises for
        // recognizeGroundRelation) -- exercises this method's own tree.sons.length != 2 rejection
        // specifically, distinct from mulNode.sons.length (the boolean-channel siblings' own
        // threeOperandMul test).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var>"
                        + "<var id=\"z\"> 1..9 </var><var id=\"w\"> 1..9 </var>",
                "<intension> eq(mul(x,y),z,w) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int product = digitOf(a, "x") * digitOf(a, "y");
            assertThat(product == digitOf(a, "z") && digitOf(a, "z") == digitOf(a, "w")).isTrue();
        }
    }

    @Test void intensionProductOfPairSecondMulOperandNotVariable_declines() throws IOException {
        // eq(mul(x,add(y,1)),z): the mul node's second operand is itself a compound expression, not
        // a bare variable -- exercises asVariable(mulNode.sons[1]).isEmpty() specifically, distinct
        // from the first-operand case recognizeBooleanProductChannel's own
        // mulOperandIsCompoundExpression test already covers.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..12 </var>",
                "<intension> eq(mul(x,add(y,1)),z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") * (digitOf(a, "y") + 1));
        }
    }

    @Test void intensionProductOfPairSelfProduct_routesThroughSquareVariableConstraint() throws IOException {
        // eq(mul(x,x),y) -- a genuine self-product ("x squared"). ProductVariableConstraint#of
        // takes a Set<Variable<N>>, which can't represent one variable used twice as a factor -- a
        // real gap found via the full XCSP3 competition corpus (LowAutocorrelation-015.xml.lzma),
        // where an earlier, unguarded version of this method threw IllegalArgumentException:
        // duplicate element from Set.of(a, a). Rather than declining, ProductRecognizer now routes
        // a two-factor self-product to the dedicated SquareVariableConstraint instead.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..4 </var><var id=\"y\"> 1..16 </var>",
                "<intension> eq(mul(x,x),y) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SquareVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "y")).isEqualTo(digitOf(a, "x") * digitOf(a, "x"));
        }
    }

    @Test void intensionProductOfPairSelfProduct_geOperatorSwapsMulToSecondOperand_routesThroughSquareConstraint() throws IOException {
        // ge(mul(x,x),4) is rewritten by xcsp3-tools' own canonizer into le(4,mul(x,x)) -- ge/gt are
        // always eliminated via le/lt with operands swapped, and that swap moves the compound mul
        // side to tree.sons[1] regardless of its complexity (confirmed via a direct probe against a
        // real parsed tree: LE with son0=LONG, son1=MUL). ProductRecognizer must check sons[1] for
        // mul(...) too, flipping the operator back, or GEQ would be silently unreachable for any
        // source file that used ge/gt against a mul(...) operand.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -5..5 </var>",
                "<intension> ge(mul(x,x),4) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SquareConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") * digitOf(a, "x")).isGreaterThanOrEqualTo(4);
        }
    }

    @Test void intensionProductOfPair_geOperatorSwapsMulToSecondOperand_routesThroughProductConstraint() throws IOException {
        // Same GE-swap phenomenon as the self-product case above, but for a genuine two-distinct-
        // factor product: ge(mul(x,y),6) canonicalizes to le(6,mul(x,y)), moving mul(...) to
        // tree.sons[1]. Confirms the sons[1] check's general (non-self-product) path, not just
        // recognizeSelfProduct, correctly flips the operator back to GEQ.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..4 </var><var id=\"y\"> 1..4 </var>",
                "<intension> ge(mul(x,y),6) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(ProductConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") * digitOf(a, "y")).isGreaterThanOrEqualTo(6);
        }
    }

    @Test void intensionProductOfThreeFactorsWithDuplicate_fallsBackToPredicateConstraint() throws IOException {
        // eq(mul(x,x,y),z): a genuine duplicate factor at arity 3 (not the two-factor self-product
        // recognizeSelfProduct handles) -- exercises the general factor loop's own
        // !factors.add(factor.get()) branch specifically, distinct from mul(x,x)'s own dedicated
        // Square routing (only tried for mulNode.sons.length == 2). ProductVariableConstraint's
        // Set<Variable<N>> factor representation can't hold x twice here either, so recognition must
        // decline the same way the two-factor case originally did before Square* existed.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..27 </var>",
                "<intension> eq(mul(x,x,y),z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") * digitOf(a, "x") * digitOf(a, "y"));
        }
    }

    @Test void intensionProductOfPairTargetNeitherVariableNorConstant_declines() throws IOException {
        // eq(mul(x,y),add(z,w)): mul(x,y)'s target side is itself a compound expression -- neither a
        // bare variable nor a constant -- exercises asConstant(tree.sons[1]).isEmpty() after
        // asVariable(tree.sons[1]) has already failed, so ProductRecognizer declines. Both
        // SumOrLinearRecognizer (mul(x,y) as add(z,w)'s target isn't resolvable, mul being
        // unsupported by resolveVariable) and BinaryRelationRecognizer's own final fallback (which
        // does resolve add(z,w) into a real sum auxiliary along the way, via resolveVariable, before
        // failing on mul(x,y) and declining overall) also decline -- the auxiliary is a harmless
        // orphan (resolveVariable's own documented, accepted tradeoff), checked via anyMatch rather
        // than the fragile iterator().next() (unspecified Set order, now more than one element).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var>"
                        + "<var id=\"z\"> 1..4 </var><var id=\"w\"> 1..4 </var>",
                "<intension> eq(mul(x,y),add(z,w)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") * digitOf(a, "y")).isEqualTo(digitOf(a, "z") + digitOf(a, "w"));
        }
    }

    @Test void intensionProductOfPair_reified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"t\"> 1..9 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(mul(x,y),t) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x"), y = digitOf(a, "y"), t = digitOf(a, "t"), b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, t=%d, b=%d", x, y, t, b).isEqualTo(t == x * y);
        }
    }

    // ---- intension dist(...) recognition (AbsoluteDifferenceVariableConstraint/AbsoluteDifferenceConstraint) -------

    @Test void intensionDistanceOfPair_variableTarget_routesThroughAbsoluteDifferenceVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..5 </var><var id=\"y\"> 1..5 </var><var id=\"t\"> 0..4 </var>",
                "<intension> eq(dist(x,y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AbsoluteDifferenceVariableConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(Math.abs(digitOf(a, "x") - digitOf(a, "y")));
        }
    }

    @Test void intensionDistanceOfPair_constantTarget_routesThroughAbsoluteDifferenceConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..5 </var><var id=\"y\"> 1..5 </var>",
                "<intension> ne(dist(x,y),3) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AbsoluteDifferenceConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs(digitOf(a, "x") - digitOf(a, "y"))).isNotEqualTo(3);
        }
    }

    @Test void intensionDistanceOfPair_operandNotVariable_fallsBackToPredicateConstraint() throws IOException {
        // dist(add(x,1),y): the dist node's first operand is itself a compound expression, not a
        // bare variable -- exercises asVariable(distNode.sons[0]).isEmpty() specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..5 </var><var id=\"t\"> 0..4 </var>",
                "<intension> eq(dist(add(x,1),y),t) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "t")).isEqualTo(Math.abs(digitOf(a, "x") + 1 - digitOf(a, "y")));
        }
    }

    @Test void intensionDistancePairComparison_routesThroughBinaryComparatorConstraintOverAuxiliaries() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..4 </var><var id=\"b\"> 1..4 </var><var id=\"c\"> 1..4 </var><var id=\"d\"> 1..4 </var>",
                "<intension> ne(dist(a,b),dist(c,d)) </intension>");
        // Two auxiliary-linking AbsoluteDifferenceVariableConstraints plus the comparator over them.
        assertThat(instance.csp().getConstraints()).hasSize(3);
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof AbsoluteDifferenceVariableConstraint<?>).hasSize(2);
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int distAb = Math.abs(digitOf(a, "a") - digitOf(a, "b"));
            int distCd = Math.abs(digitOf(a, "c") - digitOf(a, "d"));
            assertThat(distAb).isNotEqualTo(distCd);
        }
    }

    @Test void intensionDistancePairComparison_constantDistOperand_resolvesViaResolveVariable() throws IOException {
        // ne(dist(a,5),dist(c,d)): the left dist node's *second* operand is a bare constant, not a
        // variable -- since asDistancePairOperand now resolves each dist operand via
        // resolveVariable (not the narrower asVariable), a bare constant resolves too (via
        // constantVariable), so this recognizes exactly like the all-variable case rather than
        // falling back to PredicateConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..3 </var><var id=\"c\"> 1..3 </var><var id=\"d\"> 1..3 </var>",
                "<intension> ne(dist(a,5),dist(c,d)) </intension>");
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof AbsoluteDifferenceVariableConstraint<?>).hasSize(2);
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int distLeft = Math.abs(digitOf(a, "a") - 5);
            int distRight = Math.abs(digitOf(a, "c") - digitOf(a, "d"));
            assertThat(distLeft).isNotEqualTo(distRight);
        }
    }

    @Test void intensionDistancePairComparison_secondDistOperandUnsupportedCompound_fallsBackToPredicateConstraint() throws IOException {
        // ne(dist(div(x,6),div(y,z)),dist(e,f)): the left dist node's *second* operand is a div
        // with a variable divisor, unresolvable (resolveVariable's divisor guard requires a
        // constant) -- while the first (div(x,6)) resolves fine. xcsp3-tools' canonizer promotes a
        // compound ahead of a bare leaf (confirmed elsewhere in this file), but leaves two
        // same-operator-type compounds (div vs div here) in their written order (confirmed via a
        // real probe -- unlike add(c,1), which the canonizer promotes ahead of div regardless of
        // which side it's written on), so this genuinely exercises asDistancePairOperand's
        // resolveVariable(distNode.sons[1]).isEmpty() branch specifically, distinct from the
        // first-operand rejection every other "operand not variable" test in this file exercises.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..6 </var><var id=\"y\"> 1..6 </var><var id=\"z\"> 1..3 </var>"
                        + "<var id=\"e\"> 1..3 </var><var id=\"f\"> 1..3 </var>",
                "<intension> ne(dist(div(x,6),div(y,z)),dist(e,f)) </intension>");
        // Not iterator().next(): div(x,6)'s own auxiliary-linking LinearVariableConstraint is added
        // (as a side effect of successfully resolving the first dist operand) before the top-level
        // PredicateConstraint, so it iterates first.
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int distLeft = Math.abs(digitOf(a, "x") / 6 - digitOf(a, "y") / digitOf(a, "z"));
            int distRight = Math.abs(digitOf(a, "e") - digitOf(a, "f"));
            assertThat(distLeft).isNotEqualTo(distRight);
        }
    }

    @Test void resolveVariableDiv_secondDistOperandUnsupportedCompound_fallsBackToPredicateConstraint() throws IOException {
        // eq(dist(div(x,6),div(y,z)),1): the *second* dist operand (div(y,z), variable divisor)
        // fails to resolve while the first (div(x,6)) succeeds -- exercises
        // recognizeDistanceOfPair's own resolveVariable(distNode.sons[1]).isEmpty() branch, the
        // dist-vs-target sibling of the pair-comparison test above.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..6 </var><var id=\"y\"> 1..6 </var><var id=\"z\"> 1..3 </var>",
                "<intension> eq(dist(div(x,6),div(y,z)),1) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs(digitOf(a, "x") / 6 - digitOf(a, "y") / digitOf(a, "z"))).isEqualTo(1);
        }
    }

    @Test void intensionDistanceThreeOperands_fallsBackToPredicateConstraint() throws IOException {
        // dist(a,b,c): a 3-operand dist node -- exercises both asDistancePairOperand's and
        // recognizeDistanceOfPair's own distNode.sons.length != 2 rejection (this same tree reaches
        // both, in sequence, since recognizeDistancePairComparison is tried first on every tree).
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..3 </var><var id=\"b\"> 1..3 </var><var id=\"c\"> 1..3 </var>",
                "<intension> eq(dist(a,b,c),1) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
    }

    @Test void intensionDistancePairComparison_repeatedSubexpression_reusesSameAuxiliary() throws IOException {
        // ne(dist(a,b),dist(a,c)) and ne(dist(a,b),dist(c,d)): dist(a,b) recurs across both
        // constraints -- should share one auxiliary/link constraint rather than building a
        // redundant copy per occurrence (the same amortization shiftVariable/constantVariable
        // already give). Three distinct dist(...) sub-expressions overall (dist(a,b), dist(a,c),
        // dist(c,d)) -> exactly 3 AbsoluteDifferenceVariableConstraint objects, not 4.
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..4 </var><var id=\"b\"> 1..4 </var><var id=\"c\"> 1..4 </var><var id=\"d\"> 1..4 </var>",
                "<intension> ne(dist(a,b),dist(a,c)) </intension>"
                        + "<intension> ne(dist(a,b),dist(c,d)) </intension>");
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof AbsoluteDifferenceVariableConstraint<?>).hasSize(3);
    }

    @Test void intensionDistancePairComparison_rightOperandNotDist_fallsBackToPredicateConstraint() throws IOException {
        // ne(dist(a,b),add(c,d)) canonicalizes to ne(add(c,d),dist(a,b)) (add first) -- neither
        // recognizeDistancePairComparison (needs dist on both sides) nor recognizeDistanceOfPair
        // (needs sons[0] to literally be dist(...), which it no longer is post-canonicalization)
        // matches, so it falls all the way to PredicateConstraint. BinaryRelationRecognizer's own
        // final fallback does resolve add(c,d) into a real sum auxiliary along the way (via
        // resolveVariable) before failing on dist(a,b) and declining overall -- a harmless orphaned
        // auxiliary/linking-constraint (resolveVariable's own documented, accepted tradeoff), so the
        // PredicateConstraint that actually governs this relation is checked via anyMatch rather
        // than the fragile iterator().next() (unspecified Set order, now more than one element).
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..3 </var><var id=\"b\"> 1..3 </var><var id=\"c\"> 1..3 </var><var id=\"d\"> 1..3 </var>",
                "<intension> ne(dist(a,b),add(c,d)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs(digitOf(a, "a") - digitOf(a, "b"))).isNotEqualTo(digitOf(a, "c") + digitOf(a, "d"));
        }
    }

    @Test void intensionDistanceOfPair_reified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"t\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(dist(x,y),t) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x"), y = digitOf(a, "y"), t = digitOf(a, "t"), b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, t=%d, b=%d", x, y, t, b).isEqualTo(t == Math.abs(x - y));
        }
    }

    @Test void intensionDistancePairComparison_reified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 1..3 </var><var id=\"b\"> 1..3 </var><var id=\"c\"> 1..3 </var>"
                        + "<var id=\"d\"> 1..3 </var><var id=\"r\"> 0..1 </var>",
                "<intension reifiedBy=\"r\"> ne(dist(a,b),dist(c,d)) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment asg : found) {
            int distAb = Math.abs(digitOf(asg, "a") - digitOf(asg, "b"));
            int distCd = Math.abs(digitOf(asg, "c") - digitOf(asg, "d"));
            int r = digitOf(asg, "r");
            assertThat(r == 1).as("distAb=%d, distCd=%d, r=%d", distAb, distCd, r).isEqualTo(distAb != distCd);
        }
    }

    // ---- intension or(...) recognition (RelationLogicConstraint) ----------------------------------------------------

    @Test void intensionOrOfValueLiterals_routesThroughRelationLogicConstraint() throws IOException {
        // or(ne(x,1), eq(y,2)): both children are bare eq/ne-vs-constant literals -- exercises the
        // mixed EQ/NEQ case rather than falling back to PredicateConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<intension> or(ne(x,1),eq(y,2)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") != 1 || digitOf(a, "y") == 2).isTrue();
        }
    }

    @Test void intensionOrOfVariableLiterals_routesThroughRelationLogicConstraint() throws IOException {
        // or(ne(x,y), eq(z,w)): both children are bare eq/ne-vs-variable literals.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"w\"> 0..2 </var>",
                "<intension> or(ne(x,y),eq(z,w)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") != digitOf(a, "y") || digitOf(a, "z") == digitOf(a, "w")).isTrue();
        }
    }

    @Test void intensionOrMixedValueAndVariableLiteral_routesThroughRelationLogicConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<intension> or(eq(x,1),ne(y,z)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") == 1 || digitOf(a, "y") != digitOf(a, "z")).isTrue();
        }
    }

    @Test void intensionOrLeqValueLiterals_routesThroughRelationLogicConstraint() throws IOException {
        // or(le(x,1), le(y,1)): both children are bare le-vs-constant literals -- the shape
        // RoomMate-sr0050-int.xml.lzma's 2,450 preference-ranking clauses actually take.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var>",
                "<intension> or(le(x,1),le(y,1)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") <= 1 || digitOf(a, "y") <= 1).isTrue();
        }
    }

    @Test void intensionOrLtVariableLiterals_routesThroughRelationLogicConstraint() throws IOException {
        // or(lt(p,q), eq(z,1)): lt survives uncanonicalized between two plain variables (only a
        // variable-vs-constant lt gets folded into le by the canonizer).
        Xcsp3Instance instance = parseXml(
                "<var id=\"p\"> 0..3 </var><var id=\"q\"> 0..3 </var><var id=\"z\"> 0..1 </var>",
                "<intension> or(lt(p,q),eq(z,1)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "p") < digitOf(a, "q") || digitOf(a, "z") == 1).isTrue();
        }
    }

    @Test void intensionOrLeqConstantFirstLiteral_recognizedViaFlippedValueLiteral() throws IOException {
        // or(le(0,x), eq(y,1)): a genuine constant-first le literal (the real MagicSequence-style
        // shape GroundRelationRecognizer's own doc describes, e.g. iff(le(0,p[i]),le(0,s[i]))) --
        // recognizeLiteral now matches this directly (flipping LEQ to GEQ), not just the
        // variable-first form.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..5 </var><var id=\"y\"> 0..1 </var>",
                "<intension> or(le(0,x),eq(y,1)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") >= 0 || digitOf(a, "y") == 1).isTrue();
        }
    }

    @Test void intensionOrGeOperand_recognizesViaCheaperLiteralPath() throws IOException {
        // or(ge(x,2), eq(y,1)): ge(x,2) canonicalizes to le(2,x) -- constant first, variable second.
        // recognizeLiteral now checks both operand orders (mirroring GroundRelationRecognizer's own
        // dual-order check), so the cheaper 2-literal RelationLogicConstraint path recognizes this
        // directly -- previously (when recognizeLiteral only checked variable-first) this fell
        // through to the general N-ary OR fallback (AtLeastNConstraint over two reified indicators),
        // which still worked but needed two extra indicator variables neither operand actually needs.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..1 </var>",
                "<intension> or(ge(x,2),eq(y,1)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") >= 2 || digitOf(a, "y") == 1).isTrue();
        }
    }

    @Test void intensionOrNestedChild_recognizesViaResolveConstraintsRecursion() throws IOException {
        // or(and(eq(x,1),eq(y,2)), eq(z,3)): the left child is a compound and(...) -- previously
        // unrecognizable (recognizeOrOfLiterals' recognizeLiteral only matches a bare eq/ne/le/lt
        // literal, never a compound and/or child), but resolveConstraint recurses into it: the
        // and(...) child resolves via AndConstraint over its own two recognized conjuncts (reified
        // into its own fresh indicator, so AndConstraint itself appears as a ReifiedConstraint body
        // rather than a top-level constraint), the eq(z,3) child resolves via recognizeGroundRelation
        // (reified into a sibling indicator), and the two indicators combine via one top-level
        // AtLeastNConstraint -- the actual motivating capability behind this whole recursive design.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..3 </var>",
                "<intension> or(and(eq(x,1),eq(y,2)),eq(z,3)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c.getRelation().contains("AND"));
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            boolean left = digitOf(a, "x") == 1 && digitOf(a, "y") == 2;
            assertThat(left || digitOf(a, "z") == 3).isTrue();
        }
    }

    @Test void intensionOrNonLiteralOperand_recognizesViaFullRecursiveChain() throws IOException {
        // or(eq(add(x,y),5), eq(z,2)): the left child is a bare eq (arity 2, EQ operator), but
        // add(x,y) sums two distinct variables -- unlike add(var,const), which xcsp3-tools' own
        // canonizer folds directly into a var-vs-constant equality (eq(add(x,1),5) becomes eq(x,4)
        // before this code ever sees it, confirmed empirically), a two-variable sum survives as a
        // genuine compound expression -- OrRecognizer's own 2-literal recognizeOrOfLiterals declines
        // here (its recognizeLiteral only matches a bare eq/ne/le/lt, not a compound add(...) operand),
        // but the general OR path recurses into each child via the handler's full recognizeConstraint
        // chain, not just another literal/leaf-relation -- so eq(add(x,y),5) still resolves, via
        // SumOrLinearRecognizer, to a real SumBoundConstraint rather than falling all the way to
        // PredicateConstraint. Recognizing a shape is sound wherever in the tree it occurs, so an
        // and/or child is never restricted to a narrower recognizer set than a top-level relation.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> or(eq(add(x,y),5),eq(z,2)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") + digitOf(a, "y") == 5 || digitOf(a, "z") == 2).isTrue();
        }
    }

    @Test void intensionOrChainedEqualityOperand_recognizesViaAtLeastNConstraint() throws IOException {
        // or(eq(x,y,z), eq(w,1)): the left child is an n-ary (3-operand) equality -- not the arity-2
        // shape recognizeOrOfLiterals' own fast path handles, so that 2-literal path still declines,
        // but OrRecognizer's own n-ary fallback dispatches each child through the full recognizer
        // chain, where NaryEqualityRecognizer now resolves the left child (into an AndConstraint of
        // its own) and GroundRelationRecognizer the right -- both reified and combined via
        // AtLeastNConstraint(n=1), rather than falling back to PredicateConstraint as this used to.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"w\"> 0..1 </var>",
                "<intension> or(eq(x,y,z),eq(w,1)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        assertThat(instance.csp().getConstraints()).noneMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            boolean allEqual = digitOf(a, "x") == digitOf(a, "y") && digitOf(a, "y") == digitOf(a, "z");
            assertThat(allEqual || digitOf(a, "w") == 1).isTrue();
        }
    }

    @Test void intensionOrThreeOperands_recognizesViaAtLeastNConstraint() throws IOException {
        // or(eq(x,1),eq(y,2),eq(z,3)): a 3-operand or(...) isn't the two-child shape
        // recognizeOrOfLiterals matches, but resolveConstraint's general OR path has no arity
        // restriction -- each of the three children recognizes via recognizeGroundRelation and the
        // whole thing combines into one AtLeastNConstraint(n=1) over three reified indicators, a
        // genuine N-ary OR primitive rather than PredicateConstraint's opaque, unpropagated check.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> or(eq(x,1),eq(y,2),eq(z,3)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") == 1 || digitOf(a, "y") == 2 || digitOf(a, "z") == 3).isTrue();
        }
    }

    @Test void intensionOrReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> or(eq(x,1),eq(y,2)) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x == 1 || y == 2);
        }
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

    // ---- clause ---------------------------------------------------------------------------------------

    @Test void clause_positiveAndNegativeLiterals_excludesOnlyFalsifyingCombination() throws IOException {
        // x OR NOT(y) -- falsified only by x=0,y=1.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var>",
                "<clause> x not(y) </clause>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(3);
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") == 1 || digitOf(a, "y") == 0).isTrue();
        }
    }

    @Test void clauseReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var><var id=\"b\"> 0 1 </var>",
                "<clause reifiedBy=\"b\"> x not(y) </clause>");
        for (Assignment a : solutions(instance.csp())) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(x == 1 || y == 0);
        }
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void clauseTautology_sameVariablePositiveAndNegative_leavesVariableUnconstrained() throws IOException {
        // x OR NOT(x) -- always true regardless of x's value, since its domain is exactly {0, 1}.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var>",
                "<clause> x not(x) </clause>");
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    // ---- extension (table) --------------------------------------------------------------------------

    @Test void extensionSupport_buildsTuplesConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><supports> (0,1)(2,0) </supports></extension>");
        assertThat(solutions(instance.csp())).hasSize(2);
    }

    @Test void extensionConflict_buildsConflictTuplesConstraint() throws IOException {
        // x,y in {0,1,2}: 9 combinations total, minus the single listed conflict (0,0) -> 8 solutions.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><conflicts> (0,0) </conflicts></extension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(8);
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") == 0 && digitOf(a, "y") == 0).isFalse();
        }
    }

    @Test void extensionStarredConflict_throwsUnsupported() {
        // NaryConflictTuplesConstraint's counting argument assumes each listed conflict is one
        // concrete combination, not a star-weighted group of many -- combining STARRED_TUPLES with
        // positive="false" isn't attempted, unlike the plain (non-starred) conflict case above.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><conflicts> (0,*) </conflicts></extension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void extensionStarredTuples_buildsStarredTuplesConstraint() throws IOException {
        // (0,*): x=0 with y unconstrained -- 3 solutions (y=0,1,2). (1,1): exactly x=1,y=1 -- 1
        // solution. 4 total, not the 2 a plain (non-starred) reading of the same two rows would give.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var>",
                "<extension><list> x y </list><supports> (0,*)(1,1) </supports></extension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(4);
        for (Assignment a : found) {
            int x = digitOf(a, "x"), y = digitOf(a, "y");
            assertThat(x == 0 || (x == 1 && y == 1)).as("x=%d, y=%d", x, y).isTrue();
        }
    }

    // Smart tuples (expression cells like "ge(3)" in place of literal values) have no real,
    // hand-authorable XML fixture obscure/rare enough to be worth constructing here -- see
    // Xcsp3CallbackHandlerTest#buildCtrExtension_naryForm_smartTuples_throws for the direct
    // white-box coverage of that throw branch instead.

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
        // A unary restriction is just a predicate over one variable's own domain, so it maps onto
        // UnaryPredicateConstraint directly rather than the n-ary NaryConflictTuplesConstraint form
        // (see extensionConflict_buildsConflictTuplesConstraint) -- functionally equivalent for
        // arity 1, but a genuine UnaryConstraint is eligible for NodeConsistency's preprocessing
        // pass the way an ordinary NaryConstraint is not, the same reasoning the unary positive form
        // above already documents.
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

    @Test void lexMatrix_buildsRowAndColumnLexConstraints() throws IOException {
        // 2x2 matrix over {0,1}: rows must be pairwise lex<=, and so must columns.
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2][2]\"> 0..1 </array>",
                "<lex><matrix> x[][] </matrix><operator> le </operator></lex>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x00 = digitOf(a, "x[0][0]");
            int x01 = digitOf(a, "x[0][1]");
            int x10 = digitOf(a, "x[1][0]");
            int x11 = digitOf(a, "x[1][1]");
            boolean rowsLex = x00 < x10 || (x00 == x10 && x01 <= x11);
            boolean colsLex = x00 < x01 || (x00 == x01 && x10 <= x11);
            assertThat(rowsLex).as("rows [%d,%d] <= [%d,%d]", x00, x01, x10, x11).isTrue();
            assertThat(colsLex).as("cols [%d,%d] <= [%d,%d]", x00, x10, x01, x11).isTrue();
        }
    }

    @Test void lexMatrixReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[2][2]\"> 0..1 </array><var id=\"b\"> 0..1 </var>",
                "<lex reifiedBy=\"b\"><matrix> x[][] </matrix><operator> le </operator></lex>");
        for (Assignment a : solutions(instance.csp())) {
            int x00 = digitOf(a, "x[0][0]");
            int x01 = digitOf(a, "x[0][1]");
            int x10 = digitOf(a, "x[1][0]");
            int x11 = digitOf(a, "x[1][1]");
            boolean rowsLex = x00 < x10 || (x00 == x10 && x01 <= x11);
            boolean colsLex = x00 < x01 || (x00 == x01 && x10 <= x11);
            int b = digitOf(a, "b");
            assertThat(b == 1).as("matrix=[[%d,%d],[%d,%d]], b=%d", x00, x01, x10, x11, b)
                    .isEqualTo(rowsLex && colsLex);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void allDifferentList_buildsDistinctVectorsConstraint() throws IOException {
        // 3 lists of 2 booleans -- 4 possible 2-bit vectors, need all 3 pairwise distinct:
        // 4*3*2 = 24 injective assignments of 3 lists to 4 distinct values.
        Xcsp3Instance instance = parseXml(
                "<array id=\"x1\" size=\"[2]\"> 0..1 </array>"
                        + "<array id=\"x2\" size=\"[2]\"> 0..1 </array>"
                        + "<array id=\"x3\" size=\"[2]\"> 0..1 </array>",
                "<allDifferent><list> x1[] </list><list> x2[] </list><list> x3[] </list></allDifferent>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int[] v1 = {digitOf(a, "x1[0]"), digitOf(a, "x1[1]")};
            int[] v2 = {digitOf(a, "x2[0]"), digitOf(a, "x2[1]")};
            int[] v3 = {digitOf(a, "x3[0]"), digitOf(a, "x3[1]")};
            assertThat(v1).isNotEqualTo(v2);
            assertThat(v1).isNotEqualTo(v3);
            assertThat(v2).isNotEqualTo(v3);
        }
        assertThat(sols).hasSize(24);
    }

    @Test void allDifferentListReified_indicatorTracksPairwiseDistinctness() throws IOException {
        // 2 lists of 2 booleans -- 4 possible values each; b tracks whether the two vectors differ
        // (12 of the 16 combinations have them distinct, matching 4*3 ordered distinct pairs).
        Xcsp3Instance instance = parseXml(
                "<array id=\"x1\" size=\"[2]\"> 0..1 </array>"
                        + "<array id=\"x2\" size=\"[2]\"> 0..1 </array><var id=\"b\"> 0..1 </var>",
                "<allDifferent reifiedBy=\"b\"><list> x1[] </list><list> x2[] </list></allDifferent>");
        int trueCount = 0;
        for (Assignment a : solutions(instance.csp())) {
            int[] v1 = {digitOf(a, "x1[0]"), digitOf(a, "x1[1]")};
            int[] v2 = {digitOf(a, "x2[0]"), digitOf(a, "x2[1]")};
            boolean distinct = !java.util.Arrays.equals(v1, v2);
            int b = digitOf(a, "b");
            assertThat(b == 1).as("v1=%s, v2=%s", java.util.Arrays.toString(v1), java.util.Arrays.toString(v2)).isEqualTo(distinct);
            if (distinct) trueCount++;
        }
        assertThat(trueCount).isEqualTo(12);
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

    @Test void sumVariableCoefficients_decomposesIntoProductVariableConstraintsPlusSum() throws IOException {
        // Sigma x[i]*c[i] == 10, decomposing into one ProductVariableConstraint per position (a
        // fresh auxiliary holding x*c1, y*c2) plus a plain sumConstraint over the auxiliaries.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"c1\"> 1..2 </var><var id=\"c2\"> 1..2 </var>",
                "<sum><list> x y </list><coeffs> c1 c2 </coeffs><condition> (eq,10) </condition></sum>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int total = digitOf(a, "x") * digitOf(a, "c1") + digitOf(a, "y") * digitOf(a, "c2");
            assertThat(total).isEqualTo(10);
        }
    }

    @Test void sumVariableCoefficients_booleanOperands_decomposesIntoMinVariableConstraints() throws IOException {
        // Both list and coeffVars are {0,1} -- x[i]*c[i] == min(x[i],c[i]) exactly for every value,
        // so productOfPair should route through MinVariableConstraint instead of
        // ProductVariableConstraint (which bails on propagation whenever a factor's domain minimum
        // is <= 0, true of every 0/1 variable) -- the same reasoning
        // recognizeBooleanProductChannel applies on the intension side. Confirmed against a real
        // XCSP3 competition instance (Bibd-sum-06-050-25-03-10.xml.lzma), whose pairwise
        // block-intersection constraints are exactly this shape at scale (15 pairs x 50 positions).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0 1 </var><var id=\"c1\"> 0 1 </var><var id=\"c2\"> 0 1 </var>",
                "<sum><list> x y </list><coeffs> c1 c2 </coeffs><condition> (eq,1) </condition></sum>");
        assertThat(instance.csp().getConstraints()).anyMatch(MinVariableConstraint.class::isInstance);
        assertThat(instance.csp().getConstraints()).noneMatch(ProductVariableConstraint.class::isInstance);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int total = digitOf(a, "x") * digitOf(a, "c1") + digitOf(a, "y") * digitOf(a, "c2");
            assertThat(total).isEqualTo(1);
        }
    }

    @Test void sumVariableCoefficients_mixedBooleanAndNonBooleanOperands_perPositionRoutingDiffers() throws IOException {
        // Position 0 (x,c1) is boolean-boolean -> MinVariableConstraint. Position 1 (y,c2): y's own
        // domain 0..2 is non-boolean, so productOfPair's isBooleanBounds(boundsA) check alone is
        // already false -> ProductVariableConstraint. Position 2 (z,c3): z is boolean but c3's
        // domain 0..2 isn't -> isBooleanBounds(boundsA) is true this time, only
        // isBooleanBounds(boundsB) is false -> ProductVariableConstraint via the other operand.
        // Exercises that the boolean check in productOfPair is genuinely per-position, not an
        // all-or-nothing decision for the whole sum, and covers both operands of the check
        // independently failing.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0 1 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0 1 </var>"
                        + "<var id=\"c1\"> 0 1 </var><var id=\"c2\"> 0..2 </var><var id=\"c3\"> 0..2 </var>",
                "<sum><list> x y z </list><coeffs> c1 c2 c3 </coeffs><condition> (eq,2) </condition></sum>");
        assertThat(instance.csp().getConstraints()).anyMatch(MinVariableConstraint.class::isInstance);
        assertThat(instance.csp().getConstraints()).anyMatch(ProductVariableConstraint.class::isInstance);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int total = digitOf(a, "x") * digitOf(a, "c1") + digitOf(a, "y") * digitOf(a, "c2") + digitOf(a, "z") * digitOf(a, "c3");
            assertThat(total).isEqualTo(2);
        }
    }

    @Test void sumVariableCoefficientsReified_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"c1\"> 1..2 </var><var id=\"c2\"> 1..2 </var>"
                        + "<var id=\"b\"> 0..1 </var>",
                "<sum reifiedBy=\"b\"><list> x y </list><coeffs> c1 c2 </coeffs><condition> (eq,10) </condition></sum>"))
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

    @Test void countMultipleSingleValueGroupsSameList_consolidatesIntoGlobalCardinalityConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (le,1) </condition></count>"
                        + "<count><list> x y z </list><values> 1 </values><condition> (le,1) </condition></count>"
                        + "<count><list> x y z </list><values> 2 </values><condition> (le,1) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(1);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(0);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            // each of 0,1,2 used at most once across x,y,z -- with 3 vars and 3 values this forces a permutation
            assertThat(Set.of(digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "z"))).hasSize(3);
        }
    }

    @Test void countSingleValueGroupsWithEqAndGtOperators_consolidatesWithCorrectRanges() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (eq,1) </condition></count>"
                        + "<count><list> x y z </list><values> 1 </values><condition> (gt,0) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(1);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(0);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            List<Integer> digits = Stream.of(digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "z")).toList();
            assertThat(digits.stream().filter(v -> v == 0).count()).as("solution=%s", a).isEqualTo(1);
            assertThat(digits.stream().filter(v -> v == 1).count()).as("solution=%s", a).isGreaterThan(0);
        }
    }

    @Test void countSingleValueGroupsOneReified_reifiedEntryNotConsolidated() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (le,1) </condition></count>"
                        + "<count reifiedBy=\"b\"><list> x y z </list><values> 1 </values><condition> (le,1) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(0);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(1);
        for (Assignment a : solutions(instance.csp())) {
            long actual = Stream.of(digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "z")).filter(v -> v == 1).count();
            assertThat(digitOf(a, "b") == 1).as("count=%d, b=%d", actual, digitOf(a, "b")).isEqualTo(actual <= 1);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countSingleValueGroupsWithIncompatibleOperator_fallsBackToIndividualCounts() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (le,1) </condition></count>"
                        + "<count><list> x y z </list><values> 1 </values><condition> (ne,2) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(0);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(2);
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countSingleValueGroupsWithDuplicateValue_fallsBackToIndividualCounts() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (le,1) </condition></count>"
                        + "<count><list> x y z </list><values> 0 </values><condition> (ge,0) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(0);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(2);
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void countSingleValueGroupWithUnsatisfiableTranslatedBound_fallsBackToIndividualCounts() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"z\"> 0..2 </var>",
                "<count><list> x y z </list><values> 0 </values><condition> (lt,0) </condition></count>"
                        + "<count><list> x y z </list><values> 1 </values><condition> (le,1) </condition></count>");
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof GlobalCardinalityConstraint).count())
                .isEqualTo(0);
        assertThat(instance.csp().getConstraints().stream().filter(c -> c instanceof CountConstraint).count())
                .isEqualTo(2);
        // (lt,0) on its own forbids every value of x/y/z equalling 0 zero-or-more times, which is
        // vacuously unreachable as a "count < 0" -- unsatisfiable regardless of consolidation.
        assertThat(solutions(instance.csp())).isEmpty();
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

    @Test void nValuesWithGreaterThanOneCondition_routesToNotAllEqualConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..2 </var><var id=\"x2\"> 1..2 </var><var id=\"x3\"> 1..2 </var>",
                "<nValues><list> x1 x2 x3 </list><condition> (gt,1) </condition></nValues>");
        Set<Assignment> solutions = solutions(instance.csp());
        // 2^3 = 8 combinations minus the 2 all-same ones (1,1,1) and (2,2,2)
        assertThat(solutions).hasSize(6);
        for (Assignment a : solutions) {
            int x1 = digitOf(a, "x1"), x2 = digitOf(a, "x2"), x3 = digitOf(a, "x3");
            assertThat(x1 == x2 && x2 == x3).isFalse();
        }
    }

    @Test void nValuesWithGreaterThanTwoCondition_fallsBackToNValueConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<nValues><list> x1 x2 x3 </list><condition> (gt,2) </condition></nValues>");
        assertThat(solutions(instance.csp())).isNotEmpty();
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

    @Test void cardinalityWithVariableOccurs_buildsGlobalCardinalityVariableConstraint() throws IOException {
        // Fixed values, but each occurrence count is itself a variable -- one
        // GlobalCardinalityVariableConstraint covering every tracked value jointly:
        // count(list,1)==o1, count(list,2)==o2, count(list,3)==o3.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"o1\"> 0..3 </var><var id=\"o2\"> 0..3 </var><var id=\"o3\"> 0..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> 1 2 3 </values>"
                        + "<occurs> o1 o2 o3 </occurs></cardinality>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            long count1 = java.util.stream.Stream.of("x1", "x2", "x3").filter(n -> digitOf(a, n) == 1).count();
            long count2 = java.util.stream.Stream.of("x1", "x2", "x3").filter(n -> digitOf(a, n) == 2).count();
            long count3 = java.util.stream.Stream.of("x1", "x2", "x3").filter(n -> digitOf(a, n) == 3).count();
            assertThat(digitOf(a, "o1")).as("solution=%s", a).isEqualTo((int) count1);
            assertThat(digitOf(a, "o2")).as("solution=%s", a).isEqualTo((int) count2);
            assertThat(digitOf(a, "o3")).as("solution=%s", a).isEqualTo((int) count3);
        }
    }

    @Test void cardinalityWithVariableOccursClosedCoveringEveryDomain_buildsConstraints() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"o1\"> 0..3 </var><var id=\"o2\"> 0..3 </var><var id=\"o3\"> 0..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"true\"> 1 2 3 </values>"
                        + "<occurs> o1 o2 o3 </occurs></cardinality>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cardinalityWithVariableOccursClosedNotCoveringEveryDomain_throwsUnsupported() {
        // x1's domain (1..4) isn't fully covered by values {1,2,3} -- same guard as the fixed-occurs
        // closed case, exercised here for the variable-occurs overload specifically.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..4 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>"
                        + "<var id=\"o1\"> 0..3 </var><var id=\"o2\"> 0..3 </var><var id=\"o3\"> 0..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"true\"> 1 2 3 </values>"
                        + "<occurs> o1 o2 o3 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityWithVariableOccursSelfReferential_solvesMagicSequence() throws IOException {
        // The classic "magic sequence" over length 4: x[i] == the number of occurrences of i in x
        // itself -- occurs and list are literally the same variables. Unique known solution family
        // for n=4 is {2,0,2,0} up to the fixed prefix pattern (and its trivial n-1,1,0,...,0 sibling
        // family only appears for n>=7), so just check the defining property holds, not a specific
        // solution -- the point of this test is the self-reference wiring, not the puzzle's math.
        Xcsp3Instance instance = parseXml(
                "<array id=\"x\" size=\"[4]\"> 0..4 </array>",
                "<cardinality><list> x[] </list><values closed=\"false\"> 0 1 2 3 </values>"
                        + "<occurs> x[] </occurs></cardinality>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            for (int value = 0; value < 4; value++) {
                int finalValue = value;
                long count = java.util.stream.IntStream.range(0, 4)
                        .filter(i -> digitOf(a, "x[" + i + "]") == finalValue).count();
                assertThat(digitOf(a, "x[" + value + "]")).as("solution=%s, value=%d", a, value).isEqualTo((int) count);
            }
        }
    }

    @Test void cardinalityWithVariableOccursReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..2 </var><var id=\"x2\"> 1..2 </var>"
                        + "<var id=\"o1\"> 0..2 </var><var id=\"o2\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<cardinality reifiedBy=\"b\"><list> x1 x2 </list><values closed=\"false\"> 1 2 </values>"
                        + "<occurs> o1 o2 </occurs></cardinality>");
        for (Assignment a : solutions(instance.csp())) {
            long count1 = java.util.stream.Stream.of("x1", "x2").filter(n -> digitOf(a, n) == 1).count();
            long count2 = java.util.stream.Stream.of("x1", "x2").filter(n -> digitOf(a, n) == 2).count();
            boolean matches = digitOf(a, "o1") == count1 && digitOf(a, "o2") == count2;
            int b = digitOf(a, "b");
            assertThat(b == 1).as("solution=%s", a).isEqualTo(matches);
        }
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cardinalityOccursRange_buildsGlobalCardinalityRangeConstraint() throws IOException {
        // value 1 must appear 1..2 times, value 2 must appear 0..1 times, value 3 unconstrained.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..3 </var><var id=\"x3\"> 1..3 </var>",
                "<cardinality><list> x1 x2 x3 </list><values closed=\"false\"> 1 2 </values>"
                        + "<occurs> 1..2 0..1 </occurs></cardinality>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            long count1 = java.util.stream.Stream.of("x1", "x2", "x3").filter(n -> digitOf(a, n) == 1).count();
            long count2 = java.util.stream.Stream.of("x1", "x2", "x3").filter(n -> digitOf(a, n) == 2).count();
            assertThat(count1).as("solution=%s", a).isBetween(1L, 2L);
            assertThat(count2).as("solution=%s", a).isBetween(0L, 1L);
        }
        assertThat(sols).isNotEmpty();
    }

    @Test void cardinalityOccursRangeClosedCoveringEveryDomain_buildsGlobalCardinalityRangeConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..2 </var><var id=\"x2\"> 1..2 </var>",
                "<cardinality><list> x1 x2 </list><values closed=\"true\"> 1 2 </values>"
                        + "<occurs> 0..2 0..2 </occurs></cardinality>");
        assertThat(solutions(instance.csp())).isNotEmpty();
    }

    @Test void cardinalityOccursRangeClosedNotCoveringEveryDomain_throwsUnsupported() {
        // x1's domain (1..3) isn't fully covered by values {1,2}.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x1\"> 1..3 </var><var id=\"x2\"> 1..2 </var>",
                "<cardinality><list> x1 x2 </list><values closed=\"true\"> 1 2 </values>"
                        + "<occurs> 0..2 0..2 </occurs></cardinality>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    @Test void cardinalityOccursRangeReified_indicatorTracksRangeSatisfaction() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x1\"> 1..2 </var><var id=\"x2\"> 1..2 </var><var id=\"b\"> 0..1 </var>",
                "<cardinality reifiedBy=\"b\"><list> x1 x2 </list><values closed=\"false\"> 1 </values>"
                        + "<occurs> 0..1 </occurs></cardinality>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            long count1 = java.util.stream.Stream.of("x1", "x2").filter(n -> digitOf(a, n) == 1).count();
            boolean inRange = count1 <= 1;
            assertThat(digitOf(a, "b") == 1).as("solution=%s", a).isEqualTo(inRange);
        }
        assertThat(sols).hasSize(4); // all 2x2 x1,x2 combos, b determined (not free) by range membership
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

    @Test void elementWithConstantCondition_bindsResultToAuxiliaryConstantVariable() throws IOException {
        // <value> 5 </value> against a constant (rather than a variable) yields a ConditionVal;
        // elementResult bridges it via a fresh singleton-domain auxiliary variable ($const5) rather
        // than requiring a real result variable -- solutions must place 5 somewhere in a[] at index i.
        Xcsp3Instance instance = parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><value> 5 </value></element>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment solution : found) {
            int index = digitOf(solution, "i");
            assertThat(digitOf(solution, "a[" + index + "]")).isEqualTo(5);
        }
    }

    @Test void elementWithNonEqConstantCondition_throwsUnsupported() {
        // <condition>(ne,5)</condition> against a constant yields a ConditionVal with a non-EQ
        // operator -- still rejected, same as the ConditionVar non-EQ case below.
        assertThatThrownBy(() -> parseXml(
                "<array id=\"a\" size=\"[3]\"> 0..5 </array><var id=\"i\"> 0..2 </var>",
                "<element><list startIndex=\"0\"> a[0] a[1] a[2] </list><index> i </index><condition> (ne,5) </condition></element>"))
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

    // ---- mdd ------------------------------------------------------------------------------------------------------

    @Test void mdd_buildsRegularConstraint_acceptsOnlyMatchingSequence() throws IOException {
        // Layered DAG root -> n1 -> nodeT, XCSP3's fixed source/true-terminal names, mirroring
        // regular_buildsRegularConstraint_acceptsOnlyMatchingSequence's shape exactly.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<mdd><list> x y </list>"
                        + "<transitions> (root,0,n1)(n1,1,nodeT) </transitions></mdd>");
        Set<Assignment> sols = solutions(instance.csp());
        assertThat(sols).hasSize(1);
        Assignment solution = sols.iterator().next();
        assertThat(digitOf(solution, "x")).isEqualTo(0);
        assertThat(digitOf(solution, "y")).isEqualTo(1);
    }

    @Test void mdd_multipleAcceptingPaths_acceptsBothOrderings() throws IOException {
        // Two disjoint root-to-nodeT paths: root--0-->n1--1-->nodeT and root--1-->n2--0-->nodeT,
        // accepting exactly (0,1) and (1,0) out of all four 2x2 combinations.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<mdd><list> x y </list>"
                        + "<transitions> (root,0,n1)(root,1,n2)(n1,1,nodeT)(n2,0,nodeT) </transitions></mdd>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            boolean accepted = digitOf(a, "x") != digitOf(a, "y");
            assertThat(accepted).as("x=%d, y=%d", digitOf(a, "x"), digitOf(a, "y")).isTrue();
        }
        assertThat(sols).hasSize(2);
    }

    @Test void mddReified_indicatorTracksAcceptance() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"b\"> 0..1 </var>",
                "<mdd reifiedBy=\"b\"><list> x y </list>"
                        + "<transitions> (root,0,n1)(n1,1,nodeT) </transitions></mdd>");
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

    // ---- noOverlap (1D) -----------------------------------------------------------------------------------------------

    @Test void noOverlap1D_buildsDisjunctiveConstraint_disjointIntervals() throws IOException {
        // Two length-2 tasks over {0..3}: valid iff x0+2<=x1 or x1+2<=x0. Hand-enumerated: (0,2)
        // (0,3) (1,3) (2,0) (3,0) (3,1) -- 6 solutions.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x0\"> 0..3 </var><var id=\"x1\"> 0..3 </var>",
                "<noOverlap><origins> x0 x1 </origins><lengths> 2 2 </lengths></noOverlap>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int x0 = digitOf(a, "x0");
            int x1 = digitOf(a, "x1");
            assertThat(x0 + 2 <= x1 || x1 + 2 <= x0).as("x0=%d, x1=%d", x0, x1).isTrue();
        }
        assertThat(sols).hasSize(6);
    }

    @Test void noOverlap1DReified_indicatorTracksDisjointness() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x0\"> 0..3 </var><var id=\"x1\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<noOverlap reifiedBy=\"b\"><origins> x0 x1 </origins><lengths> 2 2 </lengths></noOverlap>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int x0 = digitOf(a, "x0");
            int x1 = digitOf(a, "x1");
            boolean disjoint = x0 + 2 <= x1 || x1 + 2 <= x0;
            assertThat(digitOf(a, "b") == 1).as("x0=%d, x1=%d", x0, x1).isEqualTo(disjoint);
        }
        assertThat(sols).hasSize(16); // all 4x4 x0,x1 combos, b determined (not free) by disjointness
    }

    // ---- noOverlap (2D, variable lengths) ------------------------------------------------------------------------------

    @Test void noOverlap2D_variableLengths_buildsDiffnVariableConstraint() throws IOException {
        // x fixed at 0 for both, w fixed at 2 for both -> mandatory x-overlap always, forcing
        // y-separation. Same math as noOverlap1D_buildsCumulativeConstraint_disjointIntervals: 6
        // solutions among y0,y1 in {0..3} with h=2.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x0\"> 0 </var><var id=\"y0\"> 0..3 </var><var id=\"w0\"> 2 </var><var id=\"h0\"> 2 </var>"
                        + "<var id=\"x1\"> 0 </var><var id=\"y1\"> 0..3 </var><var id=\"w1\"> 2 </var><var id=\"h1\"> 2 </var>",
                "<noOverlap><origins> (x0,y0)(x1,y1) </origins><lengths> (w0,h0)(w1,h1) </lengths></noOverlap>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int y0 = digitOf(a, "y0");
            int y1 = digitOf(a, "y1");
            assertThat(y0 + 2 <= y1 || y1 + 2 <= y0).as("y0=%d, y1=%d", y0, y1).isTrue();
        }
        assertThat(sols).hasSize(6);
    }

    @Test void noOverlap2DReified_indicatorTracksSeparation() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x0\"> 0 </var><var id=\"y0\"> 0..3 </var><var id=\"w0\"> 2 </var><var id=\"h0\"> 2 </var>"
                        + "<var id=\"x1\"> 0 </var><var id=\"y1\"> 0..3 </var><var id=\"w1\"> 2 </var><var id=\"h1\"> 2 </var>"
                        + "<var id=\"b\"> 0..1 </var>",
                "<noOverlap reifiedBy=\"b\"><origins> (x0,y0)(x1,y1) </origins><lengths> (w0,h0)(w1,h1) </lengths></noOverlap>");
        Set<Assignment> sols = solutions(instance.csp());
        for (Assignment a : sols) {
            int y0 = digitOf(a, "y0");
            int y1 = digitOf(a, "y1");
            boolean separated = y0 + 2 <= y1 || y1 + 2 <= y0;
            assertThat(digitOf(a, "b") == 1).as("y0=%d, y1=%d", y0, y1).isEqualTo(separated);
        }
        assertThat(sols).hasSize(16); // all 4x4 y0,y1 combos, b determined (not free) by separation
    }

    @Test void noOverlap2D_higherDimensionality_throwsUnsupported() {
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x0\"> 0 </var><var id=\"y0\"> 0 </var><var id=\"z0\"> 0 </var>"
                        + "<var id=\"w0\"> 2 </var><var id=\"h0\"> 2 </var><var id=\"d0\"> 2 </var>"
                        + "<var id=\"x1\"> 0 </var><var id=\"y1\"> 0 </var><var id=\"z1\"> 0 </var>"
                        + "<var id=\"w1\"> 2 </var><var id=\"h1\"> 2 </var><var id=\"d1\"> 2 </var>",
                "<noOverlap><origins> (x0,y0,z0)(x1,y1,z1) </origins><lengths> (w0,h0,d0)(w1,h1,d1) </lengths></noOverlap>"))
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
        // Reified via ValueConjunctionConstraint: one literal per pinned variable, conjoined.
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

    @Test void intensionSetMembership_withVariableAndExpressionMembers_evaluatesPerAssignment() throws IOException {
        // set(...) members are arbitrary sub-expressions, not just constants -- the classic Zebra
        // puzzle's own XCSP3 encoding needs exactly this: in(horse,set(sub(diplomat,1),add(diplomat,1))).
        // Here: x in {y, z+1} -- each member evaluated against the current assignment, not a fixed set.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..4 </var>",
                "<intension> in(x,set(y,add(z,1))) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            assertThat(x == digitOf(a, "y") || x == digitOf(a, "z") + 1).isTrue();
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

    @Test void intensionOverSingleVariable_routesThroughUnaryPredicateConstraint() throws IOException {
        // eq(pow(x,3),8): a genuine single-variable predicate that isn't a bare comparison against
        // a constant (pow(x,3) is compound, not a bare variable, and resolveVariable only knows
        // neg/sub/div/mod, not pow -- so neither recognizeBinaryRelation nor recognizeGroundRelation
        // match); no recognizer handles XCSP3's pow(base,exponent) operator at all (ProductRecognizer's
        // own Javadoc notes it as a separate, currently-unrecognized shape distinct from mul(x,x)'s
        // self-product, which ProductRecognizer does recognize -- see the dedicated Square* tests
        // instead) -- with only one variable in scope, genericIntensionConstraint routes it through
        // UnaryPredicateConstraint (a real UnaryConstraint, eligible for NodeConsistency's own
        // preprocessing) rather than the n-ary PredicateConstraint every other fallback case here
        // uses. (mod(x,3) was this test's original premise, then mul(x,x), but resolveVariable's
        // div/mod widening and ProductRecognizer's self-product routing have since picked both up --
        // see the dedicated div/mod and Square* tests instead.)
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> eq(pow(x,3),8) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryPredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat((long) Math.pow(digitOf(a, "x"), 3)).isEqualTo(8);
        }
    }

    // ---- intension bare ground relation (eq/ne/le/lt vs a constant, single variable) --------------
    // GroundRelationRecognizer, below, is also reachable via ChannelRecognizer's own full-dispatch
    // operand resolution -- also chained directly into buildCtrIntension's own top-level .or() sequence: a bare single-variable
    // comparison is already a genuine UnaryConstraint via genericIntensionConstraint's own
    // UnaryPredicateConstraint fallback (NodeConsistency-eligible when added unconditionally), but
    // UnaryPredicateConstraint implements neither Propagatable#propagate nor
    // Propagatable#isNecessarilySatisfied, so a reifiedBy-attributed occurrence of this exact shape
    // previously got zero incremental propagation from its ReifiedConstraint wrapper. Routing
    // through UnaryComparatorConstraint instead closes that gap without weakening the unreified case.

    @Test void intensionGroundRelationSingleVariable_bareEq_routesThroughUnaryComparatorConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> eq(x,5) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryComparatorConstraint.class);
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp()).getSolution();
        assertThat(solution).isPresent();
        assertThat(digitOf(solution.get(), "x")).isEqualTo(5);
    }

    @Test void intensionGroundRelationSingleVariable_constantFirstLe_routesThroughFlippedUnaryComparatorConstraint() throws IOException {
        // le(0,x): the real corpus shape (constant first) -- e.g. MagicSequence-style le(0,p[i])
        // clauses, previously only recognized inside iff via recognizeRelation; now recognized here
        // directly too.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..5 </var>",
                "<intension> le(0,x) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryComparatorConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x")).isGreaterThanOrEqualTo(0);
        }
    }

    @Test void intensionGroundRelationSingleVariable_reified_indicatorTracksConstraintTruthValue() throws IOException {
        // The motivating scenario: reifiedBy means this UnaryComparatorConstraint becomes the body
        // of a ReifiedConstraint. Unlike UnaryPredicateConstraint (the pre-existing fallback for this
        // shape), it's genuinely Propagatable, so the indicator is determined by real propagation,
        // not just a final isSatisfiedBy check.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..5 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> le(0,x) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x");
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, b=%d", x, b).isEqualTo(x >= 0);
        }
    }

    // ---- intension in/notin (UnaryInSetConstraint) ------------------------------------------------

    @Test void intensionIn_literalConstantSet_routesThroughUnaryInSetConstraint() throws IOException {
        // in(x,set(1,2,4)): every set(...) member is a bare constant, so InSetRecognizer routes
        // this to a real, propagating UnaryInSetConstraint instead of the generic PredicateConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var>",
                "<intension> in(x,set(1,2,4)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryInSetConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(3);
        for (Assignment a : found) {
            assertThat(Set.of(1, 2, 4)).contains(digitOf(a, "x"));
        }
    }

    @Test void intensionNotin_literalConstantSet_routesThroughUnaryInSetConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var>",
                "<intension> notin(x,set(1,2,4)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryInSetConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(3);
        for (Assignment a : found) {
            assertThat(Set.of(1, 2, 4)).doesNotContain(digitOf(a, "x"));
        }
    }

    @Test void intensionIn_nonConstantSetMember_fallsBackToPredicateConstraint() throws IOException {
        // in(x,set(1,y)): y is a variable, not a constant -- the set's own membership genuinely
        // depends on y's current value too, so this isn't unary at all; InSetRecognizer declines
        // and IntensionExpressionEvaluator's own per-assignment set evaluation still handles it
        // correctly, just without propagation.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var>",
                "<intension> in(x,set(1,y)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x == 1 || x == y).isTrue();
        }
    }

    @Test void intensionIn_reified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> in(x,set(1,2,4)) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            boolean inSet = Set.of(1, 2, 4).contains(digitOf(a, "x"));
            assertThat(digitOf(a, "b") == 1).isEqualTo(inSet);
        }
    }

    // ---- resolveVariable / divModAuxiliaries (div(v,k)/mod(v,k) as a resolvable compound value) ---
    // resolveVariable is the recursive value-resolver wired into recognizeGroundRelation,
    // recognizeBinaryRelation, recognizeDistanceOfPair, and recognizeDistancePairComparison/
    // asDistancePairOperand: a bare variable or constant resolves directly, div(v,k)/mod(v,k)
    // resolves via a fresh quotient/remainder auxiliary pair (divModAuxiliaries) linked to v by one
    // LinearVariableConstraint, and the resolution genuinely recurses (a div/mod of another div/mod
    // resolves too, not just one level deep).

    @Test void resolveVariableDiv_insideGroundRelation_routesThroughUnaryComparatorConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> eq(div(x,3),2) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof UnaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearVariableConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") / 3).isEqualTo(2);
        }
    }

    @Test void resolveVariableMod_constantFirst_insideGroundRelation_routesThroughFlippedUnaryComparatorConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> le(1,mod(x,3)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof UnaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") % 3).isGreaterThanOrEqualTo(1);
        }
    }

    @Test void resolveVariableDiv_vsPlainVariable_insideBinaryRelation_routesThroughBinaryComparatorConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..3 </var>",
                "<intension> eq(div(x,3),y) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") / 3).isEqualTo(digitOf(a, "y"));
        }
    }

    @Test void resolveVariableDiv_vsAnotherDivOperand_insideBinaryRelation_routesThroughBinaryComparatorConstraintOverBothAuxiliaries() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> eq(div(x,3),div(y,2)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof LinearVariableConstraint<?>).hasSize(2);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") / 3).isEqualTo(digitOf(a, "y") / 2);
        }
    }

    @Test void resolveVariableDiv_insideDistOfPair_routesThroughAbsoluteDifferenceConstraintOverAuxiliaries() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var>",
                "<intension> eq(dist(div(x,3),div(y,3)),1) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AbsoluteDifferenceConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs(digitOf(a, "x") / 3 - digitOf(a, "y") / 3)).isEqualTo(1);
        }
    }

    @Test void resolveVariableDiv_negativeDomainDividend_declinesAndFallsBackToUnaryPredicateConstraint() throws IOException {
        // resolveVariable's div/mod case requires the dividend's declared min bound >= 0 (Java's
        // /,% are truncate-toward-zero, matching floor-division/true-modulo only for non-negative
        // operands) -- exercises that guard specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -3..5 </var>",
                "<intension> eq(div(x,3),1) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryPredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.floorDiv(digitOf(a, "x"), 3)).isEqualTo(1);
        }
    }

    @Test void resolveVariableDiv_variableDivisor_declinesAndFallsBackToPredicateConstraint() throws IOException {
        // div(x,y): the divisor is itself a variable, not a constant -- exercises
        // asConstant(parent.sons[1]).isEmpty() specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 1..3 </var>",
                "<intension> eq(div(x,y),2) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") / digitOf(a, "y")).isEqualTo(2);
        }
    }

    @Test void resolveVariableDiv_nonPositiveConstantDivisor_declinesAndFallsBackToUnaryPredicateConstraint() throws IOException {
        // div(x,-2): the divisor is a constant but not strictly positive -- exercises
        // divisor.get() <= 0 specifically, distinct from divisor.isEmpty() (a variable divisor,
        // tested above). Target -2 (not 1) since resolveVariable declines entirely here -- the
        // fallback genericIntensionConstraint/IntensionExpressionEvaluator evaluates div via plain
        // Java integer division (truncating, not floor), so the target must be reachable under that
        // exact semantics for x in the declared domain (x=4 or x=5 give 4/-2==5/-2==-2 in Java).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var>",
                "<intension> eq(div(x,-2),-2) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(UnaryPredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") / -2).isEqualTo(-2);
        }
    }

    @Test void resolveVariableGroundRelation_ternaryCompoundOperand_declinesCleanly() throws IOException {
        // eq(add(x,y,z),5): resolveVariable is called (via recognizeGroundRelation) on a compound
        // operand with 3 sons, not the 2-son shape its div/mod case requires -- exercises
        // resolveVariable's own parent.sons.length != 2 guard specifically (distinct from a node
        // that isn't a compound at all). The whole shape is still recognized elsewhere
        // (recognizeSumOrLinear), just not via resolveVariable/recognizeGroundRelation.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> eq(add(x,y,z),5) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") + digitOf(a, "y") + digitOf(a, "z")).isEqualTo(5);
        }
    }

    @Test void resolveVariableDiv_sameVariableAndDivisor_reusedAcrossTwoRecognizers_sharesOneAuxiliaryPair() throws IOException {
        // div(x,3) occurs once via recognizeGroundRelation (eq(div(x,3),2)) and once via
        // recognizeBinaryRelation (eq(div(x,3),y)) -- both should resolve to the same quotient/
        // remainder auxiliary pair, sharing one LinearVariableConstraint, not building a redundant
        // second copy (the same amortization distanceAuxiliary/constantVariable already give).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..3 </var>",
                "<intension> eq(div(x,3),2) </intension>"
                        + "<intension> eq(div(x,3),y) </intension>");
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof LinearVariableConstraint<?>).hasSize(1);
    }

    @Test void resolveVariableDiv_nestedDivOfDiv_resolvesRecursively() throws IOException {
        // div(div(x,2),3): the dividend of the outer div is itself a div node -- confirms
        // resolveVariable's recursion (not just one level deep) and that the synthesized quotient
        // auxiliary's own bounds are registered into boundsByName so the outer div's guard can
        // validate it.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..23 </var>",
                "<intension> eq(div(div(x,2),3),1) </intension>");
        assertThat(instance.csp().getConstraints()).filteredOn(c -> c instanceof LinearVariableConstraint<?>).hasSize(2);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat((digitOf(a, "x") / 2) / 3).isEqualTo(1);
        }
    }

    @Test void resolveVariableDiv_addDividend_constantTarget_routesThroughUnaryComparatorConstraintOverAuxiliary() throws IOException {
        // div(add(x,y),3): the dividend is now a resolvable compound (resolveVariable's own add
        // case, added for XCSP3's div(add(...),k)/mod(add(...),k) shape -- see addAuxiliary) --
        // resolves to a real div auxiliary over the sum, then GroundRelationRecognizer picks up the
        // constant target (1) directly. This used to decline entirely (add wasn't a known compound
        // operator to resolveVariable); now it's a genuinely tighter UnaryComparatorConstraint, not
        // PredicateConstraint -- see intensionSumTarget_divOfAddDividend_routesThroughBinaryComparatorConstraintOverAuxiliary
        // for the variable-target sibling of this same shape.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<intension> eq(div(add(x,y),3),1) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof UnaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).noneMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat((digitOf(a, "x") + digitOf(a, "y")) / 3).isEqualTo(1);
        }
    }

    // ---- resolveVariable / negAuxiliary (neg(v) as a resolvable compound value) ---------------------
    // neg(v) resolves the same recursive way div(v,k)/mod(v,k) already do, via a fresh negation
    // auxiliary (negAuxiliary) linked to v by one LinearVariableConstraint -- no sign guard, unlike
    // div/mod, since negation is exact for any integer domain.

    @Test void resolveVariableNeg_insideGroundRelation_routesThroughUnaryComparatorConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -10..10 </var>",
                "<intension> eq(neg(x),5) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof UnaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearVariableConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(-digitOf(a, "x")).isEqualTo(5);
        }
    }

    @Test void resolveVariableNeg_insideBinaryRelation_routesThroughBinaryComparatorConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -10..10 </var><var id=\"y\"> -10..10 </var>",
                "<intension> eq(neg(x),y) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(-digitOf(a, "x")).isEqualTo(digitOf(a, "y"));
        }
    }

    @Test void resolveVariableNeg_insideDistOfPair_routesThroughAbsoluteDifferenceConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -10..10 </var><var id=\"y\"> -10..10 </var>",
                "<intension> eq(dist(neg(x),y),3) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AbsoluteDifferenceConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs(-digitOf(a, "x") - digitOf(a, "y"))).isEqualTo(3);
        }
    }

    @Test void resolveVariableNeg_nestedInsideDiv_resolvesRecursively() throws IOException {
        // neg(div(x,2)): the operand of neg is itself a div node -- confirms resolveVariable's
        // recursion covers neg the same way it already covers div(div(...)).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..10 </var><var id=\"y\"> -10..10 </var>",
                "<intension> eq(neg(div(x,2)),y) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(-(digitOf(a, "x") / 2)).isEqualTo(digitOf(a, "y"));
        }
    }

    @Test void resolveVariableDiv_negAuxiliaryDividend_declinesWhenAuxiliaryDomainGoesNegative() throws IOException {
        // div(neg(x),2): neg(x)'s own auxiliary domain is the negation of x's -- here [-10,0] for
        // x in [0,10] -- so div's own dividend-min->=0 guard correctly declines even though x
        // itself never goes negative, since the *auxiliary standing in for the dividend* does. The
        // neg(x) resolution attempt still leaves its own harmless orphaned LinearVariableConstraint
        // behind (the same accepted "declined recognition can leave an orphaned auxiliary" tradeoff
        // resolveVariable's own Javadoc documents), so this checks anyMatch, not the first-iterated
        // constraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..10 </var><var id=\"y\"> -10..10 </var>",
                "<intension> eq(div(neg(x),2),y) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(-digitOf(a, "x") / 2).isEqualTo(digitOf(a, "y"));
        }
    }

    // ---- resolveVariable / subAuxiliary (sub(a,b) as a resolvable compound value) --------------------
    // sub survives unflattened only when nested inside dist(...) -- a ground/binary relation's own
    // sub operand is always algebraically eliminated by the canonizer first (e.g. eq(sub(x,y),5)
    // canonicalizes directly to eq(x,add(y,5))), confirmed via a real probe.

    @Test void resolveVariableSub_insideDistOfPair_routesThroughAbsoluteDifferenceConstraintOverAuxiliary() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -10..10 </var><var id=\"y\"> -10..10 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(dist(sub(x,y),z),3) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AbsoluteDifferenceConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs((digitOf(a, "x") - digitOf(a, "y")) - digitOf(a, "z"))).isEqualTo(3);
        }
    }

    @Test void resolveVariableSub_rightOperandUnresolvable_declines() throws IOException {
        // dist(sub(x,mul(y,2)),z): sub's own right operand is compound (mul(y,2)), not resolvable
        // by resolveVariable's own leaf/neg/sub/div/mod cases -- exercises the right.isEmpty()
        // check specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -10..10 </var><var id=\"y\"> -5..5 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(dist(sub(x,mul(y,2)),z),3) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs((digitOf(a, "x") - digitOf(a, "y") * 2) - digitOf(a, "z"))).isEqualTo(3);
        }
    }

    @Test void resolveVariableSub_leftOperandUnresolvable_declines() throws IOException {
        // dist(sub(mul(x,2),y),z): sub's own left operand is compound (mul(x,2)) -- distinct from
        // the right-operand rejection above, exercising left.isEmpty() specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> -5..5 </var><var id=\"y\"> -10..10 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(dist(sub(mul(x,2),y),z),3) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(Math.abs((digitOf(a, "x") * 2 - digitOf(a, "y")) - digitOf(a, "z"))).isEqualTo(3);
        }
    }

    // ---- SumOrLinearRecognizer target-side resolveVariable fallback (compound target) ---------------

    @Test void intensionSumTarget_compoundDivExpression_routesThroughSumVariableConstraintOverAuxiliary() throws IOException {
        // eq(sub(div(x,2),y),z) canonicalizes to eq(add(y,z),div(x,2)) -- a genuinely compound
        // target (div(x,2)), neither a bare variable nor a constant, that only resolveVariable's
        // fallback (not the narrower asVariable/asConstant pair) can resolve.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..10 </var><var id=\"y\"> -10..10 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(sub(div(x,2),y),z) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof SumVariableConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat((digitOf(a, "x") / 2) - digitOf(a, "y")).isEqualTo(digitOf(a, "z"));
        }
    }

    @Test void intensionSumTarget_compoundDivExpressionWeightedCoefficients_routesThroughLinearVariableConstraint() throws IOException {
        // eq(sub(div(x,2),mul(y,3)),z) canonicalizes to eq(add(mul(y,3),z),div(x,2)) -- a compound
        // target (div(x,2)) alongside non-unit coefficients (y:3, z:1), exercising the
        // unitCoefficients==false half of the compound-target fallback specifically (distinct from
        // intensionSumTarget_compoundDivExpression's all-unit-coefficient case).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..10 </var><var id=\"y\"> -5..5 </var><var id=\"z\"> -20..20 </var>",
                "<intension> eq(sub(div(x,2),mul(y,3)),z) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearVariableConstraint<?>);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat((digitOf(a, "x") / 2) - digitOf(a, "y") * 3).isEqualTo(digitOf(a, "z"));
        }
    }

    @Test void intensionSumTarget_modOfAddDividend_routesThroughBinaryComparatorConstraintOverAuxiliary() throws IOException {
        // eq(mod(add(c,l,l),10),r): mod's own dividend is add(c,l,l), not a bare variable -- l
        // appears twice, contributing coefficient 2. resolveVariable now recurses into add (via
        // addAuxiliary/foldAddTerm) before resolving div/mod, materializing a real sum auxiliary
        // rather than declining. A real corpus regression: found via the fallback histogram as the
        // single largest remaining PredicateConstraint bucket (mod and div siblings together,
        // two-thirds of all residual occurrences across the bundled corpus).
        Xcsp3Instance instance = parseXml(
                "<var id=\"c\"> 0..5 </var><var id=\"l\"> 0..5 </var><var id=\"r\"> 0..9 </var>",
                "<intension> eq(mod(add(c,l,l),10),r) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).noneMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int sum = digitOf(a, "c") + 2 * digitOf(a, "l");
            assertThat(sum % 10).isEqualTo(digitOf(a, "r"));
        }
    }

    @Test void intensionSumTarget_divOfAddDividend_routesThroughBinaryComparatorConstraintOverAuxiliary() throws IOException {
        // Same shape as the mod case above, but div's own sibling -- exercises resolveVariable's
        // isDiv branch reaching an add(...) dividend instead of mod's.
        Xcsp3Instance instance = parseXml(
                "<var id=\"c\"> 0..5 </var><var id=\"l\"> 0..5 </var><var id=\"q\"> 0..2 </var>",
                "<intension> eq(div(add(c,l,l),10),q) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint<?>);
        assertThat(instance.csp().getConstraints()).noneMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int sum = digitOf(a, "c") + 2 * digitOf(a, "l");
            assertThat(sum / 10).isEqualTo(digitOf(a, "q"));
        }
    }

    @Test void intensionNeq_addVsAdd_routesThroughSumVariableConstraintOverAuxiliary() throws IOException {
        // ne(add(a,b),add(c,d)): SumOrLinearRecognizer (tried before BinaryRelationRecognizer, per
        // the recognizer-ordering fix above) recognizes add(a,b) directly as a coefficient map --
        // {a:1,b:1} -- against a target it resolves via resolveVariable's new add case: a single sum
        // auxiliary for add(c,d). This is tighter than routing both sides through resolveVariable
        // independently (two auxiliaries plus an indirect BinaryComparatorConstraint), which is what
        // BinaryRelationRecognizer's own final fallback would have produced had SumOrLinearRecognizer
        // not been tried first. A real corpus shape (ne(add(x[0],x[11]),add(x[10],x[1]))).
        Xcsp3Instance instance = parseXml(
                "<var id=\"a\"> 0..3 </var><var id=\"b\"> 0..3 </var><var id=\"c\"> 0..3 </var><var id=\"d\"> 0..3 </var>",
                "<intension> ne(add(a,b),add(c,d)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(cn -> cn instanceof SumVariableConstraint);
        assertThat(instance.csp().getConstraints()).noneMatch(cn -> cn instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment assignment : found) {
            assertThat(digitOf(assignment, "a") + digitOf(assignment, "b"))
                    .isNotEqualTo(digitOf(assignment, "c") + digitOf(assignment, "d"));
        }
    }

    @Test void intensionSumTarget_modOfAddDividendWithUnfoldableTerm_fallsBackToPredicateConstraint() throws IOException {
        // eq(mod(add(c,mul(a,b)),10),r): add's second term, mul(a,b), is a product of two variables
        // -- not one of foldAddTerm's four recognized shapes (bare var, mul(var,const), neg(var),
        // sub(var,var)) -- so addAuxiliary declines the same way SumOrLinearRecognizer's own
        // recognition would for the identical term shape, and resolveVariable's mod case correctly
        // falls through rather than materializing an incomplete auxiliary.
        Xcsp3Instance instance = parseXml(
                "<var id=\"c\"> 0..5 </var><var id=\"a\"> 0..3 </var><var id=\"b\"> 0..3 </var><var id=\"r\"> 0..9 </var>",
                "<intension> eq(mod(add(c,mul(a,b)),10),r) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int sum = digitOf(a, "c") + digitOf(a, "a") * digitOf(a, "b");
            assertThat(sum % 10).isEqualTo(digitOf(a, "r"));
        }
    }

    @Test void intensionSumTarget_compoundExpressionUnresolvable_fallsBackToPredicateConstraint() throws IOException {
        // eq(add(y,z),mul(x,2)): mul(x,2) is a genuinely compound target, but mul isn't one of
        // resolveVariable's own known compound operators (neg/add/sub/div/mod) -- SumOrLinearRecognizer
        // itself declines cleanly (its own target resolution fails on mul before ever materializing
        // an add(y,z) auxiliary). BinaryRelationRecognizer's own final fallback does resolve
        // add(y,z) into a real sum auxiliary along the way, via resolveVariable, before failing on
        // mul(x,2) and declining overall -- a harmless orphaned auxiliary/linking-constraint
        // (resolveVariable's own documented, accepted tradeoff), checked via anyMatch rather than
        // the fragile iterator().next() (unspecified Set order, now more than one element).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..10 </var><var id=\"y\"> -10..10 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(add(y,z),mul(x,2)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "y") + digitOf(a, "z")).isEqualTo(digitOf(a, "x") * 2);
        }
    }

    // ---- resolveConstraint (recursive and/or composition, arbitrary arity/depth) -------------------

    @Test void resolveConstraint_knightsMoveShape_endToEnd_routesThroughAndConstraintAndAtLeastNConstraint() throws IOException {
        // The actual motivating corpus shape (KnightTour-06-int.xml.lzma/QueenAttacking-06.xml.lzma,
        // 71 occurrences total): or(and(eq(dist(div,div),1),eq(dist(mod,mod),2)),
        // and(eq(dist(div,div),2),eq(dist(mod,mod),1))) -- a knight's-move adjacency relation between
        // two cell-index variables on a 6-wide grid (row = div(v,6), col = mod(v,6)). Exercises the
        // full stack together: resolveVariable's div/mod widening inside dist(...), resolveConstraint's
        // AND branch (AndConstraint over two AbsoluteDifferenceConstraint leaves), and its OR branch
        // (AtLeastNConstraint over the two AND branches' reified indicators).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x0\"> 0..35 </var><var id=\"x1\"> 0..35 </var>",
                "<intension> or(and(eq(dist(div(x0,6),div(x1,6)),1),eq(dist(mod(x0,6),mod(x1,6)),2)),"
                        + "and(eq(dist(div(x0,6),div(x1,6)),2),eq(dist(mod(x0,6),mod(x1,6)),1))) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int row0 = digitOf(a, "x0") / 6, col0 = digitOf(a, "x0") % 6;
            int row1 = digitOf(a, "x1") / 6, col1 = digitOf(a, "x1") % 6;
            int dRow = Math.abs(row0 - row1), dCol = Math.abs(col0 - col1);
            assertThat((dRow == 1 && dCol == 2) || (dRow == 2 && dCol == 1)).isTrue();
        }
        // Cross-check completeness: every genuine knight-adjacent pair on the 6x6 grid is found.
        int expectedPairs = 0;
        for (int v0 = 0; v0 < 36; v0++) {
            for (int v1 = 0; v1 < 36; v1++) {
                int dRow = Math.abs(v0 / 6 - v1 / 6), dCol = Math.abs(v0 % 6 - v1 % 6);
                if ((dRow == 1 && dCol == 2) || (dRow == 2 && dCol == 1)) expectedPairs++;
            }
        }
        assertThat(found).hasSize(expectedPairs);
    }

    @Test void resolveConstraint_twoLiteralOr_stillRoutesThroughRelationLogicConstraint() throws IOException {
        // or(eq(x,1),eq(y,2)): confirms the cheaper 2-literal special case (recognizeOrOfLiterals,
        // via RelationLogicConstraint -- no extra indicator variables) still takes priority over the
        // general N-ary path for the shape it already handles well; no regression from folding
        // recognizeOrOfLiterals into resolveConstraint's own dispatch.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var>",
                "<intension> or(eq(x,1),eq(y,2)) </intension>");
        assertThat(instance.csp().getConstraints()).hasSize(1);
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(RelationLogicConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x") == 1 || digitOf(a, "y") == 2).isTrue();
        }
    }

    @Test void resolveConstraint_deeplyNestedAndOr_resolvesRecursively() throws IOException {
        // and(eq(x,1), or(eq(y,2),eq(z,3))): AND at the root, OR nested inside one conjunct --
        // confirms genuine recursion (not just one level of and/or), not just the one hardcoded
        // or(and,and) shape.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> and(eq(x,1),or(eq(y,2),eq(z,3))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AndConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x")).isEqualTo(1);
            assertThat(digitOf(a, "y") == 2 || digitOf(a, "z") == 3).isTrue();
        }
    }

    @Test void resolveConstraint_orOfThreeAndPairs_resolvesRecursively() throws IOException {
        // or(and(eq(x,1),eq(y,1)), and(eq(x,2),eq(y,2)), eq(z,9)): a 3-ary or(...) where two
        // children are themselves and(...) -- confirms the N-ary OR path composes with nested AND
        // children too, not just the exactly-2-ary knight's-move shape.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> or(and(eq(x,1),eq(y,1)),and(eq(x,2),eq(y,2)),eq(z,9)) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof AtLeastNConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            boolean opt1 = digitOf(a, "x") == 1 && digitOf(a, "y") == 1;
            boolean opt2 = digitOf(a, "x") == 2 && digitOf(a, "y") == 2;
            assertThat(opt1 || opt2 || digitOf(a, "z") == 9).isTrue();
        }
    }

    @Test void resolveConstraint_andWithNaryEqualityConjunct_recognizesBothConjuncts() throws IOException {
        // and(eq(x,1), eq(y,z,w)): the second conjunct is a 3-ary equality -- now recognized by
        // NaryEqualityRecognizer (itself an AndConstraint of consecutive pairwise equalities), so
        // AndRecognizer's own dispatch succeeds on both conjuncts and wraps them in one outer
        // AndConstraint, rather than declining the whole and(...) as it used to.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var>"
                        + "<var id=\"z\"> 0..3 </var><var id=\"w\"> 0..3 </var>",
                "<intension> and(eq(x,1),eq(y,z,w)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AndConstraint.class);
        assertThat(instance.csp().getConstraints()).noneMatch(c -> c instanceof PredicateConstraint);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            assertThat(digitOf(a, "x")).isEqualTo(1);
            boolean allEqual = digitOf(a, "y") == digitOf(a, "z") && digitOf(a, "z") == digitOf(a, "w");
            assertThat(allEqual).isTrue();
        }
    }

    @Test void resolveConstraint_reifiedOrOfAndPairs_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> or(and(eq(x,1),eq(y,1)),and(eq(x,2),eq(y,2))) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            boolean shape = (digitOf(a, "x") == 1 && digitOf(a, "y") == 1)
                    || (digitOf(a, "x") == 2 && digitOf(a, "y") == 2);
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d, b=%d", digitOf(a, "x"), digitOf(a, "y"), digitOf(a, "b"))
                    .isEqualTo(shape);
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

    @Test void intensionConjunctionOfNotEqualAndLessThan_recognizesViaAndConstraint() throws IOException {
        // and(ne(x,y), lt(y,z)): the root operator is AND -- resolveConstraint's AND branch
        // recognizes both conjuncts (each a bare binary relation via recognizeBinaryRelation) and
        // combines them via AndConstraint, a real fixpoint over both, rather than falling through
        // to the opaque, unpropagated PredicateConstraint this used to reach.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..3 </var>",
                "<intension> and(ne(x,y),lt(y,z)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AndConstraint.class);
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

    @Test void intensionAddOfVariableAndNestedExpression_routesThroughLinearVariableConstraint() throws IOException {
        // add(x,mul(y,2)): xcsp3-tools' canonizer reorders add's own two operands by complexity,
        // same as it does for eq/ne's operands (see intensionBinaryOffsetWithEquals's own comment) --
        // the compound mul(y,2) canonicalizes to the front, the bare variable x to the back, so
        // asVariable(sons[0]) is absent (mul(...) isn't a plain variable) even though
        // asConstant(sons[1]) does find a leaf (just not a LONG one) -- recognizeBinaryRelation
        // still declines here, but recognizeSumOrLinear recognizes add's own two terms directly
        // (x as a unit-coefficient term, mul(y,2) as a weighted one) into LinearVariableConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,mul(y,2))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(LinearVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") * 2);
        }
    }

    @Test void intensionAddOfTwoVariablesNoConstant_routesThroughSumVariableConstraint() throws IOException {
        // add(x,y) has two sons but neither is a constant, so it doesn't match the
        // "var + constant" offset shape even though the add itself is binary --
        // recognizeBinaryRelation still declines, but recognizeSumOrLinear recognizes both terms
        // as unit-coefficient variables into SumVariableConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,y)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SumVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y"));
        }
    }

    @Test void intensionAddOfTwoNestedExpressions_neitherOperandIsALeaf_routesThroughLinearVariableConstraint() throws IOException {
        // add(mul(x,2),mul(y,3)): both operands are compound expressions, so the canonizer's
        // complexity-based reordering has no simpler leaf to demote to the back -- sons[1] stays a
        // non-leaf MUL node, which is what exercises asConstant's own "not a leaf at all" branch
        // (distinct from intensionAddOfVariableAndNestedExpression's "leaf, but not LONG" case above) --
        // recognizeBinaryRelation still declines, but recognizeSumOrLinear recognizes both weighted
        // terms into LinearVariableConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..20 </var>",
                "<intension> eq(z,add(mul(x,2),mul(y,3))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(LinearVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") * 2 + digitOf(a, "y") * 3);
        }
    }

    // ---- intension add(...) recognition (SumVariableConstraint/SumBoundConstraint/LinearVariableConstraint/LinearBoundConstraint) ----

    @Test void intensionSumUnweighted_constantTarget_routesThroughSumBoundConstraint() throws IOException {
        // le(add(x,y),20): every add term is a bare variable, target is a constant.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..15 </var><var id=\"y\"> 0..15 </var>",
                "<intension> le(add(x,y),20) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SumBoundConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") + digitOf(a, "y")).isLessThanOrEqualTo(20);
        }
    }

    @Test void intensionSum_geOperatorSwapsAddToSecondOperand_routesThroughSumBoundConstraint() throws IOException {
        // ge(add(x,y),6) is rewritten by xcsp3-tools' own canonizer into le(6,add(x,y)) -- ge/gt are
        // always eliminated via le/lt with operands swapped, and that swap moves the compound add
        // side to tree.sons[1] regardless of its complexity (confirmed via a direct probe against a
        // real parsed tree: LE with son0=LONG, son1=ADD, for domains 1..5). SumOrLinearRecognizer
        // must check sons[1] for add(...) too, flipping the operator back, or GEQ would be silently
        // unreachable for any source file that used ge/gt against an add(...) operand.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..5 </var><var id=\"y\"> 1..5 </var>",
                "<intension> ge(add(x,y),6) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SumBoundConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(15); // 15 of 25 pairs in {1..5}^2 sum to >= 6
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") + digitOf(a, "y")).isGreaterThanOrEqualTo(6);
        }
    }

    @Test void intensionSumWeighted_constantTarget_routesThroughLinearBoundConstraint() throws IOException {
        // eq(add(mul(x,2),y),10): x is weighted, y is unit-coefficient, target is a constant.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var>",
                "<intension> eq(add(mul(x,2),y),10) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(LinearBoundConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") * 2 + digitOf(a, "y")).isEqualTo(10);
        }
    }

    @Test void intensionSumThreeTerms_routesThroughSumVariableConstraint() throws IOException {
        // add with more than two unit-coefficient terms generalizes the same way.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"w\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(x,y,w)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(SumVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") + digitOf(a, "w"));
        }
    }

    @Test void intensionSumReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(z,add(x,y)) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x"), y = digitOf(a, "y"), z = digitOf(a, "z"), b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, z=%d, b=%d", x, y, z, b).isEqualTo(z == x + y);
        }
    }

    @Test void intensionAddOfNonVariableTermAndVariable_fallsBackToPredicateConstraint() throws IOException {
        // add(mul(x,y),z): one term is a product of two variables -- neither a bare variable nor
        // a mul(var, constant) -- so the whole add is declined, not just that one term silently
        // dropped.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..3 </var><var id=\"w\"> 0..15 </var>",
                "<intension> eq(w,add(mul(x,y),z)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "w")).isEqualTo(digitOf(a, "x") * digitOf(a, "y") + digitOf(a, "z"));
        }
    }

    @Test void intensionAddOfMulTermWithNonVariableFirstOperand_fallsBackToPredicateConstraint() throws IOException {
        // add(mul(div(x,2),3),w): the mul term's own first operand is itself a compound
        // expression (a different operator, div, so it can't get flattened together with the
        // outer mul the way nested mul(mul(...)) terms can), not a bare variable -- exercises
        // asVariable(term.sons[0]).isEmpty() specifically, distinct from
        // intensionAddOfNonVariableTermAndVariable's rejection (a non-constant second operand)
        // and intensionAddOfThreeArgMulTerm's rejection (wrong arity).
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"w\"> 0..3 </var><var id=\"v\"> 0..30 </var>",
                "<intension> eq(v,add(mul(div(x,2),3),w)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "v")).isEqualTo((digitOf(a, "x") / 2) * 3 + digitOf(a, "w"));
        }
    }

    @Test void intensionAddOfThreeArgMulTerm_fallsBackToPredicateConstraint() throws IOException {
        // add(mul(x,y,z),w): the mul term has three operands, not the two-term mul(var, constant)
        // shape recognizeSumOrLinear's own term.sons.length == 2 guard requires.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..3 </var><var id=\"y\"> 1..3 </var><var id=\"z\"> 1..3 </var><var id=\"w\"> 0..3 </var><var id=\"v\"> 0..30 </var>",
                "<intension> eq(v,add(mul(x,y,z),w)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "v")).isEqualTo(digitOf(a, "x") * digitOf(a, "y") * digitOf(a, "z") + digitOf(a, "w"));
        }
    }

    @Test void intensionAddOfNegatedVariableTerm_routesThroughLinearVariableConstraint() throws IOException {
        // add(x,neg(y)): neg(y) contributes coefficient -1 to y, same as mul(y,-1) would.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> -5..5 </var>",
                "<intension> eq(z,add(x,neg(y))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(LinearVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") - digitOf(a, "y"));
        }
    }

    @Test void intensionAddOfNegatedNonVariableTerm_fallsBackToPredicateConstraint() throws IOException {
        // add(x,neg(mul(y,2))): neg's own operand is compound, not a bare variable.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> -10..10 </var>",
                "<intension> eq(z,add(x,neg(mul(y,2)))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") - digitOf(a, "y") * 2);
        }
    }

    @Test void intensionAddOfSubtractionTerm_routesThroughLinearVariableConstraint() throws IOException {
        // add(x,sub(y,w)): sub(y,w) contributes coefficient 1 to y and -1 to w from a single term.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"w\"> 0..5 </var><var id=\"z\"> -5..15 </var>",
                "<intension> eq(z,add(x,sub(y,w))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(LinearVariableConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") - digitOf(a, "w"));
        }
    }

    @Test void intensionAddOfSubtractionTermWithNonVariableOperand_fallsBackToPredicateConstraint() throws IOException {
        // add(x,sub(y,mul(w,2))): sub's own right operand is compound, not a bare variable.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"w\"> 0..5 </var><var id=\"z\"> -10..15 </var>",
                "<intension> eq(z,add(x,sub(y,mul(w,2)))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") - digitOf(a, "w") * 2);
        }
    }

    @Test void intensionAddOfSubtractionTermWithNonVariableLeftOperand_fallsBackToPredicateConstraint() throws IOException {
        // add(x,sub(mul(y,2),w)): sub's own left operand is compound, not a bare variable --
        // distinct from intensionAddOfSubtractionTermWithNonVariableOperand's right-operand
        // rejection, exercising subLeft.isPresent() being false specifically.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"w\"> 0..5 </var><var id=\"z\"> -10..15 </var>",
                "<intension> eq(z,add(x,sub(mul(y,2),w))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(digitOf(a, "x") + digitOf(a, "y") * 2 - digitOf(a, "w"));
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

    // ---- intension add(relation,...) op target recognition (LinearBooleanBoundConstraint/LinearBooleanVariableConstraint) ----

    @Test void intensionSumOfRelations_constantTarget_routesThroughLinearBooleanBoundConstraint() throws IOException {
        // add(le(2,x),le(3,y)): both add terms are themselves relations (x>=2, y>=3), not bare
        // variables/weighted mul terms -- SumOrLinearRecognizer declines each term, but
        // RelationSumRecognizer resolves each via the full recognizer chain (here,
        // GroundRelationRecognizer), reifies them into fresh boolean indicators, and sums those
        // indicators (weight 1 each) against a constant target -- "exactly one of these two
        // thresholds is met".
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var>",
                "<intension> eq(add(le(2,x),le(3,y)),1) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearBooleanBoundConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int xGe2 = digitOf(a, "x") >= 2 ? 1 : 0;
            int yGe3 = digitOf(a, "y") >= 3 ? 1 : 0;
            assertThat(xGe2 + yGe3).isEqualTo(1);
        }
    }

    @Test void intensionSumOfRelations_geOperatorSwapsAddToSecondOperand_routesThroughLinearBooleanBoundConstraint() throws IOException {
        // ge(add(eq(x,1),eq(y,1),eq(z,1)),2) is rewritten by xcsp3-tools' own canonizer into
        // le(2,add(...)) -- the same ge/gt operand-swap phenomenon SumOrLinearRecognizer's own
        // GE-swap test documents, but here every add term is itself a relation, so this exercises
        // RelationSumRecognizer's identical dual-order fix instead: "at least two of these three
        // equality checks hold".
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var><var id=\"z\"> 0..1 </var>",
                "<intension> ge(add(eq(x,1),eq(y,1),eq(z,1)),2) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearBooleanBoundConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).hasSize(4); // C(3,2)+C(3,3) = 3+1 = 4 ways to have >=2 of 3 booleans true
        for (Assignment a : solutions) {
            int xEq1 = digitOf(a, "x") == 1 ? 1 : 0;
            int yEq1 = digitOf(a, "y") == 1 ? 1 : 0;
            int zEq1 = digitOf(a, "z") == 1 ? 1 : 0;
            assertThat(xEq1 + yEq1 + zEq1).isGreaterThanOrEqualTo(2);
        }
    }

    @Test void intensionSumOfRelations_variableTarget_routesThroughLinearBooleanVariableConstraint() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..1 </var>",
                "<intension> eq(z,add(le(2,x),le(3,y))) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearBooleanVariableConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int xGe2 = digitOf(a, "x") >= 2 ? 1 : 0;
            int yGe3 = digitOf(a, "y") >= 3 ? 1 : 0;
            assertThat(digitOf(a, "z")).isEqualTo(xGe2 + yGe3);
        }
    }

    @Test void intensionSumOfThreeRelations_generalizesToNTerms() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> eq(add(le(2,x),le(2,y),le(2,z)),2) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof LinearBooleanBoundConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int count = (digitOf(a, "x") >= 2 ? 1 : 0) + (digitOf(a, "y") >= 2 ? 1 : 0) + (digitOf(a, "z") >= 2 ? 1 : 0);
            assertThat(count).isEqualTo(2);
        }
    }

    @Test void intensionSumOfMixedRelationAndBareVariableTerms_fallsBackToPredicateConstraint() throws IOException {
        // add(le(2,x),y): one term is a relation (le(2,x)), the other a bare variable -- neither
        // SumOrLinearRecognizer (needs every term to be a bare variable/weighted mul term) nor
        // RelationSumRecognizer (needs every term to dispatch as a relation; a bare variable leaf
        // never does, since intensionRelationalOperator(VAR) is null) can recognize the whole add,
        // so it falls all the way through to PredicateConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..9 </var>",
                "<intension> eq(z,add(le(2,x),y)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int xGe2 = digitOf(a, "x") >= 2 ? 1 : 0;
            assertThat(digitOf(a, "z")).isEqualTo(xGe2 + digitOf(a, "y"));
        }
    }

    @Test void intensionSumOfRelationsReified_indicatorTracksConstraintTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"b\"> 0..1 </var>",
                "<intension reifiedBy=\"b\"> eq(add(le(2,x),le(3,y)),1) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int xGe2 = digitOf(a, "x") >= 2 ? 1 : 0;
            int yGe3 = digitOf(a, "y") >= 3 ? 1 : 0;
            int b = digitOf(a, "b");
            assertThat(b == 1).as("x=%d, y=%d, b=%d", digitOf(a, "x"), digitOf(a, "y"), b).isEqualTo(xGe2 + yGe3 == 1);
        }
    }

    // ---- intension eq/ne(compound-relation, variable) channel recognition (ChannelRecognizer) ----

    @Test void intensionChannelNegated_routesThroughBinaryComparatorConstraint() throws IOException {
        // QueenAttacking-06.xml.lzma's own shape: ne(and(ne(q,x),or(...)),b) -- "b is the negation
        // of whether q and x are a queen's-move apart". Simplified here to and(ne(x,y),or(eq(x,1),
        // eq(y,2))) against a plain channel variable b, with ne (not eq) as the outer operator.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<intension> ne(and(ne(x,y),or(eq(x,1),eq(y,2))),b) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            boolean cond = x != y && (x == 1 || y == 2);
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d", x, y).isEqualTo(!cond);
        }
    }

    @Test void intensionChannelEquals_routesThroughBinaryComparatorConstraint() throws IOException {
        // Same shape as intensionChannelNegated but with eq (not ne) as the outer operator --
        // confirms ChannelRecognizer isn't hardcoded to negation, just whichever of EQ/NEQ the
        // outer node carries.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var>",
                "<intension> eq(and(ne(x,y),eq(x,1)),b) </intension>");
        assertThat(instance.csp().getConstraints()).anyMatch(c -> c instanceof BinaryComparatorConstraint);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            boolean cond = x != y && x == 1;
            assertThat(digitOf(a, "b") == 1).as("x=%d, y=%d", x, y).isEqualTo(cond);
        }
    }

    @Test void intensionChannelWrongOperator_fallsBackToPredicateConstraint() throws IOException {
        // lt(mul(x,y,w),z): ProductRecognizer now handles a three-operand mul (see its own
        // "distinct-factor" generalization), but only for EQ/LEQ/GEQ -- lt isn't one of those, so
        // it still declines here, and lt isn't eq/ne either -- ChannelRecognizer's own operator
        // guard declines before ever trying to resolve either side, exercising a genuinely
        // different decline path from the "operand unrecognizable" tests below.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..2 </var><var id=\"y\"> 1..2 </var><var id=\"w\"> 1..2 </var><var id=\"z\"> 0..10 </var>",
                "<intension> lt(mul(x,y,w),z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "x") * digitOf(a, "y") * digitOf(a, "w")).isLessThan(digitOf(a, "z"));
        }
    }

    @Test void intensionChannelLeftSideUnrecognizable_fallsBackToPredicateConstraint() throws IOException {
        // ne(mul(x,y,w),b): the operator matches (ne), but the left side is a three-operand mul --
        // neither dispatchable as a relation nor a bare variable -- so indicatorFor declines for it
        // without ever needing to look at the right side.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 1..2 </var><var id=\"y\"> 1..2 </var><var id=\"w\"> 1..2 </var><var id=\"b\"> 0..8 </var>",
                "<intension> ne(mul(x,y,w),b) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "b")).isNotEqualTo(digitOf(a, "x") * digitOf(a, "y") * digitOf(a, "w"));
        }
    }

    @Test void intensionChannelRightSideUnrecognizable_fallsBackToPredicateConstraint() throws IOException {
        // ne(and(ne(x,y),eq(x,1)),or(mul(p,q,r),eq(a,1))): the left side resolves fine via dispatch
        // (the same and(...) shape intensionChannelEquals recognizes) -- confirmed empirically to
        // stay tree.sons[0] here (xcsp3-tools' canonizer keeps a recognizable and(...) ahead of an
        // or(...) it's paired against, the same complexity-based reordering SumOrLinearRecognizer's
        // own doc describes for add/mul) -- but the right side, or(mul(p,q,r),eq(a,1)), is itself
        // unrecognizable: mul(p,q,r) alone can't dispatch (no operator-bearing wrapper), so
        // OrRecognizer's own resolveEachChild declines the whole or(...) even though its other
        // disjunct eq(a,1) would recognize fine alone. This exercises the "left succeeded, right
        // didn't" branch specifically -- distinct from intensionChannelLeftSideUnrecognizable's
        // left-side failure, where mul always sorts first regardless of what it's paired against.
        // p,q,r are all >= 1, so mul(p,q,r) is always nonzero -- meaning the outer intension's raw
        // arithmetic evaluation (the generic fallback that ends up handling this) always sees
        // or(...) as true, reducing the whole ne(...) to "and(...) must be false". ChannelRecognizer
        // resolves both sides read-only before reifying either, so the left side succeeding first
        // leaves no orphaned ReifiedConstraint behind once the right side is discovered to fail.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"a\"> 0..2 </var>"
                        + "<var id=\"p\"> 1..2 </var><var id=\"q\"> 1..2 </var><var id=\"r\"> 1..2 </var>",
                "<intension> ne(and(ne(x,y),eq(x,1)),or(mul(p,q,r),eq(a,1))) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment assignment : solutions) {
            int x = digitOf(assignment, "x");
            int y = digitOf(assignment, "y");
            assertThat(x != y && x == 1).as("x=%d, y=%d", x, y).isFalse();
        }
    }

    @Test void intensionChannelReified_indicatorTracksWholeChannelTruthValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..2 </var><var id=\"y\"> 0..2 </var><var id=\"b\"> 0..1 </var><var id=\"r\"> 0..1 </var>",
                "<intension reifiedBy=\"r\"> ne(and(ne(x,y),eq(x,1)),b) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            boolean cond = x != y && x == 1;
            int b = digitOf(a, "b");
            int r = digitOf(a, "r");
            assertThat(r == 1).as("x=%d, y=%d, b=%d", x, y, b).isEqualTo(b != (cond ? 1 : 0));
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

    @Test void intensionChainedEquality_routesThroughAndConstraint() throws IOException {
        // eq(x,y,z): XCSP3's eq generalizes to n-ary "all equal" for 3+ operands --
        // NaryEqualityRecognizer resolves each operand (here, three bare variables) and chains them
        // into x==y and y==z via one AndConstraint, rather than falling back to PredicateConstraint.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..3 </var><var id=\"y\"> 0..3 </var><var id=\"z\"> 0..3 </var>",
                "<intension> eq(x,y,z) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AndConstraint.class);
        assertThat(solutions(instance.csp())).hasSize(4);
    }

    @Test void intensionChainedEquality_withConstantOperand_routesThroughAndConstraint() throws IOException {
        // eq(x,y,299): a mix of two bare variables and a bare constant -- resolveVariable wraps the
        // constant in its own memoized singleton-domain auxiliary, the same way every other
        // resolveVariable caller gets one for free, so this chains into x==y and y==$const299.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 297..300 </var><var id=\"y\"> 297..300 </var>",
                "<intension> eq(x,y,299) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(AndConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(1);
        for (Assignment a : found) {
            assertThat(digitOf(a, "x")).isEqualTo(299);
            assertThat(digitOf(a, "y")).isEqualTo(299);
        }
    }

    @Test void intensionChainedEquality_unresolvableOperand_fallsBackToPredicateConstraint() throws IOException {
        // eq(x,y,mul(a,b)): the third operand is a product of two variables -- mul isn't one of
        // resolveVariable's own known compound operators -- so NaryEqualityRecognizer declines the
        // whole node rather than partially chaining just the resolvable operands.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..9 </var><var id=\"y\"> 0..9 </var><var id=\"a\"> 1..3 </var><var id=\"b\"> 1..3 </var>",
                "<intension> eq(x,y,mul(a,b)) </intension>");
        assertThat(instance.csp().getConstraints().iterator().next()).isInstanceOf(PredicateConstraint.class);
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).isNotEmpty();
        for (Assignment a : found) {
            int product = digitOf(a, "a") * digitOf(a, "b");
            assertThat(digitOf(a, "x")).isEqualTo(digitOf(a, "y"));
            assertThat(digitOf(a, "y")).isEqualTo(product);
        }
    }

    // ---- sqr / pow / min / max / imp / if (full XCSP3-core intension grammar coverage) --------------

    @Test void intensionSquare_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..30 </var>",
                "<intension> eq(sqr(x),y) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            assertThat(digitOf(a, "y")).isEqualTo(x * x);
        }
    }

    @Test void intensionPower_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..4 </var><var id=\"y\"> 0..70 </var>",
                "<intension> eq(pow(x,3),y) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            assertThat(digitOf(a, "y")).isEqualTo((int) Math.pow(x, 3));
        }
    }

    @Test void intensionMinOfPair_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..5 </var>",
                "<intension> eq(z,min(x,y)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            assertThat(digitOf(a, "z")).isEqualTo(Math.min(digitOf(a, "x"), digitOf(a, "y")));
        }
    }

    @Test void intensionMaxOfThree_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..5 </var><var id=\"y\"> 0..5 </var><var id=\"z\"> 0..5 </var><var id=\"w\"> 0..5 </var>",
                "<intension> eq(w,max(x,y,z)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            int z = digitOf(a, "z");
            assertThat(digitOf(a, "w")).isEqualTo(Math.max(x, Math.max(y, z)));
        }
    }

    @Test void intensionImplication_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"y\"> 0..1 </var>",
                "<intension> imp(eq(x,1),eq(y,1)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            int y = digitOf(a, "y");
            assertThat(x != 1 || y == 1).as("x=%d, y=%d", x, y).isTrue();
        }
    }

    @Test void intensionTernaryConditional_evaluatesCorrectly() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\"> 0..1 </var><var id=\"z\"> 0..1 </var>",
                "<intension> eq(z,if(eq(x,1),1,0)) </intension>");
        Set<Assignment> solutions = solutions(instance.csp());
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int x = digitOf(a, "x");
            assertThat(digitOf(a, "z")).isEqualTo(x == 1 ? 1 : 0);
        }
    }

    // ---- symbolic (string-valued) variable domains -------------------------------------------------------------------------

    @Test void symbolicDomain_solvesWithinDeclaredValues() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var>",
                "<intension> eq(x,B) </intension>");
        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp()).getSolution();
        assertThat(solution).isPresent();
        assertThat(stringOf(solution.get(), "x")).isEqualTo("B");
    }

    @Test void symbolicIntensionEqVarVar_solutionsMatch() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var><var id=\"y\" type=\"symbolic\"> A B C </var>",
                "<intension> eq(x,y) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(3);
        for (Assignment a : found) {
            assertThat(stringOf(a, "x")).isEqualTo(stringOf(a, "y"));
        }
    }

    @Test void symbolicIntensionNeVarVar_solutionsDiffer() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var><var id=\"y\" type=\"symbolic\"> A B C </var>",
                "<intension> ne(x,y) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(6);
        for (Assignment a : found) {
            assertThat(stringOf(a, "x")).isNotEqualTo(stringOf(a, "y"));
        }
    }

    @Test void symbolicIntensionEqVarConstant_variableOnLeft_pinsVariable() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var>",
                "<intension> eq(x,B) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(1);
        assertThat(stringOf(found.iterator().next(), "x")).isEqualTo("B");
    }

    @Test void symbolicIntensionEqVarConstant_variableOnRight_pinsVariable() throws IOException {
        // xcsp3-tools' own canonizer may reorder eq's operands, but a constant-on-the-left source
        // XML still needs to resolve correctly regardless of which side the library hands us.
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var>",
                "<intension> eq(B,x) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(1);
        assertThat(stringOf(found.iterator().next(), "x")).isEqualTo("B");
    }

    @Test void symbolicIntensionNeVarConstant_variableOnLeft_excludesValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var>",
                "<intension> ne(x,B) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(2);
        for (Assignment a : found) {
            assertThat(stringOf(a, "x")).isNotEqualTo("B");
        }
    }

    @Test void symbolicIntensionNeVarConstant_variableOnRight_excludesValue() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var>",
                "<intension> ne(B,x) </intension>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(2);
        for (Assignment a : found) {
            assertThat(stringOf(a, "x")).isNotEqualTo("B");
        }
    }

    // Reification of symbolic intension/allDifferent isn't reachable through real parsing at all --
    // xcsp3-tools' own loadCtr dispatch has no route for it (confirmed empirically, see
    // Xcsp3CallbackHandler#buildCtrIntension(String, XVarSymbolic[], XNodeParent)'s own Javadoc); it
    // throws the library's raw RuntimeException before either buildCtrXxx override is ever entered,
    // the same as completelyUnrecognisedConstruct_throwsRuntimeException below.

    @Test void symbolicIntensionWrongArity_throwsUnsupported() {
        // eq(x,y,z) is still TypeExpr.EQ, but with 3 sons rather than the 2 this handler supports.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B </var><var id=\"y\" type=\"symbolic\"> A B </var>"
                        + "<var id=\"z\" type=\"symbolic\"> A B </var>",
                "<intension> eq(x,y,z) </intension>"))
                .isInstanceOf(UnsupportedXcsp3ConstraintException.class);
    }

    // symbolicIntensionBothOperandsConstants (neither operand a variable) can't be reached through
    // real XML parsing -- xcsp3-tools' own CtrLoaderInteger.intension NPEs first on a
    // zero-variable-scope intension before our buildCtrIntension(XVarSymbolic...) is ever entered --
    // so it's a direct white-box test on Xcsp3CallbackHandlerTest instead; see
    // Xcsp3CallbackHandlerTest#buildCtrIntensionSymbolic_bothOperandsConstants_throwsUnsupported.

    @Test void symbolicAllDifferent_solutionsAllDistinct() throws IOException {
        Xcsp3Instance instance = parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var><var id=\"y\" type=\"symbolic\"> A B C </var>"
                        + "<var id=\"z\" type=\"symbolic\"> A B C </var>",
                "<allDifferent><list> x y z </list></allDifferent>");
        Set<Assignment> found = solutions(instance.csp());
        assertThat(found).hasSize(6);
        for (Assignment a : found) {
            assertThat(Set.of(stringOf(a, "x"), stringOf(a, "y"), stringOf(a, "z"))).hasSize(3);
        }
    }

    // ---- unsupported construct falls through to the library's own default --------------------------------------------------

    @Test void completelyUnrecognisedConstruct_throwsRuntimeException() {
        // extension over symbolic variables has no buildCtrExtension(XVarSymbolic[]...) override at
        // all, so this falls through to XCallbacks2's own default (unimplementedCase), not
        // UnsupportedXcsp3ConstraintException -- mdd, then clause, then a bare symbolic variable
        // domain (buildVarSymbolic) used to be this test's example, but all three are now
        // recognised, mapped constructs.
        assertThatThrownBy(() -> parseXml(
                "<var id=\"x\" type=\"symbolic\"> A B C </var><var id=\"y\" type=\"symbolic\"> A B C </var>",
                "<extension><list> x y </list><supports> (A,B)(B,C) </supports></extension>"))
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
