package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisjunctiveConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- isSatisfiedBy() ---

    @Test
    void nonOverlapping_satisfied() {
        Variable<Integer> s1 = F.create("s1"), s2 = F.create("s2");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 0, s2, 2)))).isTrue();
    }

    @Test
    void overlapping_notSatisfied() {
        Variable<Integer> s1 = F.create("s1o"), s2 = F.create("s2o");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 0, s2, 1)))).isFalse();
    }

    @Test
    void nonAdjacentOverlap_detectedViaAdjacentPairCheck() {
        // Sorted by start: s2=[0,2), s1=[1,3), s3=[5,7) -- s2/s1 overlap, an adjacent pair in
        // start-sorted order, so the "check only consecutive pairs" shortcut still catches it.
        Variable<Integer> s1 = F.create("s1n"), s2 = F.create("s2n"), s3 = F.create("s3n");
        var c = DisjunctiveConstraint.of(List.of(s1, s2, s3), List.of(2, 2, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 1, s2, 0, s3, 5)))).isFalse();
    }

    @Test
    void adjacent_satisfied() {
        Variable<Integer> s1 = F.create("s1a"), s2 = F.create("s2a");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 1, s2, 3)))).isTrue();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        Variable<Integer> s1 = F.create("s1p"), s2 = F.create("s2p");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 0)))).isTrue();
    }

    @Test
    void of_unequalListLengths_asserts() {
        Variable<Integer> s1 = F.create("s1u");
        assertThatThrownBy(() -> DisjunctiveConstraint.of(List.of(s1), List.of(2, 2)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_createsEquivalentConstraint() {
        Variable<Integer> s1 = F.create("s1e"), s2 = F.create("s2e");
        assertThat(DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2)))
                .isEqualTo(DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2)));
    }

    // --- propagate(): edge-finding ---

    @Test
    void propagate_edgeFinding_tightensEarliestStart_noCompulsoryPartsExist() {
        // Worked example from the class Javadoc: t1,t2 duration 3, window [0,6) each (domain
        // [0,3]); t3 duration 3, window [0,9) (domain [0,6]). Theta={t1,t2}: est=0,p=6,lct=6 --
        // not overloaded alone, but combined with t3 (est_3=0>=0): 0+6+3=9>6 forces
        // est_3 <- max(0,6)=6, and since t3's own lct=9 (lst=6), the window collapses to {6}.
        // CumulativeConstraintTest's analogous instance (no compulsory parts, wide domains)
        // reports no change at all -- this is the case DisjunctiveConstraint strictly improves on.
        Variable<Integer> t1 = F.create("efn_t1"), t2 = F.create("efn_t2"), t3 = F.create("efn_t3");
        var c = DisjunctiveConstraint.of(List.of(t1, t2, t3), List.of(3, 3, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                t1, IntRangeDomain.of(0, 3),
                t2, IntRangeDomain.of(0, 3),
                t3, IntRangeDomain.of(0, 6));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(t3);
        assertThat(result.get().get(t3)).isEqualTo(IntRangeDomain.of(6, 6));
    }

    @Test
    void propagate_edgeFinding_tightensLatestStart_backwardPass() {
        // Mirror of the forward case via the time-reversal used for the backward pass.
        // j1,j2 duration 3, window [3,9) (domain [3,6]) -- together need exactly [3,9), no slack.
        // j0 duration 3, domain [0,6] (window [0,9)): the only room left is [0,3), forcing j0
        // to start at exactly 0.
        Variable<Integer> j0 = F.create("bwd_j0"), j1 = F.create("bwd_j1"), j2 = F.create("bwd_j2");
        var c = DisjunctiveConstraint.of(List.of(j0, j1, j2), List.of(3, 3, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                j0, IntRangeDomain.of(0, 6),
                j1, IntRangeDomain.of(3, 6),
                j2, IntRangeDomain.of(3, 6));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(j0);
        assertThat(result.get().get(j0)).isEqualTo(IntRangeDomain.of(0, 0));
    }

    @Test
    void propagate_overload_returnsEmpty() {
        // Two duration-5 tasks both confined to [0,3]: combined they need 10 time units but their
        // shared window is only [0,8) wide -- infeasible before any single task is even singleton.
        Variable<Integer> a = F.create("ovl_a"), b = F.create("ovl_b");
        var c = DisjunctiveConstraint.of(List.of(a, b), List.of(5, 5));
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_combinedBoundsInfeasible_returnsEmpty() {
        // Found via exhaustive brute-force search (checked all 15 non-empty subsets of these 4
        // tasks against the raw overload condition est(Theta)+p(Theta)>lct(Theta) directly, for
        // many random small instances): no single Theta here is directly overloaded, yet
        // propagate() is still infeasible -- the forward pass pushes t2's/t3's est up (via
        // Theta={t0,t1}, then growing further to include t2) to the edge of their own lst, and the
        // backward/mirror pass independently pushes t3's lst down just enough to cross it. This is
        // the case CumulativeConstraint's own timetabling has no equivalent of at all: it has
        // nothing resembling a "two independently-derived bounds interacting" failure mode.
        Variable<Integer> t0 = F.create("cbi_t0"), t1 = F.create("cbi_t1"), t2 = F.create("cbi_t2"), t3 = F.create("cbi_t3");
        var constraint = DisjunctiveConstraint.of(List.of(t0, t1, t2, t3), List.of(1, 2, 3, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                t0, IntRangeDomain.of(1, 2),
                t1, IntRangeDomain.of(3, 4),
                t2, IntRangeDomain.of(1, 7),
                t3, IntRangeDomain.of(4, 7));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_wideDomains_noChange() {
        Variable<Integer> s1 = F.create("s1w"), s2 = F.create("s2w");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(s1, IntRangeDomain.of(0, 10), s2, IntRangeDomain.of(0, 10));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_overload_citesOverloadedTasksAsRange() {
        Variable<Integer> a = F.create("eio_a"), b = F.create("eio_b");
        var c = DisjunctiveConstraint.of(List.of(a, b), List.of(5, 5));
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains))
                .contains(RangeNogoodConstraint.of(Map.of(a, IntervalDomain.of(0, 3), b, IntervalDomain.of(0, 3))));
    }

    @Test
    void explainInfeasible_gappedDomain_fallsBackPastRangeCitation() {
        // Same overload as explainInfeasible_overload_citesOverloadedTasksAsRange, but a's domain
        // has a gap (still min=0/max=3, so the overload check -- which only reads bounds -- is
        // unaffected) -- RangeNogoodConstraint#fromCurrentBounds refuses to cite a gapped discrete
        // domain as a range (see its own Javadoc), so this falls through to the singleton fallback;
        // a isn't singleton either, so the overall result is empty -- sound, just not explained.
        Variable<Integer> a = F.create("eig_a"), b = F.create("eig_b");
        var c = DisjunctiveConstraint.of(List.of(a, b), List.of(5, 5));
        Domain<Integer> gapped = IntRangeDomain.of(0, 3).toBuilder().delete(2).build();
        var domains = Map.<Variable<?>, Domain<?>>of(a, gapped, b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_combinedBounds_citesFullVariableSet() {
        // Same scenario as propagate_combinedBoundsInfeasible_returnsEmpty (verified via brute-force
        // subset search to have no directly-overloaded Theta); citing all 4 tasks confirms this went
        // through the combined-bounds fallback, not either pass's own narrower overloaded-Theta
        // culprit set.
        Variable<Integer> t0 = F.create("eic_t0"), t1 = F.create("eic_t1"), t2 = F.create("eic_t2"), t3 = F.create("eic_t3");
        var constraint = DisjunctiveConstraint.of(List.of(t0, t1, t2, t3), List.of(1, 2, 3, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                t0, IntRangeDomain.of(1, 2),
                t1, IntRangeDomain.of(3, 4),
                t2, IntRangeDomain.of(1, 7),
                t3, IntRangeDomain.of(4, 7));
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(RangeNogoodConstraint.of(Map.of(
                t0, IntervalDomain.of(1, 2),
                t1, IntervalDomain.of(3, 4),
                t2, IntervalDomain.of(1, 7),
                t3, IntervalDomain.of(4, 7))));
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        Variable<Integer> s1 = F.create("s1f"), s2 = F.create("s2f");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(s1, IntRangeDomain.of(0, 10), s2, IntRangeDomain.of(0, 10));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- getRelation() / toString() ---

    @Test
    void getRelation() {
        Variable<Integer> s1 = F.create("s1r"), s2 = F.create("s2r");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.getRelation()).isEqualTo("disjunctive(tasks=2)");
    }

    @Test
    void testToString() {
        Variable<Integer> s1 = F.create("s1t"), s2 = F.create("s2t");
        var c = DisjunctiveConstraint.of(List.of(s1, s2), List.of(2, 2));
        assertThat(c.toString()).isEqualTo("<(s1t, s2t), disjunctive(tasks=2)>");
    }
}
