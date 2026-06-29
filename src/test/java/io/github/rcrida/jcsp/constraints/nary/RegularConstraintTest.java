package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RegularConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // 3-state DFA over {0,1} accepting sequences with no two consecutive 1s.
    // 0 = last-saw-0 (start), 1 = last-saw-1, 2 = saw-11 (dead). Accepting: {0, 1}.
    static final Automaton<Integer> NO_DOUBLE_ONE = Automaton.of(3, 0, Set.of(0, 1), Map.of(
            0, Map.of(0, 0, 1, 1),
            1, Map.of(0, 0, 1, 2),
            2, Map.of(0, 2, 1, 2)));

    Variable<Integer> v0 = F.create("v0");
    Variable<Integer> v1 = F.create("v1");
    Variable<Integer> v2 = F.create("v2");

    // --- Automaton.of validation ---

    @Test
    void automaton_initialStateOutOfRange_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, 5, Set.of(0), Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void automaton_acceptingStateOutOfRange_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, 0, Set.of(0, 9), Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void automaton_transition_missingReturnsMinusOne() {
        assertThat(NO_DOUBLE_ONE.transition(0, 0)).isEqualTo(0);
        assertThat(NO_DOUBLE_ONE.transition(1, 1)).isEqualTo(2);
        assertThat(NO_DOUBLE_ONE.transition(0, 99)).isEqualTo(-1); // value not in map
        assertThat(NO_DOUBLE_ONE.transition(7, 0)).isEqualTo(-1);  // state not in map
    }

    // --- RegularConstraint.of validation ---

    @Test
    void of_emptySequence_asserts() {
        assertThatThrownBy(() -> RegularConstraint.of(List.of(), NO_DOUBLE_ONE))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_validSequence_true() {
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v0, 0, v1, 1, v2, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_consecutiveOnes_false() {
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v0, 1, v1, 1, v2, 0)))).isFalse();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v0, 0, v1, 1, v2, 1)))).isFalse();
    }

    @Test
    void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v0, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_missingTransition_false() {
        // 2-state DFA with no transition from state 0 on value 99
        var dfa = Automaton.of(2, 0, Set.of(0, 1), Map.of(0, Map.of(0, 1), 1, Map.of(0, 0)));
        var c = RegularConstraint.of(List.of(v0), dfa);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v0, 99)))).isFalse();
    }

    // --- propagate ---

    @Test
    void propagate_unconstrained_noChange() {
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v0, IntRangeDomain.of(0, 1), v1, IntRangeDomain.of(0, 1), v2, IntRangeDomain.of(0, 1));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_forcedOne_prunesNeighbours() {
        // v1 forced to 1 ⇒ v0 cannot be 1 (would form 11) and v2 must be 0
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v0, IntRangeDomain.of(0, 1), v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(0, 1));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v0)).isEqualTo(IntRangeDomain.of(0, 0));
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(0, 0));
    }

    @Test
    void propagate_forwardWipeout_infeasible() {
        // DFA accepting only value 0; all-{1} sequence has no valid transition from the start
        var dfa = Automaton.of(2, 0, Set.of(1), Map.of(0, Map.of(0, 1), 1, Map.of(0, 1)));
        var c = RegularConstraint.of(List.of(v0, v1), dfa);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v0, IntRangeDomain.of(1, 1), v1, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noAcceptingReachable_infeasible() {
        // DFA that accepts only sequences ending in 0; force all values to 1
        var dfa = Automaton.of(2, 0, Set.of(0), Map.of(
                0, Map.of(0, 0, 1, 1), 1, Map.of(0, 0, 1, 1)));
        var c = RegularConstraint.of(List.of(v0, v1), dfa);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v0, IntRangeDomain.of(1, 1), v1, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void automaton_initialStateNegative_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, -1, Set.of(0), Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void automaton_acceptingStateNegative_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, 0, Set.of(-1), Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void automaton_transitionTargetOutOfRange_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, 0, Set.of(0), Map.of(0, Map.of(0, 5))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void automaton_transitionTargetNegative_asserts() {
        assertThatThrownBy(() -> Automaton.of(2, 0, Set.of(0), Map.of(0, Map.of(0, -1))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void propagate_missingTransitions_prunesUnsupportedValues() {
        // DFA: states {0,1,2}, initial=0, accepting={2}.
        // Only value 2 has valid transitions: (0,2)->1, (1,2)->2.
        // Value 1 has no transition from any state (returns -1).
        // IntRangeDomain.of(1,2) guarantees 1 is iterated before 2, so the backward pass
        // and pruning pass both exercise the next==-1 branch before finding the productive one.
        var dfa = Automaton.of(3, 0, Set.of(2), Map.of(
                0, Map.of(2, 1),
                1, Map.of(2, 2)));
        var c = RegularConstraint.of(List.of(v0, v1), dfa);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v0, IntRangeDomain.of(1, 2),
                v1, IntRangeDomain.of(1, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v0)).isEqualTo(IntRangeDomain.of(2, 2));
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(2, 2));
    }

    @Test
    void toString_showsRelation() {
        var c = RegularConstraint.of(List.of(v0, v1, v2), NO_DOUBLE_ONE);
        assertThat(c.toString()).isEqualTo("<(v0, v1, v2), regular([v0, v1, v2], 3 states)>");
    }

    // --- CSP builder method ---

    @Test
    void cspBuilder_regularConstraint_method() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v0, IntRangeDomain.of(0, 1))
                .variableDomain(v1, IntRangeDomain.of(0, 1))
                .regularConstraint(List.of(v0, v1), NO_DOUBLE_ONE)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(3);
    }

    // --- solver integration ---

    @Test
    void solver_findsAllNoDoubleOneSequences() {
        // Length-4 binary sequences with no "11": 8 solutions
        Variable<Integer> a = F.create("a");
        Variable<Integer> b = F.create("b");
        Variable<Integer> c = F.create("c");
        Variable<Integer> d = F.create("d");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 1))
                .variableDomain(b, IntRangeDomain.of(0, 1))
                .variableDomain(c, IntRangeDomain.of(0, 1))
                .variableDomain(d, IntRangeDomain.of(0, 1))
                .constraint(RegularConstraint.of(List.of(a, b, c, d), NO_DOUBLE_ONE))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList()).hasSize(8);
    }
}
