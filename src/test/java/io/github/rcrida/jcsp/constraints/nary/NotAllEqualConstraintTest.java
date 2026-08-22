package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NotAllEqualConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void of_fewerThanTwoVariables_throws() {
        Variable<Integer> x1 = F.create("x1");
        assertThatThrownBy(() -> NotAllEqualConstraint.of(Set.of(x1)))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy() ---

    @Test
    void isSatisfiedByEmpty() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = NotAllEqualConstraint.of(Set.of(x1, x2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByPartial_notYetAllAssigned_optimisticallyTrue() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5, x2, 5)))).isTrue();
    }

    @Test
    void isSatisfiedByFullyAssignedAllSame_false() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5, x2, 5, x3, 5)))).isFalse();
    }

    @Test
    void isSatisfiedByFullyAssignedOneDifferent_true() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.of(Set.of(x1, x2, x3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x1, 5, x2, 5, x3, 6)))).isTrue();
    }

    @Test
    void getRelation_sortedByVariableName() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = NotAllEqualConstraint.of(Set.of(x2, x1));
        assertThat(c.getRelation()).isEqualTo("NotAllEqual(x1, x2)");
    }

    // --- propagate() ---

    @Test
    void propagate_twoOpenVariables_noOp() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 5),
                x3, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_twoSingletonsAlreadyDiffer_noOp() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(2, 2),
                x3, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_oneOpenSharesCommonValue_forcesItAway() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(3, 3),
                x2, IntRangeDomain.of(3, 3),
                x3, IntRangeDomain.of(3, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x3);
        assertThat(result.get().get(x3)).isEqualTo(IntRangeDomain.of(4, 5));
    }

    @Test
    void propagate_oneOpenDoesNotContainCommonValue_noOp() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(3, 3),
                x2, IntRangeDomain.of(3, 3),
                x3, IntRangeDomain.of(4, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_allSingletonAllEqual_infeasible() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(7, 7),
                x2, IntRangeDomain.of(7, 7),
                x3, IntRangeDomain.of(7, 7));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_allSingletonSomeDiffer_noOp() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(7, 7),
                x2, IntRangeDomain.of(7, 7),
                x3, IntRangeDomain.of(8, 8));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_allSingletonAllEqual_groundReason() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(7, 7),
                x2, IntRangeDomain.of(7, 7),
                x3, IntRangeDomain.of(7, 7));
        assertThat(c.explainInfeasible(domains)).contains(
                GroundNogoodConstraint.of(Map.of(x1, 7, x2, 7, x3, 7)));
    }

    @Test
    void explainInfeasible_notAllSingleton_empty() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = NotAllEqualConstraint.<Integer>of(Set.of(x1, x2, x3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(7, 7),
                x2, IntRangeDomain.of(7, 7),
                x3, IntRangeDomain.of(3, 5));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- solver integration ---

    @Test
    void solver_notAllEqual_excludesOnlyTheAllSameAssignment() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var domain = IntRangeDomain.of(1, 2);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .notAllEqualConstraint(Set.of(x1, x2, x3))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        // 2^3 = 8 total combinations minus the 2 all-same ones (1,1,1) and (2,2,2)
        assertThat(solutions).hasSize(6);
        solutions.forEach(s -> {
            int v1 = s.getValue(x1).orElseThrow();
            int v2 = s.getValue(x2).orElseThrow();
            int v3 = s.getValue(x3).orElseThrow();
            assertThat(v1 == v2 && v2 == v3).isFalse();
        });
    }
}
