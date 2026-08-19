package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NaryConflictTuplesConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x_conflict");
    Variable<Integer> y = F.create("y_conflict");

    // x,y in {0,1,2}: forbid the diagonal (0,0), (1,1), (2,2) -- an all-different-like restriction.
    Assignment c1 = Assignment.of(Map.of(x, 0, y, 0));
    Assignment c2 = Assignment.of(Map.of(x, 1, y, 1));
    Assignment c3 = Assignment.of(Map.of(x, 2, y, 2));

    NaryConflictTuplesConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = NaryConflictTuplesConstraint.of(Set.of(c1, c2, c3));
    }

    // --- isSatisfiedBy ---

    @Test void listedConflict_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 0)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isFalse();
    }

    @Test void unlistedCombination_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 0)))).isTrue();
    }

    @Test void partialAssignment_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0)))).isTrue();
    }

    // --- of() validation ---

    @Test void of_mismatchedVariableSets_asserts() {
        var other = Assignment.of(Map.of(x, 1)); // missing y
        assertThatThrownBy(() -> NaryConflictTuplesConstraint.of(Set.of(c1, other)))
                .isInstanceOf(AssertionError.class);
    }

    @Test void of_emptyConflicts_asserts() {
        assertThatThrownBy(() -> NaryConflictTuplesConstraint.of(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- toString / getRelation ---

    @Test void testToString_rendersNotIn() {
        assertThat(constraint.toString()).contains("NOT IN").contains("(0, 0)").contains("(1, 1)").contains("(2, 2)");
    }

    // --- propagate(): the pigeonhole counting argument ---

    @Test void propagate_valueFullyCoveredByConflicts_pruned() {
        // y restricted to {0}: x=0 is the only value whose sole remaining completion (y=0) is
        // listed as a conflict -- the live-conflict count for x=0 (1) equals the other-domain
        // product (|{0}| = 1), so x=0 is pruned. x=1,2 still have y=0 as a valid (non-conflicting)
        // completion, so they survive.
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(0, 2), y, IntRangeDomain.of(0, 0));
        var result = constraint.propagate(domains).orElseThrow();
        assertThat(result.get(x)).isEqualTo(IntRangeDomain.of(1, 2));
    }

    @Test void propagate_noLiveConflicts_noChange() {
        // x restricted to {1,2}: none of the three listed conflicts (0,0),(1,1),(2,2) has an
        // x-value still present in x's domain and a y-value still present in y's domain
        // simultaneously for every listed conflict -- specifically (0,0) is dead outright (x=0 no
        // longer in domain), (1,1) and (2,2) are each dead too once y is pinned away from their
        // own value, leaving zero live conflicts and nothing to prune.
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 2), y, IntRangeDomain.of(0, 0));
        var result = constraint.propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_noValueFullyCovered_noChange() {
        // Every value of x still has at least one non-conflicting completion in y in {0,1,2}.
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(0, 2), y, IntRangeDomain.of(0, 2));
        var result = constraint.propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_everyValuePruned_infeasible() {
        // x,y both pinned to {0}: the only possible combination is the listed conflict (0,0), so
        // x=0's live-conflict count (1) equals the other-domain product (1) -- x is pruned to
        // empty, which propagate reports as infeasible.
        var c = NaryConflictTuplesConstraint.of(Set.of(c1)); // just (0,0)
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(0, 0), y, IntRangeDomain.of(0, 0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test void propagate_wideDomainFarExceedsConflictCount_earlyExitSkipsVariable() {
        // A single conflict can never fully cover a wide domain -- the early-exit short-circuit
        // (otherDomainProduct capped against conflicts.size()) means this variable is skipped
        // entirely without finishing the (otherwise huge) product computation.
        var c = NaryConflictTuplesConstraint.of(Set.of(c1));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(0, 999), y, IntRangeDomain.of(0, 999));
        var result = c.propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test void explainInfeasible_allSingleton_returnsFullReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(0, 0), y, IntRangeDomain.of(0, 0));
        assertThat(constraint.explainInfeasible(domains)).contains(RangeNogoodConstraint.of(Map.of(
                x, IntervalDomain.of(0, 0), y, IntervalDomain.of(0, 0))));
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_fallsThroughToEmpty() {
        var c = NaryConflictTuplesConstraint.of(Set.of(c1));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DiscreteDomain.of(0, 5), y, IntRangeDomain.of(0, 0));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- ConstraintSatisfactionProblem builder wiring ---

    @Test void solver_excludesOnlyListedConflicts() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 2))
                .variableDomain(y, IntRangeDomain.of(0, 2))
                .conflictTuplesConstraint(Set.of(c1, c2, c3))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(6); // 9 combinations minus the 3-element diagonal
        for (var s : solutions) {
            assertThat(s.getValue(x)).isNotEqualTo(s.getValue(y));
        }
    }
}
