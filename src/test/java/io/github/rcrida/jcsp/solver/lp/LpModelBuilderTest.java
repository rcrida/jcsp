package io.github.rcrida.jcsp.solver.lp;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryStarredTuplesConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class LpModelBuilderTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void sumBoundConstraint_geqLowerBound_findsCheaperVariable() {
        // minimize 2x+3y s.t. x+y>=4, x,y in [0,10]; cheapest is all-x: x=4,y=0, cost=8
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 10))
                .variableDomain(y, IntRangeDomain.of(0, 10))
                .sumConstraint(Set.of(x, y), Operator.GEQ, 4)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 2.0).coefficient(y, 3.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(8.0, within(1e-6));
        assertThat(bound.get().solution().get(x)).isCloseTo(4.0, within(1e-6));
        assertThat(bound.get().solution().get(y)).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void linearBoundConstraint_eqBound_forcesMinimum() {
        // minimize x s.t. 2x+y=10, y in [0,5], x in [0,10] -> x >= 2.5, achieved at y=5
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 10))
                .variableDomain(y, IntRangeDomain.of(0, 5))
                .linearConstraint(Map.of(x, 2, y, 1), Operator.EQ, 10)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(2.5, within(1e-6));
    }

    @Test
    void sumVariableConstraint_targetVariable_translatesTargetWithNegativeCoefficient() {
        // v1=1, v2=2 (fixed), v1+v2<=t, t in [0,10]; minimize t -> t=3
        Variable<Integer> v1 = F.create("v1");
        Variable<Integer> v2 = F.create("v2");
        Variable<Integer> t = F.create("t");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 1))
                .variableDomain(v2, IntRangeDomain.of(2, 2))
                .variableDomain(t, IntRangeDomain.of(0, 10))
                .sumConstraint(Set.of(v1, v2), Operator.LEQ, t)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(t, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(3.0, within(1e-6));
    }

    @Test
    void linearVariableConstraint_targetVariable_translatesWeightedTermsAndTarget() {
        // v1=2, v2=1 (fixed); 2*v1+3*v2<=t, t in [0,20]; minimize t -> t=7
        Variable<Integer> v1 = F.create("v1");
        Variable<Integer> v2 = F.create("v2");
        Variable<Integer> t = F.create("t");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(2, 2))
                .variableDomain(v2, IntRangeDomain.of(1, 1))
                .variableDomain(t, IntRangeDomain.of(0, 20))
                .linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, t)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(t, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(7.0, within(1e-6));
    }

    @Test
    void infeasibleRelaxation_returnsEmpty() {
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 1))
                .variableDomain(y, IntRangeDomain.of(0, 1))
                .sumConstraint(Set.of(x, y), Operator.GEQ, 5)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        assertThat(LpModelBuilder.solve(csp, objective)).isEmpty();
    }

    @Test
    void nonPropagatingOperator_rowSkipped() {
        // A NEQ sum constraint isn't modelled (mirrors SumBoundConstraint's own propagator, which
        // treats NEQ as a no-op) -- the LP bound reflects only the box constraint on x.
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(2, 10))
                .variableDomain(y, IntRangeDomain.of(0, 10))
                .sumConstraint(Set.of(x, y), Operator.NEQ, 4)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(2.0, within(1e-6));
    }

    @Test
    void nonPropagatingOperator_linearBoundConstraintRowSkipped() {
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(2, 10))
                .variableDomain(y, IntRangeDomain.of(0, 10))
                .linearConstraint(Map.of(x, 1, y, 1), Operator.LT, 4)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(2.0, within(1e-6));
    }

    @Test
    void nonPropagatingOperator_sumVariableConstraintRowSkipped() {
        Variable<Integer> v1 = F.create("v1");
        Variable<Integer> v2 = F.create("v2");
        Variable<Integer> t = F.create("t");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 1))
                .variableDomain(v2, IntRangeDomain.of(2, 2))
                .variableDomain(t, IntRangeDomain.of(0, 10))
                .sumConstraint(Set.of(v1, v2), Operator.GT, t)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(t, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void nonPropagatingOperator_linearVariableConstraintRowSkipped() {
        Variable<Integer> v1 = F.create("v1");
        Variable<Integer> v2 = F.create("v2");
        Variable<Integer> t = F.create("t");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(2, 2))
                .variableDomain(v2, IntRangeDomain.of(1, 1))
                .variableDomain(t, IntRangeDomain.of(0, 20))
                .linearConstraint(Map.of(v1, 2, v2, 3), Operator.NEQ, t)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(t, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void nonLinearConstraintIgnored_boundReflectsOnlyBoxConstraints() {
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(2, 10))
                .variableDomain(y, IntRangeDomain.of(0, 10))
                .notEqualsConstraint(x, y)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(2.0, within(1e-6));
    }

    @Test
    void noRelevantVariables_returnsConstantWithoutBuildingModel() {
        Variable<Integer> x = F.create("x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 10))
                .build();
        LinearObjective objective = LinearObjective.builder().constant(42.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).contains(new LpBound(42.0, Map.of()));
    }

    // ---- assignment relaxation (GlobalCardinalityConstraint + function-table linkage) --------------

    private static GlobalCardinalityConstraint.OccurrenceRange atMostOne() {
        return new GlobalCardinalityConstraint.OccurrenceRange(0, 1);
    }

    @Test
    void gccWithFunctionalLookupTables_assignmentRelaxationTightensBoundBeyondBox() {
        // Two items each pick an option 0,1,2; both prefer option 0 (gain 10) over 1/2 (gain 1),
        // but the GCC forbids two items sharing an option. True joint optimum is 10+1=11 -- a
        // naive per-variable box bound (each g_i independently maxed at 10) would allow 20.
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> s1 = F.create("s1");
        Variable<Integer> g0 = F.create("g0");
        Variable<Integer> g1 = F.create("g1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 2))
                .variableDomain(s1, IntRangeDomain.of(0, 2))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .variableDomain(g1, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0, s1),
                        Map.of(0, atMostOne(), 1, atMostOne(), 2, atMostOne()))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(s0, 0, g0, 10)),
                        Assignment.of(Map.of(s0, 1, g0, 1)),
                        Assignment.of(Map.of(s0, 2, g0, 1))))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(s1, 0, g1, 10)),
                        Assignment.of(Map.of(s1, 1, g1, 1)),
                        Assignment.of(Map.of(s1, 2, g1, 1))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, -1.0).coefficient(g1, -1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(-11.0, within(1e-6));
    }

    @Test
    void sameFunctionTablesWithoutGcc_boundStaysLooseBoxOnly() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> s1 = F.create("s1");
        Variable<Integer> g0 = F.create("g0");
        Variable<Integer> g1 = F.create("g1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 2))
                .variableDomain(s1, IntRangeDomain.of(0, 2))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .variableDomain(g1, IntRangeDomain.of(0, 10))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(s0, 0, g0, 10)),
                        Assignment.of(Map.of(s0, 1, g0, 1)),
                        Assignment.of(Map.of(s0, 2, g0, 1))))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(s1, 0, g1, 10)),
                        Assignment.of(Map.of(s1, 1, g1, 1)),
                        Assignment.of(Map.of(s1, 2, g1, 1))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, -1.0).coefficient(g1, -1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(-20.0, within(1e-6));
    }

    @Test
    void gccPresentButNoTableConstraints_boundUnaffected() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> s1 = F.create("s1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 2))
                .variableDomain(s1, IntRangeDomain.of(0, 2))
                .globalCardinalityRangeConstraint(Set.of(s0, s1),
                        Map.of(0, atMostOne(), 1, atMostOne(), 2, atMostOne()))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(s0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void tableSharingTwoGccVariables_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> s1 = F.create("s1");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 1))
                .variableDomain(s1, IntRangeDomain.of(0, 1))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0, s1), Map.of(0, atMostOne(), 1, atMostOne()))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(s0, 0, s1, 1, g0, 5)),
                        Assignment.of(Map.of(s0, 1, s1, 0, g0, 7))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void ambiguousTable_sameKeyValueTwice_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 0))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne()))
                .starredTuplesConstraint(Set.of(
                        Map.of(s0, 0, g0, 3),
                        Map.of(s0, 0, g0, 7)))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void starredKeyColumn_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 0))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne()))
                .starredTuplesConstraint(Set.of(Map.of(s0, NaryStarredTuplesConstraint.STAR, g0, 3)))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void objectiveVariableStarredForOneKeyValue_noLinkage() {
        // g0 is starred (undetermined) when s0=1, so it can't be soundly linked at all --
        // otherwise selecting s0=1 in the LP would wrongly force g0 to 0.
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 1))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne(), 1, atMostOne()))
                .starredTuplesConstraint(Set.of(
                        Map.of(s0, 0, g0, 9),
                        Map.of(s0, 1, g0, NaryStarredTuplesConstraint.STAR)))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void tableWithNoObjectiveRelevantColumn_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> junk = F.create("junk");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 0))
                .variableDomain(junk, IntRangeDomain.of(0, 10))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne()))
                .tuplesConstraint(Set.of(Assignment.of(Map.of(s0, 0, junk, 4))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void keyDomainNotFullyCoveredByTuples_noLinkage() {
        // s0's domain has 0 and 1, but the table only has a tuple for 0 -- table's own GAC
        // propagation hasn't (yet) narrowed the domain to match, so trusting it would be unsound.
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 1))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne(), 1, atMostOne()))
                .tuplesConstraint(Set.of(Assignment.of(Map.of(s0, 0, g0, 9))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void tooManyLiveTuples_exceedsCap_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        int tupleCount = 65;
        Set<Assignment> tuples = IntStream.range(0, tupleCount)
                .mapToObj(i -> Assignment.of(Map.of(s0, i, g0, i)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, tupleCount - 1))
                .variableDomain(g0, IntRangeDomain.of(0, tupleCount - 1))
                .globalCardinalityRangeConstraint(Set.of(s0),
                        IntStream.range(0, tupleCount).boxed()
                                .collect(Collectors.toMap(i -> i, i -> atMostOne(), (a, b) -> a, LinkedHashMap::new)))
                .tuplesConstraint(tuples)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void noLiveTuples_allFilteredOutByCurrentDomain_noLinkage() {
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 1))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne(), 1, atMostOne()))
                .tuplesConstraint(Set.of(Assignment.of(Map.of(s0, 5, g0, 9))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, 1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void starredTableWithDeadTupleFilteredByDomain_stillFormsLinkage() {
        // s0's domain excludes 2, so that tuple is filtered out as not live; the remaining live
        // tuples still exactly cover the domain and form a valid linkage.
        Variable<Integer> s0 = F.create("s0");
        Variable<Integer> g0 = F.create("g0");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, IntRangeDomain.of(0, 1))
                .variableDomain(g0, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(s0), Map.of(0, atMostOne(), 1, atMostOne()))
                .starredTuplesConstraint(Set.of(
                        Map.of(s0, 0, g0, 9),
                        Map.of(s0, 1, g0, 2),
                        Map.of(s0, 2, g0, 100)))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(g0, -1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(-9.0, within(1e-6));
    }

    @Test
    void gccWithMinimumOccurrenceRange_cardinalityRowGetsLowerBound() {
        // A tracked value's occurrence range has a positive minimum (not just LEQ-style [0,max]),
        // exercising the cardinality row's lower-bound branch alongside its upper bound.
        Variable<Integer> item = F.create("item");
        Variable<Integer> gain = F.create("gain");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(item, IntRangeDomain.of(0, 0))
                .variableDomain(gain, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(item),
                        Map.of(0, new GlobalCardinalityConstraint.OccurrenceRange(1, 1)))
                .tuplesConstraint(Set.of(Assignment.of(Map.of(item, 0, gain, 5))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(gain, -1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(-5.0, within(1e-6));
    }

    @Test
    void gccVariableMissingTrackedValue_cardinalityRowSkipsIt() {
        // itemB's domain is fixed to {0}, so it has no indicator for tracked value 1 -- the
        // cardinality row for value 1 must skip it rather than fail. itemB's one-hot forces it
        // onto option 0, which then (via the GCC's at-most-one-per-option cap) blocks itemA from
        // also using option 0, so itemA is forced to option 1 (gain 1) despite preferring option 0
        // (gain 10).
        Variable<Integer> itemA = F.create("itemA");
        Variable<Integer> itemB = F.create("itemB");
        Variable<Integer> gainA = F.create("gainA");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(itemA, IntRangeDomain.of(0, 1))
                .variableDomain(itemB, IntRangeDomain.of(0, 0))
                .variableDomain(gainA, IntRangeDomain.of(0, 10))
                .globalCardinalityRangeConstraint(Set.of(itemA, itemB), Map.of(0, atMostOne(), 1, atMostOne()))
                .tuplesConstraint(Set.of(
                        Assignment.of(Map.of(itemA, 0, gainA, 10)),
                        Assignment.of(Map.of(itemA, 1, gainA, 1))))
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(gainA, -1.0).build();

        var bound = LpModelBuilder.solve(csp, objective);

        assertThat(bound).isPresent();
        assertThat(bound.get().lowerBound()).isCloseTo(-1.0, within(1e-6));
    }
}
