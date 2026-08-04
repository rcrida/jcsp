package io.github.rcrida.jcsp.solver.lp;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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
}
