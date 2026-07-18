package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct tests of {@code Solver.Factory.FULL_PROPAGATION_INFERENCE}'s {@code applyWithReason}
 * override -- the single-pass propagate-and-explain combination that replaced the deleted
 * {@code MacAndFixpointConflictExplainer}/{@code ConflictExplainer} pair (2026-07-18): explaining a
 * conflict is now this {@code Inference}'s own job, not a second strategy object's, so the reason
 * is derived inline rather than by a separate, from-scratch re-derivation.
 */
class FullPropagationInferenceTest {

    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void applyWithReason_macWipesSingletonNeighbour_citesBothValuesDirectly() {
        // y's only possible value is 1, but notEquals(x,y) forbids y=1 once x=1. MAC assigns x=1
        // (singleton via AssignedDomain): the notEquals arc wipes y's domain entirely -- and since
        // y was already singleton ({1}) before the wipeout, AC3's allSingletonReason cites both
        // values directly. Unlike the old MacAndFixpointConflictExplainer (which never attempted
        // this and always fell back to the raw assignment on a MAC failure), this is a genuine
        // improvement that falls out of routing the MAC failure through AC3#applyQueueWithReason
        // instead of a bare Optional.empty().
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(x, 1, y, 1)));
    }

    @Test
    void applyWithReason_macWipesNonSingletonNeighbour_fallsBackToAssignment() {
        // A constraint that's unsatisfiable for every pair empties y's domain (size 3) in one AC3
        // pass without y ever having been singleton -- allSingletonReason can't cite a value for a
        // non-singleton side, so AC3 reports no reason, and applyWithReason falls back to the full
        // assignment (matching Inference#applyWithReason's own default fallback).
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(10, 12))
                .biPredicateConstraint(x, y, (a, b) -> false)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(assignment.getValues()));
    }

    @Test
    void applyWithReason_macSucceedsButAllDiffHallViolation_producesRangeNogoodOverViolatingVariables() {
        // x=3 causes notEquals(x,a/b/c) to narrow a,b,c from {1,2,3} to {1,2}.
        // With a=b=c={1,2} after MAC, pairwise AC3 finds every value supported (e.g. a=1
        // is supported by b=2) so AC3 does NOT detect the infeasibility. AllDiff GAC detects
        // the Hall violation (3 variables confined to 2 values) via propagate(), and its own
        // explainInfeasible finds the same Hall-violating subset -- but a,b,c are not all
        // singleton here, so allSingletonReason yields no ground reason (tier 1 empty). Tier 2
        // (FixpointConsistency's generic current-bounds fallback) then fires: AllDiffConstraint's
        // propagate() already reported infeasible given exactly a,b,c's current {1,2} domains, so
        // citing those bounds as a RangeNogoodConstraint is sound -- and strictly better than the
        // assignment-wide fallback, since it excludes x (irrelevant to AllDiff) and generalises
        // over the whole {1,2} range rather than one exact value.
        Variable<Integer> x = F.create("x");
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(3, 4))
                .variableDomain(a, IntRangeDomain.of(1, 3))
                .variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(x, a)
                .notEqualsConstraint(x, b)
                .notEqualsConstraint(x, c)
                .allDiffConstraint(Set.of(a, b, c))
                .build();
        var assignment = Assignment.of(Map.of(x, 3));
        ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                a, IntervalDomain.of(1.0, 2.0), b, IntervalDomain.of(1.0, 2.0), c, IntervalDomain.of(1.0, 2.0))));
    }

    @Test
    void applyWithReason_fixpointFindsReason_returnsReasonInsteadOfAssignment() {
        // x, y are unrelated to a, b: MAC starting from x only revises y (notEquals), succeeds,
        // and never touches a/b. The fixpoint then reaches comparatorConstraint(a, LEQ, b): a=[5,5]
        // is pinned singleton and already exceeds b's range [0,3], so
        // BinaryComparatorConstraint.propagateWithReasons reports {a=5.0} as the reason (see
        // BinaryComparatorConstraintTest) instead of falling back to the full assignment.
        Variable<Integer> x = F.create("x"), y = F.create("y");
        Variable<Double> a = F.create("a"), b = F.create("b");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .variableDomain(a, IntervalDomain.of(5.0, 5.0))
                .variableDomain(b, IntervalDomain.of(0.0, 3.0))
                .notEqualsConstraint(x, y)
                .comparatorConstraint(a, Operator.LEQ, b)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(a, 5.0)));
        assertThat(result.reason()).isNotEqualTo(GroundNogoodConstraint.of(assignment.getValues()));
    }

    @Test
    void applyWithReason_feasible_returnsNarrowedProblem() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.problem()).isEqualTo(
                Solver.Factory.FULL_PROPAGATION_INFERENCE.apply(csp, x, assignment).orElseThrow());
    }
}
