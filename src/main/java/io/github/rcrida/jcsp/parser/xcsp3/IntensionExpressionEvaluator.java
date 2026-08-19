package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.LongStream;

/**
 * Converts an XCSP3 {@code <intension>} expression tree -- already parsed into an {@link XNode}
 * AST by {@code xcsp3-tools} -- into a {@link Predicate}{@code <Assignment>} suitable for {@link
 * io.github.rcrida.jcsp.ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder#predicateConstraint}.
 * Covers the arithmetic ({@code neg}/{@code abs}/{@code add}/{@code sub}/{@code mul}/{@code
 * div}/{@code mod}/{@code dist}), relational ({@code eq}/{@code ne}/{@code lt}/{@code le}/{@code
 * ge}/{@code gt}), boolean ({@code not}/{@code and}/{@code or}/{@code xor}/{@code iff}), and
 * set-membership ({@code in}/{@code notin} against a {@code set(...)} whose members are themselves
 * arbitrary sub-expressions -- constants, variables, or nested arithmetic like {@code sub(x,1)} --
 * evaluated per member against the current assignment, same as any other operand) operators that
 * appear in this project's XCSP3 test fixtures -- not the full XCSP3 expression language (e.g.
 * {@code min}/{@code max}/{@code imp}/{@code if} are not handled). An operator outside this set
 * throws {@link UnsupportedXcsp3ConstraintException} rather than silently mis-evaluating.
 */
final class IntensionExpressionEvaluator {

    private IntensionExpressionEvaluator() {
    }

    static Predicate<Assignment> toPredicate(XNode<XVarInteger> tree, Map<String, Variable<Integer>> variablesByName) {
        return assignment -> evaluate(tree, assignment, variablesByName) != 0;
    }

    // Package-private (not private): the "unsupported leaf type" branch below is unreachable through
    // any real XCSP3 intension tree over XVarInteger scope (only VAR/LONG leaves ever occur), so
    // IntensionExpressionEvaluatorTest exercises it directly with a hand-built XNode.specialLeaf(...).
    static long evaluate(XNode<XVarInteger> node, Assignment assignment, Map<String, Variable<Integer>> variablesByName) {
        if (node instanceof XNodeLeaf<XVarInteger> leaf) {
            if (leaf.getType() == TypeExpr.VAR) {
                Variable<Integer> variable = variablesByName.get(((XVarInteger) leaf.value).id());
                return assignment.getValue(variable).orElseThrow();
            }
            if (leaf.getType() == TypeExpr.LONG) {
                return (Long) leaf.value;
            }
            throw new UnsupportedXcsp3ConstraintException("Unsupported intension leaf type: " + leaf.getType());
        }
        // in/notin's second operand is a set(...) literal, not a scalar expression -- evaluating it
        // through the generic operand loop below (which expects every son to reduce to one long via
        // applyOperator) would fail on the SET node itself, so it's intercepted here instead.
        if (node.getType() == TypeExpr.IN || node.getType() == TypeExpr.NOTIN) {
            long value = evaluate(node.sons[0], assignment, variablesByName);
            boolean contains = evaluateSet(node.sons[1], assignment, variablesByName).contains(value);
            return (node.getType() == TypeExpr.IN ? contains : !contains) ? 1 : 0;
        }
        long[] operands = new long[node.sons.length];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = evaluate(node.sons[i], assignment, variablesByName);
        }
        return applyOperator(node.getType(), operands);
    }

    /**
     * Evaluates a {@code set(...)} node's members against the current assignment. Each member is
     * itself an arbitrary sub-expression (constant, variable, or nested arithmetic like {@code
     * sub(x,1)}), so this just delegates to {@link #evaluate} per member rather than restricting to
     * one leaf kind -- the set's own contents can therefore vary across assignments (e.g. a set of
     * values adjacent to a variable's current position), not just across distinct calls.
     */
    private static Set<Long> evaluateSet(
            XNode<XVarInteger> node, Assignment assignment, Map<String, Variable<Integer>> variablesByName) {
        if (node.getType() != TypeExpr.SET) {
            throw new UnsupportedXcsp3ConstraintException("in/notin requires a literal set(...) operand, got: " + node.getType());
        }
        Set<Long> values = new LinkedHashSet<>();
        for (XNode<XVarInteger> son : node.sons) {
            values.add(evaluate(son, assignment, variablesByName));
        }
        return values;
    }

    private static long applyOperator(TypeExpr type, long[] operands) {
        return switch (type) {
            case NEG -> -operands[0];
            case ABS -> Math.abs(operands[0]);
            case DIST -> Math.abs(operands[0] - operands[1]);
            case ADD -> LongStream.of(operands).sum();
            case SUB -> operands[0] - operands[1];
            case MUL -> LongStream.of(operands).reduce(1L, (a, b) -> a * b);
            case DIV -> operands[0] / operands[1];
            case MOD -> operands[0] % operands[1];
            case EQ -> allEqual(operands) ? 1 : 0;
            case NE -> operands[0] != operands[1] ? 1 : 0;
            case LT -> operands[0] < operands[1] ? 1 : 0;
            case LE -> operands[0] <= operands[1] ? 1 : 0;
            case GE -> operands[0] >= operands[1] ? 1 : 0;
            case GT -> operands[0] > operands[1] ? 1 : 0;
            case NOT -> operands[0] == 0 ? 1 : 0;
            case AND -> LongStream.of(operands).allMatch(v -> v != 0) ? 1 : 0;
            case OR -> LongStream.of(operands).anyMatch(v -> v != 0) ? 1 : 0;
            // XCSP3 core: true iff an odd number of operands are true (n-ary parity, generalising
            // binary xor).
            case XOR -> LongStream.of(operands).filter(v -> v != 0).count() % 2 == 1 ? 1 : 0;
            // XCSP3 core: true iff every operand shares the same truth value -- operationally
            // identical to EQ (allEqual), just restricted to boolean-typed operands.
            case IFF -> allEqual(operands) ? 1 : 0;
            default -> throw new UnsupportedXcsp3ConstraintException("Unsupported intension operator: " + type);
        };
    }

    private static boolean allEqual(long[] operands) {
        for (int i = 1; i < operands.length; i++) {
            if (operands[i] != operands[0]) return false;
        }
        return true;
    }
}
