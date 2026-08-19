package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.github.rcrida.jcsp.constraints.nary.NaryStarredTuplesConstraint.STAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NaryStarredTuplesConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x_starred");
    Variable<Integer> y = F.create("y_starred");
    Variable<Integer> z = F.create("z_starred");

    static Map<Variable<?>, Object> tuple(Variable<?> vx, Object xv, Variable<?> vy, Object yv, Variable<?> vz, Object zv) {
        Map<Variable<?>, Object> t = new HashMap<>();
        t.put(vx, xv);
        t.put(vy, yv);
        t.put(vz, zv);
        return t;
    }

    // t1: x=1, y and z unconstrained. t2: x=2, y and z unconstrained.
    Map<Variable<?>, Object> t1 = tuple(x, 1, y, STAR, z, STAR);
    Map<Variable<?>, Object> t2 = tuple(x, 2, y, STAR, z, STAR);

    NaryStarredTuplesConstraint constraint = NaryStarredTuplesConstraint.of(Set.of(t1, t2));

    // --- isSatisfiedBy ---

    @Test void starMatchesAnyValue_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 999, z, -5)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 0, z, 0)))).isTrue();
    }

    @Test void nonStarPositionMismatch_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 1, z, 1)))).isFalse();
    }

    @Test void partialAssignment_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1)))).isTrue();
    }

    // --- of() validation ---

    @Test void of_mismatchedVariableSets_asserts() {
        Map<Variable<?>, Object> other = Map.of(x, 1, y, 1); // missing z
        assertThatThrownBy(() -> NaryStarredTuplesConstraint.of(Set.of(t1, other)))
                .isInstanceOf(AssertionError.class);
    }

    @Test void of_emptyTuples_asserts() {
        assertThatThrownBy(() -> NaryStarredTuplesConstraint.of(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- toString / getRelation ---

    @Test void testToString_rendersStarLiterally() {
        assertThat(constraint.toString()).contains("*").contains("(1, *, *)").contains("(2, *, *)");
    }

    // --- propagate(): concrete position pruned, starred positions left untouched ---

    @Test void propagate_prunesConcretePosition_leavesStarredPositionsUntouched() {
        // x is constrained to {1,2} by the two live tuples; y and z are starred in both, so every
        // live tuple supports every value currently in their domains -- no pruning, not even
        // narrowed to a materialised union of "used" values.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 3), y, IntRangeDomain.of(1, 3), z, IntRangeDomain.of(1, 3));
        var result = constraint.propagate(domains).orElseThrow();
        assertThat(result.get(x)).isEqualTo(IntRangeDomain.of(1, 2));
        assertThat(result).doesNotContainKey(y);
        assertThat(result).doesNotContainKey(z);
    }

    @Test void propagate_noLiveTuples_infeasible() {
        // x restricted to {3}: neither t1 (x=1) nor t2 (x=2) is live.
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(3, 3), y, IntRangeDomain.of(1, 3), z, IntRangeDomain.of(1, 3));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test void propagate_starredTupleStillRequiresItsOwnConcretePositionsToBeLive() {
        // t3 = (2, 2, *): starred at z, but still requires x=2 AND y=2 to be live -- a wildcard
        // elsewhere in the tuple doesn't exempt its own concrete positions from the liveness check.
        var c = NaryStarredTuplesConstraint.of(Set.of(tuple(x, 2, y, 2, z, STAR)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(2, 2), z, IntRangeDomain.of(1, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test void propagate_allTuplesLive_xNotFullyUsed_prunesToUsedValues() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(0, 2), y, IntRangeDomain.of(1, 1), z, IntRangeDomain.of(1, 1));
        // x's domain {0,1,2}: only 1 and 2 are supported (0 is not a tuple value at all)
        var result = constraint.propagate(domains).orElseThrow();
        assertThat(result.get(x)).isEqualTo(IntRangeDomain.of(1, 2));
    }

    // --- explainInfeasible() ---

    @Test void explainInfeasible_allSingleton_returnsFullReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(3, 3), y, IntRangeDomain.of(1, 1), z, IntRangeDomain.of(1, 1));
        assertThat(constraint.explainInfeasible(domains)).contains(RangeNogoodConstraint.of(Map.of(
                x, IntervalDomain.of(3, 3), y, IntervalDomain.of(1, 1), z, IntervalDomain.of(1, 1))));
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_fallsThroughToEmpty() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DiscreteDomain.of(3, 5), y, IntRangeDomain.of(1, 1), z, IntRangeDomain.of(1, 1));
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    // --- ConstraintSatisfactionProblem builder wiring ---

    @Test void solver_findsExactTupleSolutions() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .starredTuplesConstraint(Set.of(t1, t2))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(18); // x in {1,2} (2 options) * y in {1,2,3} * z in {1,2,3}
        for (var s : solutions) {
            assertThat(s.getValue(x).orElseThrow()).isIn(1, 2);
        }
    }
}
