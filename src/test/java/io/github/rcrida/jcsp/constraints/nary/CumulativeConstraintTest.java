package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class CumulativeConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> s1 = F.create("s1");
    Variable<Integer> s2 = F.create("s2");

    // 2 tasks, each duration=2, resource=1, capacity=1 (serial)
    CumulativeConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = CumulativeConstraint.of(List.of(s1, s2), List.of(2, 2), List.of(1, 1), 1);
    }

    @Test
    void nonOverlapping_satisfied() {
        // s1=[0,2), s2=[2,4): no overlap
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(s1, 0, s2, 2)))).isTrue();
    }

    @Test
    void overlapping_notSatisfied() {
        // s1=[0,2), s2=[1,3): overlap at t=1
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(s1, 0, s2, 1)))).isFalse();
    }

    @Test
    void adjacent_satisfied() {
        // s1=[1,3), s2=[3,5): touch but do not overlap
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(s1, 1, s2, 3)))).isTrue();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(s1, 0)))).isTrue();
    }

    @Test
    void of_unequalListLengths_asserts() {
        assertThatThrownBy(() -> CumulativeConstraint.of(List.of(s1), List.of(2, 2), List.of(1), 1))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_unequalResourcesLength_asserts() {
        // starts.size() == durations.size() is true, but starts.size() != resources.size()
        assertThatThrownBy(() -> CumulativeConstraint.of(List.of(s1, s2), List.of(2, 2), List.of(1), 1))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(CumulativeConstraint.of(List.of(s1, s2), List.of(2, 2), List.of(1, 1), 1))
                .isEqualTo(constraint);
    }

    @Test
    void propagate_tightensStartBound() {
        // x1 ∈ [0,1], d=2, r=2 → compulsory part [lst=1, ect=0+2=2) = [1,2): P(1)=2
        // x2 ∈ [0,3], d=2, r=2, limit=2
        // x2 at start=0: runs [0,2), t=1 ex=2, 2+2=4>2 → infeasible
        // x2 at start=1: runs [1,3), t=1 ex=2, 2+2=4>2 → infeasible
        // x2 at start=2: runs [2,4), ex=0 everywhere → feasible → new est=2
        // x2 at start=3: feasible → new lst=3 unchanged
        // Result: x2 domain tightened from [0,3] to [2,3]
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2, 2), List.of(2, 2), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(0, 1),
                x2, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void propagate_tightensLatestStart() {
        // x1 ∈ [2,3], d=2, r=2 → compulsory part [lst=3, ect=4) = [3,4): P(3)=2
        // x2 ∈ [0,3], d=2, r=2, limit=2
        // x2 newLst scan: start=3 → t=3: 2+2=4>2 → newLst--; start=2 → t=3 still blocked → newLst--;
        //                 start=1 → t∈[1,3): ex=0 everywhere → feasible → newLst=1
        // Result: x2 domain tightened from [0,3] to [0,1]
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2, 2), List.of(2, 2), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(2, 3),
                x2, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(0, 1));
    }

    @Test
    void propagate_infeasible_returnsEmpty() {
        // Both tasks have compulsory parts that together exceed limit
        // s1 ∈ [1,1] (fixed), s2 ∈ [1,1] (fixed), d=2, r=1, limit=1
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2, 2), List.of(1, 1), 1);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_zeroDuration_returnsNoChange() {
        // With duration=0, minTime == maxTime → early return with no changes.
        Variable<Integer> x = F.create("x");
        var c = CumulativeConstraint.of(List.of(x), List.of(0), List.of(1), 1);
        var result = c.propagate(Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x, IntRangeDomain.of(0, 0)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_infeasibleViaExclusiveProfile() {
        // x1=[0,0] d=1 r=1, x2=[1,1] d=1 r=1, x3=[0,1] d=1 r=2, limit=2
        // Full profile: P[0]=1, P[1]=1 — overload check passes.
        // For x3: ex[0]=1, ex[1]=1. start=0: 1+2=3>2 → skip. start=1: 1+2=3>2 → skip.
        // newEst(x3)=2 > lst(x3)=1 → infeasible detected via exclusive-profile scan.
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        Variable<Integer> x3 = F.create("x3");
        var c = CumulativeConstraint.of(
                List.of(x1, x2, x3), List.of(1, 1, 1), List.of(1, 1, 2), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(0, 0),
                x2, IntRangeDomain.of(1, 1),
                x3, IntRangeDomain.of(0, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // Wide domains, no compulsory parts → no pruning
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                s1, IntRangeDomain.of(0, 5),
                s2, IntRangeDomain.of(0, 5));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_globalOverload_allSingleton_attributesAll() {
        // Same domains as propagate_infeasible_returnsEmpty: both tasks fixed, both contribute
        // compulsory-part events to the global overload check.
        Variable<Integer> x1 = F.create("gx1");
        Variable<Integer> x2 = F.create("gx2");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2, 2), List.of(1, 1), 1);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).containsOnly(Map.entry(x1, 1), Map.entry(x2, 1));
    }

    @Test
    void explainInfeasible_perTaskZeroDurationFailure_allSingleton_attributesAll() {
        // A: fixed at 0, duration 2, resource 2 -> compulsory part [0,2), global profile max = 2,
        // which does NOT exceed limit=2 (global check only fires on strictly-greater), so the
        // global overload check passes. B: fixed at 1, duration 0, resource 1 -> B has NO
        // compulsory part of its own (lst < compEnd is false when duration=0), so B contributes no
        // events to the global check. But B's own per-task exclusive-profile scan (checking B's
        // placement against A's mandatory usage alone) finds that landing at 1 (strictly inside
        // A's [0,2) window) would push the total to 2+1=3 > limit — infeasible, independent of the
        // global check. Both A and B are singleton, so both are cited.
        Variable<Integer> a = F.create("cza");
        Variable<Integer> b = F.create("czb");
        var c = CumulativeConstraint.of(List.of(a, b), List.of(2, 0), List.of(2, 1), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                a, IntRangeDomain.of(0, 0),
                b, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).containsOnly(Map.entry(a, 0), Map.entry(b, 1));
    }

    @Test
    void explainInfeasible_exclusiveProfileFailure_taskNotSingleton_returnsEmpty() {
        // Same domains as propagate_infeasibleViaExclusiveProfile: x3's own domain [0,1] is not
        // singleton, so even though x1 and x2 (the other compulsory-part contributors) are
        // singleton, the full culprit set {x1, x2, x3} isn't — citing only x1/x2 would be unsound
        // since a different x3 placement isn't excluded by this reason alone. Empty is correct.
        Variable<Integer> x1 = F.create("ex1");
        Variable<Integer> x2 = F.create("ex2");
        Variable<Integer> x3 = F.create("ex3");
        var c = CumulativeConstraint.of(
                List.of(x1, x2, x3), List.of(1, 1, 1), List.of(1, 1, 2), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(0, 0),
                x2, IntRangeDomain.of(1, 1),
                x3, IntRangeDomain.of(0, 1));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                s1, IntRangeDomain.of(0, 5),
                s2, IntRangeDomain.of(0, 5));
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(s1, s2), cumulative(limit=1, tasks=2)>");
    }

    @Test
    void solver_serialTasks_solutionCount() {
        // 2 tasks, duration=2, resource=1, capacity=1 (serial), start ∈ [0,3].
        // Non-overlapping pairs: 6 (see explanation in solver chain)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s1, IntRangeDomain.of(0, 3))
                .variableDomain(s2, IntRangeDomain.of(0, 3))
                .cumulativeConstraint(List.of(s1, s2), List.of(2, 2), List.of(1, 1), 1)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(6);
    }

    // --- IntervalDomain (continuous) tests ---

    @Test
    void isSatisfiedBy_doubleValues_nonOverlapping() {
        // s1=0.0, s2=2.0: tasks [0,2) and [2,4) — touch but do not overlap
        Variable<Double> x1 = F.create("x1d");
        Variable<Double> x2 = F.create("x2d");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(1.0, 1.0), 1.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 0.0, x2, 2.0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_doubleValues_overlapping() {
        // s1=0.0, s2=1.5: tasks [0,2) and [1.5,3.5) — overlap from 1.5 to 2.0
        Variable<Double> x1 = F.create("x1do");
        Variable<Double> x2 = F.create("x2do");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(1.0, 1.0), 1.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 0.0, x2, 1.5)))).isFalse();
    }

    @Test
    void of_double_unequalListLengths_asserts() {
        Variable<Double> xd = F.create("xd");
        // starts.size() != durations.size() → short-circuit of &&
        assertThatThrownBy(() -> CumulativeConstraint.of(List.of(xd), List.of(2.0, 2.0), List.of(1.0), 1.0))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_double_unequalResourcesLength_asserts() {
        Variable<Double> xd1 = F.create("xd1");
        Variable<Double> xd2 = F.create("xd2");
        // starts.size() == durations.size() but != resources.size() → second condition of && fails
        assertThatThrownBy(() -> CumulativeConstraint.of(List.of(xd1, xd2), List.of(2.0, 2.0), List.of(1.0), 1.0))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void propagate_intervalDomain_tightensEarliestStart() {
        // x1 ∈ [0.0, 1.0], d=2.0, r=2.0 → compulsory part [1.0, 2.0): profile P(t)=2 for t∈[1,2)
        // x2 ∈ [0.0, 3.0], d=2.0, r=2.0, limit=2.0
        // Exclusive profile for x2: overloaded [1.0, 2.0) (slack = 2-2 = 0, runEx=2>0)
        // Forbidden starts for x2: (1.0-2.0, 2.0) = (-1.0, 2.0)
        // newEst: 0.0 > -1.0 and 0.0 < 2.0 → advance to 2.0
        // Result: x2 tightened from [0.0, 3.0] to [2.0, 3.0]
        Variable<Double> x1 = F.create("x1id");
        Variable<Double> x2 = F.create("x2id");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(2.0, 2.0), 2.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntervalDomain.of(0.0, 1.0),
                x2, IntervalDomain.of(0.0, 3.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntervalDomain.of(2.0, 3.0));
    }

    @Test
    void propagate_intervalDomain_tightensLatestStart() {
        // x1 ∈ [2.0, 3.0], d=2.0, r=2.0 → compulsory part [3.0, 4.0): profile P(t)=2 for t∈[3,4)
        // x2 ∈ [0.0, 3.0], d=2.0, r=2.0, limit=2.0
        // Exclusive profile for x2: overloaded [3.0, 4.0) (slack=0, runEx=2>0)
        // Forbidden starts: (3.0-2.0, 4.0) = (1.0, 4.0)
        // newLst: 3.0 > 1.0 and 3.0 < 4.0 → retreat to 1.0
        // Result: x2 tightened from [0.0, 3.0] to [0.0, 1.0]
        Variable<Double> x1 = F.create("x1ilt");
        Variable<Double> x2 = F.create("x2ilt");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(2.0, 2.0), 2.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntervalDomain.of(2.0, 3.0),
                x2, IntervalDomain.of(0.0, 3.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntervalDomain.of(0.0, 1.0));
    }

    @Test
    void propagate_intervalDomain_infeasible_returnsEmpty() {
        // x1=[1.0,1.0] d=2.0 r=1.0, x2=[1.0,1.0] d=2.0 r=1.0, limit=1.0
        // Both have compulsory parts [1.0,3.0): global profile = 2 > 1 → infeasible
        Variable<Double> x1 = F.create("x1iinf");
        Variable<Double> x2 = F.create("x2iinf");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(1.0, 1.0), 1.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntervalDomain.of(1.0, 1.0),
                x2, IntervalDomain.of(1.0, 1.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_intervalDomain_noChange_returnsEmptyMap() {
        // Wide domains, no compulsory parts → no pruning
        Variable<Double> x1 = F.create("x1inc");
        Variable<Double> x2 = F.create("x2inc");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2.0, 2.0), List.of(1.0, 1.0), 1.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntervalDomain.of(0.0, 5.0),
                x2, IntervalDomain.of(0.0, 5.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void toString_doubleLimit_displayedAsDecimal() {
        Variable<Double> xd = F.create("xds");
        var c = CumulativeConstraint.of(List.of(xd), List.of(1.5), List.of(0.5), 2.5);
        assertThat(c.toString()).isEqualTo("<(xds), cumulative(limit=2.5, tasks=1)>");
    }

    @Test
    void propagate_taskExceedsCapacity_returnsEmpty() {
        // Task resource=3 > limit=2, no compulsory part (wide domain).
        // slack = 2-3 = -1 < 0 → exclusive profile is always > slack, covering
        // the NEGATIVE_INFINITY initialisation and post-loop closure branches.
        Variable<Integer> x = F.create("x_cap");
        var c = CumulativeConstraint.of(List.of(x), List.of(1), List.of(3), 2);
        var result = c.propagate(Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x, IntRangeDomain.of(0, 5)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_overlappingCompulsoryParts_staysOverloaded() {
        // xA=[1,1] d=3 r=1 → compulsory [1,4); xB=[2,2] d=3 r=1 → compulsory [2,5).
        // limit=2, so global profile stays within capacity (max=2 at t∈[2,4)).
        // For xC (r=2, d=1, limit=2, slack=0): exclusive events are xA's and xB's.
        // At t=2, profile goes from 1→2 while already overloaded (1>0 is true) — the
        // "wasOver=true && isOver=true" (stays in overload) branch is exercised.
        Variable<Integer> xA = F.create("xA_ov");
        Variable<Integer> xB = F.create("xB_ov");
        Variable<Integer> xC = F.create("xC_ov");
        var c = CumulativeConstraint.of(List.of(xA, xB, xC), List.of(3, 3, 1), List.of(1, 1, 2), 2);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                xA, IntRangeDomain.of(1, 1),
                xB, IntRangeDomain.of(2, 2),
                xC, IntRangeDomain.of(0, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        // xC starts at 0 are the only slot not forbidden by overloaded [1,5);
        // timetabling however only sees the open interval (0,5) as forbidden,
        // so newEst=0 (boundary, not open-forbidden) and newLst=5 (also boundary):
        // no tightening is reported by the propagator.
        assertThat(result.get()).isEmpty();
    }
}
