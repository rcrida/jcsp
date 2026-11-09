package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionSourceTest {

    private static DecisionSource replay() {
        return DecisionSource.Factory.REPLAY.create(Solver.Factory.FULL_PROPAGATION_INFERENCE,
                AdaptiveVariableSelector.Factory.INSTANCE, Assignment.empty());
    }

    @Test
    void replayFindsEveryVariableOfAProblemWhoseVariablesAreIndependent() {
        var builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 4));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 4));
        Variable<Integer> c = builder.createVariable("c", IntRangeDomain.of(0, 4));
        ConstraintSatisfactionProblem csp = builder.build();

        List<Variable<?>> decisions = replay().decisionsOf(csp, Assignment.of(Map.of(a, 1, b, 2, c, 3)));

        assertThat(decisions).containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    void replayLeavesOutAVariablePropagationSetsFromTheOthers() {
        var builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer> x = builder.createVariable("x", IntRangeDomain.of(0, 4));
        Variable<Integer> y = builder.createVariable("y", IntRangeDomain.of(1, 5));
        builder.offsetConstraint(x, 1, Operator.EQ, y);
        ConstraintSatisfactionProblem csp = builder.build();

        List<Variable<?>> decisions = replay().decisionsOf(csp, Assignment.of(Map.of(x, 2, y, 3)));

        // Whichever is branched on first, the other follows: one decision, not two.
        assertThat(decisions).hasSize(1).isSubsetOf(x, y);
    }

    @Test
    void replayFollowsTheSolutionRatherThanTheDomainOrder() {
        var builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 9));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 9));
        builder.notEqualsConstraint(a, b);
        ConstraintSatisfactionProblem csp = builder.build();

        List<Variable<?>> decisions = replay().decisionsOf(csp, Assignment.of(Map.of(a, 9, b, 8)));

        assertThat(decisions).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void declaredReturnsTheNamedVariablesInOrderForAnySolution() {
        var builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 4));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 4));
        ConstraintSatisfactionProblem csp = builder.build();
        DecisionSource source = DecisionSource.Factory.declared(List.of(b, a))
                .create(Solver.Factory.FULL_PROPAGATION_INFERENCE, AdaptiveVariableSelector.Factory.INSTANCE, Assignment.empty());

        assertThat(source.decisionsOf(csp, Assignment.of(Map.of(a, 1, b, 2)))).containsExactly(b, a);
        assertThat(source.decisionsOf(csp, Assignment.of(Map.of(a, 3, b, 0)))).containsExactly(b, a);
    }

    @Test
    void declaredCopiesWhatItIsGiven() {
        var builder = ConstraintSatisfactionProblem.builder();
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 4));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 4));
        ConstraintSatisfactionProblem csp = builder.build();
        List<Variable<?>> mutable = new ArrayList<>(List.of(a));
        DecisionSource source = DecisionSource.Factory.declared(mutable)
                .create(Solver.Factory.FULL_PROPAGATION_INFERENCE, AdaptiveVariableSelector.Factory.INSTANCE, Assignment.empty());

        mutable.add(b);

        assertThat(source.decisionsOf(csp, Assignment.of(Map.of(a, 1, b, 2)))).containsExactly(a);
    }
}
