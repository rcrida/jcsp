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
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
        selector.incrementWeights(v1, nextAssignment); // c12 weight → 2

        // Now select: v2 unassigned, v3 unassigned; v1 is excluded from csp.getVariableDomains()
        // (not a live decision variable here) but still globally unassigned, so c12 stays active.
        when(csp.getVariableDomains()).thenReturn(Map.of(v2, d2, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty()); // c12's other endpoint, queried by isActive(c12, v2, ...)
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

        selector.incrementWeights(v2, nextAssignment);
        // c12 connects v1+v2; v1 is unassigned → c12 weight becomes 2
        // c23 connects v2+v3; v3 IS assigned → c23 weight stays 1

        // Verify via selection: both v1 and v3 unassigned, but c12 weight=2 makes v1 more attractive.
        // v2 is excluded from csp.getVariableDomains() (not a live decision variable here) but still
        // globally unassigned, so c12/c23 both stay active -- matching the wdeg comments below.
        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty()); // c12/c23's other endpoint, queried by isActive
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
        selector.incrementWeights(v1, nextAssignment); // c12 weight -> 2; nogood skipped entirely

        when(csp.getVariableDomains()).thenReturn(Map.of(v2, d2, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d2.size()).thenReturn(2); // v2: wdeg = c12(2) → ratio = 1.0
        when(d3.size()).thenReturn(1); // v3: wdeg = 0 (nogood ignored, not just unweighted) → ratio = MAX_VALUE

        assertThat(selector.select(csp, assignment)).isEqualTo(v2);
    }

    @Test
    void tiedVariablesAreBrokenDeterministicallyWithoutReseeding() {
        // v1 and v3 are both unconstrained → both ratio=MAX_VALUE, a genuine tie. No reseedTieBreak
        // call at all: tieBreakRandom stays null, so repeated calls must deterministically return
        // the same tied candidate every time (today's exact behaviour, unchanged) -- which candidate
        // that is depends on csp.getVariableDomains()'s iteration order, not asserted here.
        var selector = new DomWdegVariableSelector(Set.of());

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(1);
        when(d3.size()).thenReturn(1);

        Variable<?> first = selector.select(csp, assignment);
        assertThat(first).isIn(v1, v3);
        assertThat(selector.select(csp, assignment)).isEqualTo(first);
        assertThat(selector.select(csp, assignment)).isEqualTo(first);
    }

    @Test
    void reseedTieBreakNullRestoresDeterministicChoice() {
        var selector = new DomWdegVariableSelector(Set.of());

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(1);
        when(d3.size()).thenReturn(1);

        Variable<?> deterministicChoice = selector.select(csp, assignment); // tieBreakRandom == null

        Random random = mock(Random.class);
        when(random.nextInt(2)).thenReturn(1); // pick whichever candidate is second in iteration order
        selector.reseedTieBreak(random);
        assertThat(selector.select(csp, assignment)).isNotEqualTo(deterministicChoice);

        selector.reseedTieBreak(null);
        assertThat(selector.select(csp, assignment)).isEqualTo(deterministicChoice); // back to deterministic
    }

    @Test
    void reseedTieBreakPicksAmongTiedCandidatesByRandomIndex() {
        var selector = new DomWdegVariableSelector(Set.of());
        Random random = mock(Random.class);
        when(random.nextInt(2)).thenReturn(0, 1);

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v3, d3));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v3)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(1);
        when(d3.size()).thenReturn(1);

        selector.reseedTieBreak(random);
        Variable<?> first = selector.select(csp, assignment);  // nextInt(2) -> 0
        Variable<?> second = selector.select(csp, assignment); // nextInt(2) -> 1

        assertThat(first).isIn(v1, v3);
        assertThat(second).isIn(v1, v3);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void reseedTieBreakSkipsRandomDrawWhenOnlyOneCandidate() {
        // c12 weight=1 (initial): v1: domain=1, wdeg=1 -> ratio=1.0 (unique winner)
        // v2: domain=4, wdeg=1 -> ratio=4.0
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        var selector = new DomWdegVariableSelector(Set.of(c12));
        Random random = mock(Random.class);

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v2, d2));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(d1.size()).thenReturn(1);
        when(d2.size()).thenReturn(4);

        selector.reseedTieBreak(random);
        assertThat(selector.select(csp, assignment)).isEqualTo(v1);
        verifyNoInteractions(random); // no tie -> the shared Random is never drawn from
    }

    // ── last-conflict reasoning ───────────────────────────────────────────────

    @Test
    void recordConflict_thenSelect_overridesNormalRatioComputation() {
        // c12 connects v1,v2, giving both wdeg=1. d1.size()=10 -> ratio=10.0, d2.size()=1 ->
        // ratio=1.0: normal dom/wdeg would pick v2 (smaller ratio). recordConflict(v1) must make
        // select() return v1 regardless, proving the override actually bypasses the ratio
        // computation rather than coincidentally agreeing with it.
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        var selector = new DomWdegVariableSelector(Set.of(c12));

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v2, d2));
        when(assignment.getValue(v1)).thenReturn(Optional.empty());

        selector.recordConflict(v1);
        assertThat(selector.select(csp, assignment)).isEqualTo(v1);
    }

    @Test
    void recordConflict_variableSubsequentlyAssigned_fallsBackToNormalRatio() {
        // v1 is now assigned in `assignment` -- the recorded last-conflict variable is no longer a
        // valid choice, so select() must fall through to the normal dom/wdeg computation (over just
        // v2, the only unassigned variable in csp.getVariableDomains(); v1 is excluded by the same
        // "already assigned" filter the ratio loop applies to every candidate) instead of throwing
        // or returning the now-assigned v1.
        when(c12.getVariables()).thenReturn(Set.of(v1, v2));
        var selector = new DomWdegVariableSelector(Set.of(c12));

        when(csp.getVariableDomains()).thenReturn(Map.of(v1, d1, v2, d2));
        when(assignment.getValue(v1)).thenReturn(Optional.of("assigned"));
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(d2.size()).thenReturn(1);

        selector.recordConflict(v1);
        assertThat(selector.select(csp, assignment)).isEqualTo(v2);
    }

    @Test
    void recordConflict_variableNotInCsp_fallsBackToNormalRatio() {
        // The recorded last-conflict variable (v1) isn't part of this csp's own variable domains
        // at all -- select() must not return it regardless (and must not throw), falling through to
        // the normal dom/wdeg computation over the variables that actually are present.
        var selector = new DomWdegVariableSelector(Set.of());

        when(csp.getVariableDomains()).thenReturn(Map.of(v2, d2));
        when(assignment.getValue(v2)).thenReturn(Optional.empty());
        when(d2.size()).thenReturn(3);

        selector.recordConflict(v1);
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
