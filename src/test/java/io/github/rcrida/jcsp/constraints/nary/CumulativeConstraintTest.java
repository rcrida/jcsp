package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void timetable_tightensStartBound() {
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
        var result = c.timetable(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void timetable_tightensLatestStart() {
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
        var result = c.timetable(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(x2);
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(0, 1));
    }

    @Test
    void timetable_infeasible_returnsEmpty() {
        // Both tasks have compulsory parts that together exceed limit
        // s1 ∈ [1,1] (fixed), s2 ∈ [1,1] (fixed), d=2, r=1, limit=1
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var c = CumulativeConstraint.of(List.of(x1, x2), List.of(2, 2), List.of(1, 1), 1);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 1));
        assertThat(c.timetable(domains)).isEmpty();
    }

    @Test
    void timetable_zeroDuration_returnsNoChange() {
        // With duration=0, minTime == maxTime → early return with no changes.
        Variable<Integer> x = F.create("x");
        var c = CumulativeConstraint.of(List.of(x), List.of(0), List.of(1), 1);
        var result = c.timetable(Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x, IntRangeDomain.of(0, 0)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void timetable_infeasibleViaExclusiveProfile() {
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
        assertThat(c.timetable(domains)).isEmpty();
    }

    @Test
    void timetable_noChange_returnsEmptyMap() {
        // Wide domains, no compulsory parts → no pruning
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                s1, IntRangeDomain.of(0, 5),
                s2, IntRangeDomain.of(0, 5));
        var result = constraint.timetable(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
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
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(6);
    }
}
