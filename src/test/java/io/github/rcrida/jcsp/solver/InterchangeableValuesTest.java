package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Valuation;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.RelationLogicConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InterchangeableValuesTest {
    private final Variable.Factory variables = new Variable.Factory();
    private final Variable<Integer> x = variables.create("x");
    private final Variable<Integer> y = variables.create("y");
    private final Variable<Integer> z = variables.create("z");

    private ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder threeColours() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .variableDomain(z, IntRangeDomain.of(1, 3));
    }

    private static RelationLogicConstraint.VariableLiteral literal(Variable<Integer> left, Operator operator, Variable<Integer> right) {
        return new RelationLogicConstraint.VariableLiteral(left, operator, right);
    }

    /** All pairs over {@code {1, 2, 3}} for {@code (x, y)} that {@code keep} accepts. */
    private Set<Valuation> pairs(java.util.function.BiPredicate<Integer, Integer> keep) {
        var tuples = new java.util.LinkedHashSet<Valuation>();
        for (int a = 1; a <= 3; a++) {
            for (int b = 1; b <= 3; b++) {
                if (keep.test(a, b)) tuples.add(Valuation.of(Map.of(x, a, y, b)));
            }
        }
        return tuples;
    }

    // --- hold ---

    @Test
    void equalityConstraintsOverOneDomain_hold() {
        var csp = threeColours().notEqualsConstraint(x, y).equalsConstraint(y, z).allDiffConstraint(Set.of(x, y))
                .constraint(RelationLogicConstraint.of(literal(x, Operator.NEQ, y), LogicOperator.OR, literal(x, Operator.EQ, z)))
                .build();
        assertThat(InterchangeableValues.hold(csp)).isTrue();
    }

    @Test
    void tablesClosedUnderRenaming_hold() {
        var csp = threeColours()
                .constraint(NaryTuplesConstraint.of(pairs((a, b) -> !a.equals(b))))
                .constraint(NaryConflictTuplesConstraint.of(pairs(Integer::equals)))
                .build();
        assertThat(InterchangeableValues.hold(csp)).isTrue();
    }

    @Test
    void learnedNogood_isIgnored() {
        var csp = threeColours().notEqualsConstraint(x, y).build()
                .withNogoods(Set.of(GroundNogoodConstraint.of(Map.of(x, 1, y, 2))));
        assertThat(InterchangeableValues.hold(csp)).isTrue();
    }

    @Test
    void tableMissingARenamedTuple_doesNotHold() {
        var csp = threeColours().constraint(NaryTuplesConstraint.of(pairs((a, b) -> a < b))).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void tableFarSmallerThanItsPatternsRenamings_doesNotHold() {
        // One all-different triple of 3! renamings: counting stops once past the table's one tuple.
        var csp = threeColours().constraint(NaryTuplesConstraint.of(Set.of(Valuation.of(Map.of(x, 1, y, 2, z, 3))))).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void tableNamingAValueOutsideTheDomain_doesNotHold() {
        var csp = threeColours().constraint(NaryTuplesConstraint.of(Set.of(Valuation.of(Map.of(x, 1, y, 9))))).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void relationNamingAValue_doesNotHold() {
        var valueOnLeft = threeColours().constraint(RelationLogicConstraint.of(
                new RelationLogicConstraint.ValueLiteral(x, Operator.EQ, 1), LogicOperator.OR, literal(y, Operator.NEQ, z))).build();
        var valueOnRight = threeColours().constraint(RelationLogicConstraint.of(
                literal(y, Operator.NEQ, z), LogicOperator.OR, new RelationLogicConstraint.ValueLiteral(x, Operator.EQ, 1))).build();
        assertThat(InterchangeableValues.hold(valueOnLeft)).isFalse();
        assertThat(InterchangeableValues.hold(valueOnRight)).isFalse();
    }

    @Test
    void relationOrderingVariables_doesNotHold() {
        var csp = threeColours().constraint(RelationLogicConstraint.of(
                literal(x, Operator.LT, y), LogicOperator.OR, literal(y, Operator.NEQ, z))).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void anyOtherConstraint_doesNotHold() {
        var csp = threeColours().comparatorConstraint(x, Operator.LEQ, y).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void differingDomains_doNotHold() {
        var csp = threeColours().variableDomain(z, IntRangeDomain.of(1, 4)).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void aSingleValue_doesNotHold() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, DiscreteDomain.of(1)).variableDomain(y, DiscreteDomain.of(1)).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    @Test
    void noVariables_doNotHold() {
        assertThat(InterchangeableValues.hold(ConstraintSatisfactionProblem.builder().build())).isFalse();
    }

    @Test
    void continuousDomain_doesNotHold() {
        Variable<Double> d = variables.create("d");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(d, IntervalDomain.of(0, 1)).build();
        assertThat(InterchangeableValues.hold(csp)).isFalse();
    }

    // --- restrict ---

    @Test
    void restrict_keepsHeldValuesAndTheFirstUnheldOne() {
        Valuation assignment = Valuation.of(Map.of(x, 2, y, 4));
        assertThat(InterchangeableValues.restrict(List.of(1, 2, 3, 4, 5), assignment)).containsExactly(1, 2, 4);
    }

    // --- in the search ---

    private static DomWdegLubySearch search() {
        return DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build();
    }

    @Test
    void getSolution_stillRefutesAnUncolourableGraph() {
        // K4 with three colours: no colouring, and the search must say so, not stop early.
        Variable<Integer> w = variables.create("w");
        var builder = threeColours().variableDomain(w, IntRangeDomain.of(1, 3));
        List<Variable<Integer>> clique = List.of(w, x, y, z);
        for (int i = 0; i < clique.size(); i++) {
            for (int j = i + 1; j < clique.size(); j++) builder.notEqualsConstraint(clique.get(i), clique.get(j));
        }
        assertThat(search().getSolution(builder.build())).isEmpty();
    }

    @Test
    void getSolutions_stillReturnsEverySymmetricSolution() {
        // A triangle with three colours has 3! colourings, all renamings of one another.
        var csp = threeColours().notEqualsConstraint(x, y).notEqualsConstraint(y, z).notEqualsConstraint(x, z).build();
        assertThat(search().getSolution(csp)).isPresent();
        assertThat(search().getSolutions(csp)).hasSize(6);
    }
}
