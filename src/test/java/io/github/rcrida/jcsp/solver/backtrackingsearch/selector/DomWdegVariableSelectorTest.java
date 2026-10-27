package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DomWdegVariableSelectorTest {
    private final Variable.Factory variableFactory = new Variable.Factory();


    private final Variable<Integer> v1 = variableFactory.create("v1");
    private final Variable<Integer> v2 = variableFactory.create("v2");
    private final Variable<Integer> v3 = variableFactory.create("v3");

    /** Declares variables in call order, which is the order {@code select} breaks ties in. */
    private static final class Problem {
        private final ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder =
                ConstraintSatisfactionProblem.builder();

        Problem variable(Variable<Integer> variable, int domainSize) {
            builder.variableDomain(variable, IntRangeDomain.of(1, domainSize));
            return this;
        }

        Problem differ(Variable<Integer> left, Variable<Integer> right) {
            builder.notEqualsConstraint(left, right);
            return this;
        }

        ConstraintSatisfactionProblem build() {
            return builder.build();
        }
    }

    private static DomWdegVariableSelector selectorFor(ConstraintSatisfactionProblem csp) {
        return new DomWdegVariableSelector(new HashSet<>(csp.getConstraints()));
    }

    @SafeVarargs
    private static Assignment assigned(Variable<Integer>... variables) {
        Map<Variable<Integer>, Integer> values = new LinkedHashMap<>();
        for (Variable<Integer> variable : variables) values.put(variable, 1);
        return Assignment.of(values);
    }

    @Test
    void selectsVariableWithSmallestDomWdegRatio() {
        // v1: 1 / c12 = 1.0 (wins); v2: 4 / (c12 + c23) = 2.0; v3: 3 / c23 = 3.0
        var csp = new Problem().variable(v1, 1).variable(v2, 4).variable(v3, 3).differ(v1, v2).differ(v2, v3).build();

        assertThat(selectorFor(csp).select(csp, Assignment.empty())).isEqualTo(v1);
    }

    @Test
    void onConflictBoostsActiveConstraint() {
        // Unboosted, v2 is 8/2 = 4.0 and loses to v3 at 3/1 = 3.0. Boosting c12 to 2 makes v2
        // 8/3 = 2.67, which wins; v1 (10/2 = 5.0) stays behind.
        var csp = new Problem().variable(v1, 10).variable(v2, 8).variable(v3, 3).differ(v1, v2).differ(v2, v3).build();
        var selector = selectorFor(csp);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v3);

        selector.onConflict(v1, assigned(v1));

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);
    }

    @Test
    void onStagnationRevertsAccumulatedWeightsToTheirInitialValue() {
        var csp = new Problem().variable(v1, 10).variable(v2, 8).variable(v3, 3).differ(v1, v2).differ(v2, v3).build();
        var selector = selectorFor(csp);
        selector.onConflict(v1, assigned(v1));
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);

        selector.onStagnation();

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v3);
    }

    @Test
    void onStagnationAlsoClearsTheLastConflictVariable() {
        // Last-conflict returns v2 whatever the ratios say; once cleared, the ratio picks v3.
        var csp = new Problem().variable(v1, 10).variable(v2, 8).variable(v3, 3).differ(v1, v2).differ(v2, v3).build();
        var selector = selectorFor(csp);
        selector.onValueRejected(v2);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);

        selector.onStagnation();

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v3);
    }

    @Test
    void onConflictSkipsConstraintsWithNoUnassignedNeighbour() {
        // Failing on v2 with v3 already assigned: c12 still has v1 open, so its weight rises to 2;
        // c23 has nothing open besides v2, so it stays at 1. v3 is declared first, so had c23 been
        // boosted too, v3 (2/2 = 1.0) would tie v1 and win; instead v1 (2/2 = 1.0) beats v3 (2/1).
        var csp = new Problem().variable(v3, 2).variable(v2, 100).variable(v1, 2).differ(v1, v2).differ(v2, v3).build();
        var selector = selectorFor(csp);

        selector.onConflict(v2, assigned(v2, v3));

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v1);
    }

    @Test
    void onConflictOnAVariableInNoConstraintChangesNothing() {
        var csp = new Problem().variable(v1, 10).variable(v2, 8).variable(v3, 3).differ(v1, v2).build();
        var selector = selectorFor(csp);

        selector.onConflict(v3, assigned(v3));

        // v1: 10/1, v2: 8/1, v3: unweighted -> v2 stays the best weighted choice.
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);
    }

    @Test
    void aConstraintWithOneUnassignedVariableDoesNotCount() {
        // With v2 assigned, c12 and c23 each have a single open variable, so v1 and v3 are both
        // unweighted and tie; the first declared (v1) wins. Counted wrongly, v3 (2/1) would beat v1 (5/1).
        var csp = new Problem().variable(v1, 5).variable(v2, 5).variable(v3, 2).differ(v1, v2).differ(v2, v3).build();

        assertThat(selectorFor(csp).select(csp, assigned(v2))).isEqualTo(v1);
    }

    @Test
    void variableWithNoActiveConstraintsIsChosenLast() {
        // v3 is in no constraint, so it is unweighted however small its domain.
        var csp = new Problem().variable(v3, 1).variable(v1, 5).variable(v2, 9).differ(v1, v2).build();

        assertThat(selectorFor(csp).select(csp, Assignment.empty())).isEqualTo(v1);
    }

    @Test
    void anAssignedVariableInNoConstraintIsNotSelected() {
        var csp = new Problem().variable(v3, 1).variable(v1, 5).variable(v2, 9).differ(v1, v2).build();

        // v3 (in no constraint) and v2 are assigned, so v1 is the only candidate left.
        assertThat(selectorFor(csp).select(csp, assigned(v3, v2))).isEqualTo(v1);
    }

    @Test
    void nogoodConstraintsAreExcludedFromWeightingAndSelection() {
        // The nogood over v1 and v3 must add no weight. v3 is declared before v2: were the nogood
        // counted, v3 (1/1) would tie v2 (2/2) and win.
        var csp = new Problem().variable(v3, 1).variable(v2, 2).variable(v1, 10).differ(v1, v2).build();
        NogoodConstraint nogood = GroundNogoodConstraint.of(Map.of(v1, 1, v3, 1));
        Set<Constraint> constraints = new HashSet<>(csp.getConstraints());
        constraints.add(nogood);
        var selector = new DomWdegVariableSelector(constraints);

        selector.onConflict(v1, assigned(v1));

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);
    }

    @Test
    void tiedVariablesAreBrokenDeterministicallyWithoutReseeding() {
        var csp = new Problem().variable(v1, 1).variable(v3, 1).build();
        var selector = selectorFor(csp);

        Variable<?> first = selector.select(csp, Assignment.empty());
        assertThat(first).isIn(v1, v3);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(first);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(first);
    }

    @Test
    void onRestartNullRestoresDeterministicChoice() {
        var csp = new Problem().variable(v1, 1).variable(v3, 1).build();
        var selector = selectorFor(csp);
        Variable<?> deterministicChoice = selector.select(csp, Assignment.empty());

        Random random = mock(Random.class);
        when(random.nextInt(2)).thenReturn(1); // the candidate second in iteration order
        selector.onRestart(random);
        assertThat(selector.select(csp, Assignment.empty())).isNotEqualTo(deterministicChoice);

        selector.onRestart(null);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(deterministicChoice);
    }

    @Test
    void onRestartPicksAmongTiedCandidatesByRandomIndex() {
        var csp = new Problem().variable(v1, 1).variable(v3, 1).build();
        var selector = selectorFor(csp);
        Random random = mock(Random.class);
        when(random.nextInt(2)).thenReturn(0, 1);

        selector.onRestart(random);
        Variable<?> first = selector.select(csp, Assignment.empty());  // nextInt(2) -> 0
        Variable<?> second = selector.select(csp, Assignment.empty()); // nextInt(2) -> 1

        assertThat(first).isIn(v1, v3);
        assertThat(second).isIn(v1, v3);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void onRestartSkipsRandomDrawWhenOnlyOneCandidate() {
        var csp = new Problem().variable(v1, 1).variable(v2, 4).differ(v1, v2).build();
        var selector = selectorFor(csp);
        Random random = mock(Random.class);

        selector.onRestart(random);

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v1);
        verifyNoInteractions(random);
    }

    // ── last-conflict reasoning ───────────────────────────────────────────────

    @Test
    void onValueRejected_thenSelect_overridesNormalRatioComputation() {
        // Dom/wdeg alone would pick v2 (1/1 against 10/1); last-conflict must return v1 regardless.
        var csp = new Problem().variable(v1, 10).variable(v2, 1).differ(v1, v2).build();
        var selector = selectorFor(csp);

        selector.onValueRejected(v1);

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v1);
    }

    @Test
    void onValueRejected_variableSubsequentlyAssigned_fallsBackToNormalRatio() {
        var csp = new Problem().variable(v1, 10).variable(v2, 1).differ(v1, v2).build();
        var selector = selectorFor(csp);

        selector.onValueRejected(v1);

        assertThat(selector.select(csp, assigned(v1))).isEqualTo(v2);
    }

    @Test
    void onValueRejected_variableNotInCsp_fallsBackToNormalRatio() {
        var csp = new Problem().variable(v2, 3).build();
        var selector = selectorFor(csp);

        selector.onValueRejected(v1);

        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(v2);
    }

    @Test
    void throwsWhenNoUnassignedVariable() {
        var csp = new Problem().variable(v1, 1).build();
        var selector = selectorFor(csp);

        assertThatThrownBy(() -> selector.select(csp, assigned(v1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No unassigned variable found");
    }
}
