package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.AndConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Recognizes {@code or(and(eq(b,1),R), and(eq(b,0),S))}, where {@code S} is the complement of the
 * binary relation {@code R}, as the single reification {@code b <-> R}: a {@link
 * ReifiedConstraint} on {@code b}'s shared boolean indicator, alongside {@code 0 <= b <= 1} when
 * {@code b} is declared wider. Each {@code and}'s two children may come in either order.
 * <p>
 * Relations are compared as {@link Literal}s, so a complement written with its operands swapped is
 * still recognized: the canonizer writes {@code ge(x,y)} as {@code le(y,x)}, so {@code not
 * le(x,y)} arrives as {@code lt(y,x)}. Declines anything else, and a {@code b} that also occurs in
 * {@code R}, leaving the {@code or} to {@link OrRecognizer}'s decomposition into two reified
 * conjunctions.
 */
final class ReifiedRelationRecognizer implements ConstraintRecognizer {

    private final Xcsp3CallbackHandler handler;
    private final Function<XNode<XVarInteger>, Optional<Constraint>> dispatch;

    ReifiedRelationRecognizer(@NonNull Xcsp3CallbackHandler handler,
                              @NonNull Function<XNode<XVarInteger>, Optional<Constraint>> dispatch) {
        this.handler = handler;
        this.dispatch = dispatch;
    }

    private static final Set<TypeExpr> RELATIONS = EnumSet.of(TypeExpr.EQ, TypeExpr.NE, TypeExpr.LE, TypeExpr.LT);

    /** One {@code and(eq(b,value), relation)} branch. */
    private record Branch(Variable<Integer> indicator, int value, XNode<XVarInteger> relation, Literal literal) {
    }

    /**
     * A binary relation in a form shared with its complement: {@code eq} and {@code ne} as a
     * positive and a negative {@code eq} atom over an unordered pair, {@code le(a,b)} as a positive
     * {@code le} atom and {@code lt(a,b)} as the negative of {@code le(b,a)}.
     */
    private record Literal(Atom atom, boolean positive) {
    }

    private record Atom(String relation, String left, String right) {
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        if (tree.getType() != TypeExpr.OR || tree.sons.length != 2) return Optional.empty();
        for (Branch first : branches(tree.sons[0])) {
            for (Branch second : branches(tree.sons[1])) {
                if (first.indicator() != second.indicator() || first.value() == second.value()
                        || !first.literal().atom().equals(second.literal().atom())
                        || !Boolean.logicalXor(first.literal().positive(), second.literal().positive())) {
                    continue;
                }
                Variable<Integer> b = first.indicator();
                XNode<XVarInteger> whenTrue = new Branch[]{first, second}[1 - first.value()].relation();
                return dispatch.apply(whenTrue)
                        .filter(relation -> !relation.getVariables().contains(b))
                        .map(relation -> reify(b, relation));
            }
        }
        return Optional.empty();
    }

    /**
     * {@code b <-> relation} on {@code b}'s boolean indicator. The {@code or} also confines
     * {@code b} to 0 or 1, which the indicator alone does not say when {@code b} is declared wider.
     */
    private Constraint reify(Variable<Integer> b, Constraint relation) {
        Constraint reified = ReifiedConstraint.of(handler.booleanIndicatorFor(b), relation);
        if (Xcsp3CallbackHandler.isBooleanBounds(handler.boundsOf(b))) return reified;
        return AndConstraint.of(Set.of(UnaryComparatorConstraint.of(b, Operator.GEQ, 0),
                UnaryComparatorConstraint.of(b, Operator.LEQ, 1), reified));
    }

    /** Every way {@code node} reads as {@code and(eq(b,0|1), relation)}, taking either child as the literal. */
    private List<Branch> branches(XNode<XVarInteger> node) {
        List<Branch> branches = new ArrayList<>(2);
        if (node.getType() != TypeExpr.AND || node.sons.length != 2) return branches;
        for (int literal = 0; literal < 2; literal++) {
            XNode<XVarInteger> eq = node.sons[literal];
            XNode<XVarInteger> relation = node.sons[1 - literal];
            Optional<Literal> literalOfRelation = literal(relation);
            if (eq.getType() != TypeExpr.EQ || eq.sons.length != 2 || literalOfRelation.isEmpty()) continue;
            Optional<Variable<Integer>> indicator = handler.asVariable(eq.sons[0]);
            Optional<Integer> value = Xcsp3CallbackHandler.asConstant(eq.sons[1]);
            if (indicator.isPresent() && value.isPresent() && (value.get() == 0 || value.get() == 1)) {
                branches.add(new Branch(indicator.get(), value.get(), relation, literalOfRelation.get()));
            }
        }
        return branches;
    }

    /** {@code node} as a {@link Literal}, or empty when it is not one of {@link #RELATIONS} over two operands. */
    private static Optional<Literal> literal(XNode<XVarInteger> node) {
        if (!RELATIONS.contains(node.getType()) || node.sons.length != 2) return Optional.empty();
        String a = node.sons[0].toString();
        String b = node.sons[1].toString();
        List<String> pair = Stream.of(a, b).sorted().toList();
        return Optional.of(switch (node.getType()) {
            case EQ -> new Literal(new Atom("eq", pair.get(0), pair.get(1)), true);
            case NE -> new Literal(new Atom("eq", pair.get(0), pair.get(1)), false);
            case LE -> new Literal(new Atom("le", a, b), true);
            default -> new Literal(new Atom("le", b, a), false);
        });
    }
}
