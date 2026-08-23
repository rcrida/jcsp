package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CumulativeVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void of_unequalDurationsLength_asserts() {
        Variable<Integer> s1 = F.create("s1");
        assertThatThrownBy(() -> CumulativeVariableConstraint.of(
                List.of(s1), List.of(F.create("d1"), F.create("d2")), List.of(F.create("r1")), 1))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_unequalResourcesLength_asserts() {
        Variable<Integer> s1 = F.create("s1");
        Variable<Integer> s2 = F.create("s2");
        assertThatThrownBy(() -> CumulativeVariableConstraint.of(
                List.of(s1, s2), List.of(F.create("d1"), F.create("d2")), List.of(F.create("r1")), 1))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy() ---

    @Test
    void isSatisfiedBy_missingStart_optimisticallyTrue() {
        Variable<Integer> s = F.create("s"), d = F.create("d"), r = F.create("r");
        var c = CumulativeVariableConstraint.of(List.of(s), List.of(d), List.of(r), 1);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(d, 2, r, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_missingDuration_optimisticallyTrue() {
        Variable<Integer> s = F.create("s"), d = F.create("d"), r = F.create("r");
        var c = CumulativeVariableConstraint.of(List.of(s), List.of(d), List.of(r), 1);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s, 0, r, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_missingResource_optimisticallyTrue() {
        Variable<Integer> s = F.create("s"), d = F.create("d"), r = F.create("r");
        var c = CumulativeVariableConstraint.of(List.of(s), List.of(d), List.of(r), 1);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s, 0, d, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_nonOverlapping_satisfied() {
        Variable<Integer> s1 = F.create("s1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> s2 = F.create("s2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(s1, s2), List.of(d1, d2), List.of(r1, r2), 1);
        // s1=[0,2), s2=[2,4): no overlap
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 0, d1, 2, r1, 1, s2, 2, d2, 2, r2, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_overlapping_notSatisfied() {
        Variable<Integer> s1 = F.create("s1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> s2 = F.create("s2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(s1, s2), List.of(d1, d2), List.of(r1, r2), 1);
        // s1=[0,2), s2=[1,3): overlap at t=1, combined resource 2 > limit 1
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s1, 0, d1, 2, r1, 1, s2, 1, d2, 2, r2, 1)))).isFalse();
    }

    // --- propagate(): timetabling, generalised to domain-minimum duration/resource ---

    @Test
    void propagate_tightensStartBound_matchesFixedSizeCase() {
        // Same numbers as CumulativeConstraintTest#propagate_tightensStartBound, but duration/
        // resource are singleton-domain Variables rather than fixed constants -- confirms the
        // domain-minimum generalisation reduces exactly to the fixed-size algorithm.
        Variable<Integer> x1 = F.create("x1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> x2 = F.create("x2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(0, 1), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void propagate_tightensLatestStart_matchesFixedSizeCase() {
        // Same numbers as CumulativeConstraintTest#propagate_tightensLatestStart.
        Variable<Integer> x1 = F.create("x1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> x2 = F.create("x2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(2, 3), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(2, 2),
                x2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(0, 1));
    }

    @Test
    void propagate_globalOverload_returnsEmpty() {
        // Both tasks fixed at start=1, duration=2, resource=1, limit=1 -- compulsory parts collide.
        Variable<Integer> x1 = F.create("x1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> x2 = F.create("x2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_zeroDuration_returnsNoChange() {
        Variable<Integer> x = F.create("x"), d = F.create("d"), r = F.create("r");
        var c = CumulativeVariableConstraint.of(List.of(x), List.of(d), List.of(r), 1);
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 0), d, IntRangeDomain.of(0, 0), r, IntRangeDomain.of(1, 1)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_infeasibleViaExclusiveProfile() {
        // Same shape as CumulativeConstraintTest#propagate_infeasibleViaExclusiveProfile.
        Variable<Integer> x1 = F.create("x1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> x2 = F.create("x2"), d2 = F.create("d2"), r2 = F.create("r2");
        Variable<Integer> x3 = F.create("x3"), d3 = F.create("d3"), r3 = F.create("r3");
        var c = CumulativeVariableConstraint.of(
                List.of(x1, x2, x3), List.of(d1, d2, d3), List.of(r1, r2, r3), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(0, 0), d1, IntRangeDomain.of(1, 1), r1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1), d2, IntRangeDomain.of(1, 1), r2, IntRangeDomain.of(1, 1),
                x3, IntRangeDomain.of(0, 1), d3, IntRangeDomain.of(1, 1), r3, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_wideDomains_returnsEmptyMap() {
        Variable<Integer> s1 = F.create("s1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> s2 = F.create("s2"), d2 = F.create("d2"), r2 = F.create("r2");
        var c = CumulativeVariableConstraint.of(List.of(s1, s2), List.of(d1, d2), List.of(r1, r2), 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                s1, IntRangeDomain.of(0, 5), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(1, 1),
                s2, IntRangeDomain.of(0, 5), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(1, 1));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_intervalDomain_tightensEarliestStart() {
        // Continuous (IntervalDomain) start variables, exercising the BoundedDomain output branch.
        // x1 ∈ [0.0,1.0], d=2.0, r=2.0 → compulsory part [1.0,2.0). x2 ∈ [0.0,3.0], d=2.0, r=2.0,
        // limit=2.0 → same shape as propagate_tightensStartBound_matchesFixedSizeCase, doubled.
        Variable<Double> x1 = F.create("ix1"), d1 = F.create("id1"), r1 = F.create("ir1");
        Variable<Double> x2 = F.create("ix2"), d2 = F.create("id2"), r2 = F.create("ir2");
        var c = CumulativeVariableConstraint.of(
                List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 2.0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntervalDomain.of(0.0, 1.0), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(2, 2),
                x2, IntervalDomain.of(0.0, 3.0), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntervalDomain.of(2.0, 3.0));
    }

    @Test
    void propagate_taskExceedsCapacity_returnsEmpty() {
        // Task resource=3 > limit=2, no compulsory part (wide domain). slack = 2-3 = -1 < 0 -> the
        // exclusive profile is always above slack, covering the NEGATIVE_INFINITY initialisation
        // and post-loop closure branches.
        Variable<Integer> x = F.create("capx"), d = F.create("capd"), r = F.create("capr");
        var c = CumulativeVariableConstraint.of(List.of(x), List.of(d), List.of(r), 2);
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 5), d, IntRangeDomain.of(1, 1), r, IntRangeDomain.of(3, 3)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_overlappingCompulsoryParts_staysOverloaded() {
        // xA=[1,1] d=3 r=1 -> compulsory [1,4); xB=[2,2] d=3 r=1 -> compulsory [2,5). limit=2, so
        // global profile stays within capacity. For xC (r=2, d=1, limit=2, slack=0): at t=2 the
        // exclusive profile goes from 1->2 while already overloaded (1>0 true) -- the
        // "wasOver=true && isOver=true" (stays in overload) branch is exercised.
        Variable<Integer> xA = F.create("ovA"), dA = F.create("ovdA"), rA = F.create("ovrA");
        Variable<Integer> xB = F.create("ovB"), dB = F.create("ovdB"), rB = F.create("ovrB");
        Variable<Integer> xC = F.create("ovC"), dC = F.create("ovdC"), rC = F.create("ovrC");
        var c = CumulativeVariableConstraint.of(
                List.of(xA, xB, xC), List.of(dA, dB, dC), List.of(rA, rB, rC), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                xA, IntRangeDomain.of(1, 1), dA, IntRangeDomain.of(3, 3), rA, IntRangeDomain.of(1, 1),
                xB, IntRangeDomain.of(2, 2), dB, IntRangeDomain.of(3, 3), rB, IntRangeDomain.of(1, 1),
                xC, IntRangeDomain.of(0, 5), dC, IntRangeDomain.of(1, 1), rC, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate(): energy overload ---

    @Test
    void propagate_energyOverload_threeTasksNoCompulsoryPart_infeasible() {
        // Same shape as CumulativeConstraintTest's fixed-size energy-overload test: 3 tasks,
        // duration=3 resource=2 (singleton), start domain [0,3] (width == duration, no compulsory
        // part), limit=2. Θ=all 3: energy=18 > capacity=2*6=12.
        Variable<Integer> t1 = F.create("t1"), d1 = F.create("d1"), r1 = F.create("r1");
        Variable<Integer> t2 = F.create("t2"), d2 = F.create("d2"), r2 = F.create("r2");
        Variable<Integer> t3 = F.create("t3"), d3 = F.create("d3"), r3 = F.create("r3");
        var c = CumulativeVariableConstraint.of(
                List.of(t1, t2, t3), List.of(d1, d2, d3), List.of(r1, r2, r3), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                t1, IntRangeDomain.of(0, 3), d1, IntRangeDomain.of(3, 3), r1, IntRangeDomain.of(2, 2),
                t2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(3, 3), r2, IntRangeDomain.of(2, 2),
                t3, IntRangeDomain.of(0, 3), d3, IntRangeDomain.of(3, 3), r3, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_energyOverload_genuineNonSingletonDurationDomain_infeasible() {
        // Same as above, but duration is a real [3,5] domain (not singleton) -- confirms the
        // domain-minimum substitution actually engages for a genuine variable, not just a
        // degenerate singleton standing in for a constant. dmin=3 still drives the same
        // energy=18 > capacity=12 conclusion regardless of duration's upper bound.
        Variable<Integer> t1 = F.create("gt1"), d1 = F.create("gd1"), r1 = F.create("gr1");
        Variable<Integer> t2 = F.create("gt2"), d2 = F.create("gd2"), r2 = F.create("gr2");
        Variable<Integer> t3 = F.create("gt3"), d3 = F.create("gd3"), r3 = F.create("gr3");
        var c = CumulativeVariableConstraint.of(
                List.of(t1, t2, t3), List.of(d1, d2, d3), List.of(r1, r2, r3), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                t1, IntRangeDomain.of(0, 3), d1, IntRangeDomain.of(3, 5), r1, IntRangeDomain.of(2, 2),
                t2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(3, 5), r2, IntRangeDomain.of(2, 2),
                t3, IntRangeDomain.of(0, 3), d3, IntRangeDomain.of(3, 5), r3, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_energyAtCapacityBoundary_feasible() {
        // limit=3: energy=18, capacity=3*6=18 -- not strictly exceeded, feasible.
        Variable<Integer> t1 = F.create("bt1"), d1 = F.create("bd1"), r1 = F.create("br1");
        Variable<Integer> t2 = F.create("bt2"), d2 = F.create("bd2"), r2 = F.create("br2");
        Variable<Integer> t3 = F.create("bt3"), d3 = F.create("bd3"), r3 = F.create("br3");
        var c = CumulativeVariableConstraint.of(
                List.of(t1, t2, t3), List.of(d1, d2, d3), List.of(r1, r2, r3), 3);
        var domains = Map.<Variable<?>, Domain<?>>of(
                t1, IntRangeDomain.of(0, 3), d1, IntRangeDomain.of(3, 3), r1, IntRangeDomain.of(2, 2),
                t2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(3, 3), r2, IntRangeDomain.of(2, 2),
                t3, IntRangeDomain.of(0, 3), d3, IntRangeDomain.of(3, 3), r3, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_globalOverload_allSingleton_attributesStartDurationResource() {
        Variable<Integer> x1 = F.create("ex1"), d1 = F.create("ed1"), r1 = F.create("er1");
        Variable<Integer> x2 = F.create("ex2"), d2 = F.create("ed2"), r2 = F.create("er2");
        var c = CumulativeVariableConstraint.of(List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(
                GroundNogoodConstraint.of(Map.of(x1, 1, d1, 2, r1, 1, x2, 1, d2, 2, r2, 1)));
    }

    @Test
    void explainInfeasible_globalOverload_durationNotSingleton_returnsEmpty() {
        // Same as above but d1 is not singleton -- the culprit set can't be cited (duration
        // variables are part of every citation here, not just start variables, for soundness --
        // see the class's own Javadoc).
        Variable<Integer> x1 = F.create("nx1"), d1 = F.create("nd1"), r1 = F.create("nr1");
        Variable<Integer> x2 = F.create("nx2"), d2 = F.create("nd2"), r2 = F.create("nr2");
        var c = CumulativeVariableConstraint.of(List.of(x1, x2), List.of(d1, d2), List.of(r1, r2), 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1), d1, IntRangeDomain.of(2, 3), r1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(1, 1));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_exclusiveProfileFailure_notSingleton_returnsEmpty() {
        // Same domains as propagate_infeasibleViaExclusiveProfile: x3's start domain [0,1] isn't
        // singleton, so the full culprit set isn't singleton either.
        Variable<Integer> x1 = F.create("px1"), d1 = F.create("pd1"), r1 = F.create("pr1");
        Variable<Integer> x2 = F.create("px2"), d2 = F.create("pd2"), r2 = F.create("pr2");
        Variable<Integer> x3 = F.create("px3"), d3 = F.create("pd3"), r3 = F.create("pr3");
        var c = CumulativeVariableConstraint.of(
                List.of(x1, x2, x3), List.of(d1, d2, d3), List.of(r1, r2, r3), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(0, 0), d1, IntRangeDomain.of(1, 1), r1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1), d2, IntRangeDomain.of(1, 1), r2, IntRangeDomain.of(1, 1),
                x3, IntRangeDomain.of(0, 1), d3, IntRangeDomain.of(1, 1), r3, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_energyOverload_notSingleton_returnsEmpty() {
        // Same domains as propagate_energyOverload_threeTasksNoCompulsoryPart_infeasible: start
        // domains [0,3] aren't singleton, so the energy-overload culprit set can't be cited. Not a
        // gap specific to this test: if start+duration+resource were all singleton for every task
        // in Θ, the earlier global-overload check (a fully-determined scenario always shows up as
        // a point-in-time capacity breach too, by pigeonhole) would already have caught it first --
        // this branch's citation can only ever reach this sound, conservative outcome in practice,
        // the same conclusion as CumulativeConstraint's own energy-overload citation.
        Variable<Integer> t1 = F.create("et1"), d1 = F.create("ed1"), r1 = F.create("er1");
        Variable<Integer> t2 = F.create("et2"), d2 = F.create("ed2"), r2 = F.create("er2");
        Variable<Integer> t3 = F.create("et3"), d3 = F.create("ed3"), r3 = F.create("er3");
        var c = CumulativeVariableConstraint.of(
                List.of(t1, t2, t3), List.of(d1, d2, d3), List.of(r1, r2, r3), 2);
        var domains = Map.<Variable<?>, Domain<?>>of(
                t1, IntRangeDomain.of(0, 3), d1, IntRangeDomain.of(3, 3), r1, IntRangeDomain.of(2, 2),
                t2, IntRangeDomain.of(0, 3), d2, IntRangeDomain.of(3, 3), r2, IntRangeDomain.of(2, 2),
                t3, IntRangeDomain.of(0, 3), d3, IntRangeDomain.of(3, 3), r3, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_feasible_returnsEmptyReason() {
        Variable<Integer> s1 = F.create("fs1"), d1 = F.create("fd1"), r1 = F.create("fr1");
        Variable<Integer> s2 = F.create("fs2"), d2 = F.create("fd2"), r2 = F.create("fr2");
        var c = CumulativeVariableConstraint.of(List.of(s1, s2), List.of(d1, d2), List.of(r1, r2), 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                s1, IntRangeDomain.of(0, 5), d1, IntRangeDomain.of(2, 2), r1, IntRangeDomain.of(1, 1),
                s2, IntRangeDomain.of(0, 5), d2, IntRangeDomain.of(2, 2), r2, IntRangeDomain.of(1, 1));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void testToString() {
        Variable<Integer> s1 = F.create("s1"), d1 = F.create("d1"), r1 = F.create("r1");
        var c = CumulativeVariableConstraint.of(List.of(s1), List.of(d1), List.of(r1), 1);
        assertThat(c.toString()).isEqualTo("<(d1, r1, s1), cumulativeVariable(tasks=1)>");
    }

    @Test
    void solver_serialTasks_solutionCount() {
        // Same shape as CumulativeConstraintTest#solver_serialTasks_solutionCount, but through
        // ConstraintSatisfactionProblemBuilder#cumulativeVariableConstraint, with duration/resource
        // fixed to singleton [2,2]/[1,1] domains (so behaviour matches the fixed-size case exactly).
        Variable<Integer> s1 = F.create("ss1"), d1 = F.create("sd1"), r1 = F.create("sr1");
        Variable<Integer> s2 = F.create("ss2"), d2 = F.create("sd2"), r2 = F.create("sr2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s1, IntRangeDomain.of(0, 3))
                .variableDomain(s2, IntRangeDomain.of(0, 3))
                .variableDomain(d1, IntRangeDomain.of(2, 2))
                .variableDomain(d2, IntRangeDomain.of(2, 2))
                .variableDomain(r1, IntRangeDomain.of(1, 1))
                .variableDomain(r2, IntRangeDomain.of(1, 1))
                .cumulativeVariableConstraint(List.of(s1, s2), List.of(d1, d2), List.of(r1, r2), 1)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(6);
    }
}
