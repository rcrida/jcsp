package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomWdegVariableSelectorTest {

    @Mock ConstraintSatisfactionProblem csp;
    @Mock Assignment assignment;
    @Mock Assignment nextAssignment;
    @Mock Variable v1;
    @Mock Variable v2;
    @Mock Variable v3;
    @Mock Domain d1;
    @Mock Domain d2;
    @Mock Domain d3;
    @Mock Constraint c12; // connects v1 and v2
    @Mock Constraint c23; // connects v2 and v3

    @Test
    void selectsVariableWithSmallestDomWdegRatio() {
        // c12 weight=1, c23 weight=1 (initial)
        // v1: domain=1, wdeg(v1) = c12 (active: v2 unassigned) = 1 → ratio=1.0  ← wins
        // v2: domain=4, wdeg(v2) = c12 + c23 (both active) = 2 → ratio=2.0
        // v3: domain=3, wdeg(v3) = c23 (active: v2 unassigned) = 1 → ratio=3.0
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        when(c23.getVariables()).thenReturn(Set.of(v2, v3));
        var selector = new DomWdegVariableSelector(Set.of(c12, c23));

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v2, d2, v3, d3));
        when(csp.getConstraints()).thenReturn(Set.of(c12, c23));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(1);
        when(d2.size()).thenReturn(4);
        when(d3.size()).thenReturn(3);

        assertThat(selector.select(csp, assignment)).isEqualTo(v1);
    }

    @Test
    void incrementWeightsBoostsActiveConstraint() {
        // After incrementing c12 (active: v1 assigned, v2 still unassigned),
        // c12.weight becomes 2. v2's ratio drops to domain/2, so v2 beats v3.
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        when(c23.getVariables()).thenReturn(Set.of(v2, v3));
        var selector = new DomWdegVariableSelector(Set.of(c12, c23));

        // nextAssignment: v1 is the just-assigned variable (excluded by !v.equals(variable));
        // only v2 (the unassigned neighbour in c12) is actually queried.
        when(nextAssignment.getValue(v2)).thenReturn(Optional.empty());
        when(csp.getConstraints()).thenReturn(Set.of(c12, c23));
        selector.incrementWeights(csp, v1, nextAssignment); // c12 weight → 2

        // Now select: v2 unassigned, v3 unassigned; v1 is assigned so excluded.
        when(csp.getVariableDomains()).thenReturn(Map.of(v2, d2, v3, d3));
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d2.size()).thenReturn(4); // v2: wdeg = c12(2)+c23(1)=3 (both active) → ratio=4/3≈1.33
        when(d3.size()).thenReturn(3); // v3: wdeg = c23(1) → ratio=3/1=3.0
        // v2 wins with ratio 1.33 < 3.0

        assertThat(selector.select(csp, assignment)).isEqualTo(v2);
    }

    @Test
    void incrementWeightsSkipsConstraintsWithNoUnassignedNeighbour() {
        // c23 involves v2+v3. When we fail on v2 (with v3 already assigned in nextAssignment),
        // c23 has no unassigned neighbour for v2, so its weight must NOT be incremented.
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        when(c23.getVariables()).thenReturn(Set.of(v2, v3));
        var selector = new DomWdegVariableSelector(Set.of(c12, c23));

        // nextAssignment: v2 is the just-assigned variable (excluded by !v.equals(variable));
        // v3 (neighbour via c23) and v1 (neighbour via c12) are what actually get queried.
        when(nextAssignment.getValue(v3)).thenReturn(Optional.of("assigned"));
        when(nextAssignment.getValue(v1)).thenReturn(Optional.empty());
        when(csp.getConstraints()).thenReturn(Set.of(c12, c23));

        selector.incrementWeights(csp, v2, nextAssignment);
        // c12 connects v1+v2; v1 is unassigned → c12 weight becomes 2
        // c23 connects v2+v3; v3 IS assigned → c23 weight stays 1

        // Verify via selection: both v1 and v3 unassigned, but c12 weight=2 makes v1 more attractive
        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(2); // v1: wdeg = c12(2) (v2 not in variableDomains here) → ratio=1.0
        when(d3.size()).thenReturn(2); // v3: wdeg = c23(1) → ratio=2.0  (v2 not in variableDomains)

        assertThat(selector.select(csp, assignment)).isEqualTo(v1);
    }

    @Test
    void variableWithNoActiveConstraintsIsChosenLast() {
        // v1 is connected to v2 via c12. v3 has no constraints.
        // With equal domain sizes, v1 (wdeg=1) wins over v3 (wdeg=0 → ratio=MAX_VALUE).
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        var selector = new DomWdegVariableSelector(Set.of(c12));

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(csp.getConstraints()).thenReturn(Set.of(c12));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty()); // v2 counted as unassigned neighbour
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(5); // v1: ratio = 5/1 = 5.0
        when(d3.size()).thenReturn(1); // v3: ratio = MAX_VALUE (no active constraints)

        assertThat(selector.select(csp, assignment)).isEqualTo(v1);
    }

    @Test
    void nogoodConstraintsAreExcludedFromWeightingAndSelection() {
        // A NogoodConstraint over v1+v3 must never contribute to weighting or wdeg, even though
        // it structurally satisfies isActive's other conditions (shares an unassigned variable).
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        NogoodConstraint nogood = GroundNogoodConstraint.of(Map.of(v1, "a", v3, "b"));
        var selector = new DomWdegVariableSelector(Set.of(c12, nogood));

        when(nextAssignment.getValue(v2)).thenReturn(Optional.empty());
        when(csp.getConstraints()).thenReturn(Set.of(c12, nogood));
        selector.incrementWeights(csp, v1, nextAssignment); // c12 weight -> 2; nogood skipped entirely

        when(csp.getVariableDomains()).thenReturn(Map.of(v2, d2, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d2.size()).thenReturn(2); // v2: wdeg = c12(2) → ratio = 1.0
        when(d3.size()).thenReturn(1); // v3: wdeg = 0 (nogood ignored, not just unweighted) → ratio = MAX_VALUE

        assertThat(selector.select(csp, assignment)).isEqualTo(v2);
    }

    @Test
    void throwsWhenNoUnassignedVariable() {
        var selector = new DomWdegVariableSelector(Set.of());

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1));
        when(assignment.getValue(v1)).thenReturn(Optional.of("assigned"));

        assertThatThrownBy(() -> selector.select(csp, assignment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No unassigned variable found");
    }
}
