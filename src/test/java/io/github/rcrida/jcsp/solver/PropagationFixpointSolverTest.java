package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class PropagationFixpointSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    /** Wraps a no-op inner solver so we can inspect the preprocessed CSP. */
    static PropagationFixpointSolver solverWith(Solver inner) {
        return PropagationFixpointSolver.builder().inner(inner).build();
    }

    @Test
    void singlePassSuffices_noFeedbackNeeded() {
        // x1∈{1,2}, x2∈{1,2}, allDiff — no downstream constraint, converges in one loop pass
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .allDiffConstraint(Set.of(x1, x2))
                .build();
        assertThat(solverWith(csp2 -> Stream.empty()).getSolutions(csp)).isEmpty();
    }

    @Test
    void feedbackRequired_ac3ThenAllDiffThenAc3() {
        // x1∈{1,2}, x2∈{1,2}, x3∈{1,2,3}, allDiff(x1,x2,x3), notEquals(x3, x4), x4∈{3,4,5}
        // Pass 1: AC3 no change; AllDiff naked-pair → x3={3}
        // Pass 2: AC3 propagates x3=3 → x4∈{4,5}; AllDiff no further change
        // Pass 3: no change → exit
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        Variable<Integer> x3 = F.create("x3"), x4 = F.create("x4");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 3))
                .variableDomain(x4, IntRangeDomain.of(3, 5))
                .allDiffConstraint(Set.of(x1, x2, x3))
                .notEqualsConstraint(x3, x4)
                .build();

        // Use the full solver to observe the effect after propagation
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        // x3 must be 3; x4 must be 4 or 5
        solutions.forEach(a -> {
            assertThat(a.getValue(x3)).hasValue(3);
            assertThat(a.getValue(x4)).hasValueSatisfying(v -> assertThat(v).isIn(4, 5));
        });
        assertThat(solutions).isNotEmpty();
    }

    @Test
    void infeasibleViaAC3_returnsEmpty() {
        // x1={1} and x2={1} with x1≠x2 — AC3 wipes x2's domain
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x1, x2)
                .build();
        assertThat(solverWith(c -> Stream.empty()).getSolutions(csp)).isEmpty();
    }

    @Test
    void infeasibleViaSum_returnsEmpty() {
        // v1∈{5..9}, v2∈{5..9}, sum ≤ 8 — min sum = 10 > 8 → SumConsistency detects infeasibility
        Variable<Integer> v1 = F.create("v1"), v2 = F.create("v2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(5, 9))
                .variableDomain(v2, IntRangeDomain.of(5, 9))
                .sumConstraint(Set.of(v1, v2), Operator.LEQ, 8)
                .build();
        assertThat(solverWith(c -> Stream.empty()).getSolutions(csp)).isEmpty();
    }

    @Test
    void infeasibleViaAllDiff_returnsEmpty() {
        // Three variables all with domain {1,2} — Hall violation for allDiff
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 2))
                .allDiffConstraint(Set.of(x1, x2, x3))
                .build();
        assertThat(solverWith(c -> Stream.empty()).getSolutions(csp)).isEmpty();
    }

    @Test
    void explainConflict_feasibleCsp_returnsEmptyMap() {
        // SumConstraint reduces domains in first pass (changed=true branch) but doesn't fail.
        // Second pass: no further progress (changed=false branch) → while exits → Map.of().
        Variable<Integer> x = F.create("ecx"), y = F.create("ecy");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(PropagationFixpointSolver.explainConflict(csp)).isEmpty();
    }

    @Test
    void fullChain_macFailureDuringSearch_coversPostMacEmptyBranch() {
        // biPredicateConstraint(a==b) contradicts notEqualsConstraint(a,b).
        // Top-level AC3 misses the contradiction (each value has separate support in the other constraint).
        // When DomWdeg assigns a=v, MAC propagates b=v (biPredicate) and b≠v (notEquals)
        // simultaneously, emptying b's domain → postMac.isEmpty()=true in ConflictExplainer.
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 2))
                .variableDomain(b, IntRangeDomain.of(1, 2))
                .variableDomain(c, IntRangeDomain.of(1, 2))
                .biPredicateConstraint(a, b, (x, y) -> x.equals(y))
                .notEqualsConstraint(a, b)
                .notEqualsConstraint(a, c)
                .notEqualsConstraint(b, c)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList()).isEmpty();
    }

}
