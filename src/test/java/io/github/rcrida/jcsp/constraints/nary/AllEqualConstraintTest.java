package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AllEqualConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void isSatisfiedByEmpty() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.of(Set.of(x1, x2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingle() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.of(Set.of(x1, x2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5)))).isTrue();
    }

    @Test
    void isSatisfiedByAllSame() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5, x2, 5, x3, 5)))).isTrue();
    }

    @Test
    void isSatisfiedByOneDifferent() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5, x2, 5, x3, 6)))).isFalse();
    }

    @Test
    void getRelation_sortedByVariableName() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.of(Set.of(x2, x1));
        assertThat(c.getRelation()).isEqualTo("x1 == x2");
    }

    @Test
    void getAsBinaryConstraints_chainOfConsecutivePairs_isCompleteDecomposition() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.getAsBinaryConstraints()).hasSize(2); // n-1 pairs for n variables
        assertThat(c.isDecompositionComplete()).isTrue();
    }

    @Test
    void getAsBinaryConstraints_singleVariable_empty() {
        Variable<Integer> x1 = F.create("x1");
        var c = AllEqualConstraint.of(Set.of(x1));
        assertThat(c.getAsBinaryConstraints()).isEmpty();
    }

    // --- propagate() ---

    @Test
    void propagate_narrowsToSharedIntersection() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5),
                x2, IntRangeDomain.of(3, 8),
                x3, IntRangeDomain.of(2, 4));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        // shared intersection of {1..5}, {3..8}, {2..4} = {3,4}
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(3, 4));
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(3, 4));
        assertThat(result.get().get(x3)).isEqualTo(IntRangeDomain.of(3, 4));
    }

    @Test
    void propagate_alreadyEqualDomains_noChange() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 3),
                x2, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_disjointDomains_infeasible() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(3, 4));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_threeVariablesEarlyEmptyIntersection_shortCircuitsRemainingScan() {
        // Any two of {1,2}/{3,4}/{5,6} are already mutually disjoint, so regardless of iteration
        // order, `shared` empties out after the second variable is folded in, leaving the third
        // variable's own domain never checked -- exercises the loop guard's `!shared.isEmpty()`
        // short-circuit becoming false while `i < vars.size()` is still true, not just the loop
        // running to completion.
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(3, 4),
                x3, IntRangeDomain.of(5, 6));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_singleVariable_noChange() {
        Variable<Integer> x1 = F.create("x1");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1));
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_disjointSingletons_groundReason() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(2, 2));
        assertThat(c.explainInfeasible(domains)).contains(
                GroundNogoodConstraint.of(Map.of(x1, 1, x2, 2)));
    }

    @Test
    void explainInfeasible_disjointNonSingletonNumeric_rangeReason() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(5, 6),
                x3, IntRangeDomain.of(1, 6));
        // x1={1,2} and x2={5,6} are disjoint -- tight pair reason, not full-collective
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get().getVariables()).containsExactlyInAnyOrder(x1, x2);
    }

    @Test
    void explainInfeasible_noPairDisjoint_bothFallbacksDecline_returnsEmpty() {
        // x1={1,2}, x2={2,3}, x3={1,3}: every pair intersects (Helly property would force a common
        // point for genuine intervals), but no value is common to all three -- only possible with a
        // gapped domain (x3), which is exactly what makes both fallbacks decline: no variable is
        // singleton (ground fails) and x3 isn't gapless (range fails; RangeNogoodConstraint's own
        // safety gate requires size == max-min+1, and x3 has size 2 over span 3). This isn't a
        // coincidence: gapless integer ranges satisfy 1D Helly (pairwise overlap implies a common
        // point), so genuine "no pair disjoint yet jointly infeasible" is only reachable via
        // non-range-citable domains -- meaning this fallback branch can never actually succeed for
        // this constraint, only be attempted and decline.
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(2, 3),
                x3, NumericDiscreteDomain.of(1, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_disjointPairNonNumeric_neitherGroundNorRangeCitable_continuesLoop() {
        // Non-numeric T: RangeNogoodConstraint always declines (not Number-based), so a disjoint
        // non-singleton pair can't be cited by either fallback -- the loop must continue past it
        // (here, to the end, since n=2) rather than returning a false-positive "found" result.
        Variable<String> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllEqualConstraint.<String>of(Set.of(x1, x2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, DiscreteDomain.of("a", "b"),
                x2, DiscreteDomain.of("c", "d"));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- solver integration ---

    @Test
    void solver_allEqual_onlyEqualAssignmentsSurvive() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .allEqualConstraint(Set.of(x1, x2, x3))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(3); // (1,1,1), (2,2,2), (3,3,3)
        solutions.forEach(s -> {
            int v1 = (int) s.getValue(x1).orElseThrow();
            assertThat(s.getValue(x2)).contains(v1);
            assertThat(s.getValue(x3)).contains(v1);
        });
    }
}
