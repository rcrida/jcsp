package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SetMembershipConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Set<Integer>> S = F.create("s_member");

    // --- isSatisfiedByValue ---

    @Test void isSatisfiedByValue_trueWhenElementPresent() {
        assertThat(SetMembershipConstraint.of(S, 2).isSatisfiedByValue(Set.of(1, 2, 3))).isTrue();
    }

    @Test void isSatisfiedByValue_falseWhenElementAbsent() {
        assertThat(SetMembershipConstraint.of(S, 4).isSatisfiedByValue(Set.of(1, 2, 3))).isFalse();
    }

    // --- toString ---

    @Test void toString_format() {
        assertThat(SetMembershipConstraint.of(S, 2).toString()).isEqualTo("<(s_member), 2 in s_member>");
    }

    // --- propagate: non-SetBoundedDomain ---

    @Test void propagate_nonSetBoundedDomain_noOp() {
        var domain = DiscreteDomain.of(Set.of(1), Set.of(1, 2));
        var result = SetMembershipConstraint.of(S, 1).propagate(Map.of(S, domain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: narrowing ---

    @Test void propagate_forcesElementIntoLowerBound() {
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3);
        var result = SetMembershipConstraint.of(S, 1).propagate(Map.of(S, domain)).orElseThrow();
        assertThat(result).containsOnlyKeys(S);
        @SuppressWarnings("unchecked")
        var narrowed = (SetIntervalDomain<Integer>) result.get(S);
        assertThat(narrowed.getLowerBound()).isEqualTo(Set.of(1));
    }

    @Test void propagate_noOpWhenElementAlreadyInLowerBound() {
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        var result = SetMembershipConstraint.of(S, 1).propagate(Map.of(S, domain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate: infeasibility ---

    @Test void propagate_infeasibleWhenElementNotInUpperBound() {
        var domain = SetIntervalDomain.of(Set.of(), Set.of(2, 3), 0, 2);
        var result = SetMembershipConstraint.of(S, 1).propagate(Map.of(S, domain));
        assertThat(result).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_delegatesToSetBoundsNogood() {
        var domain = SetIntervalDomain.of(Set.of(), Set.of(2, 3), 0, 2);
        var reason = SetMembershipConstraint.of(S, 1).explainInfeasible(Map.of(S, domain));
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(SetBoundsNogoodConstraint.class);
    }

    // --- isNecessarilySatisfied ---

    @Test void isNecessarilySatisfied_trueWhenElementInLowerBound() {
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 0, 3);
        assertThat(SetMembershipConstraint.of(S, 1).isNecessarilySatisfied(Map.of(S, domain))).isTrue();
    }

    @Test void isNecessarilySatisfied_falseWhenElementNotYetInLowerBound() {
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3);
        assertThat(SetMembershipConstraint.of(S, 1).isNecessarilySatisfied(Map.of(S, domain))).isFalse();
    }

    @Test void isNecessarilySatisfied_falseForNonSetBoundedDomain() {
        var domain = DiscreteDomain.of(Set.of(1));
        assertThat(SetMembershipConstraint.of(S, 1).isNecessarilySatisfied(Map.of(S, domain))).isFalse();
    }

    // --- end-to-end: reified via ReifiedConstraint, resolved by the full solver chain ---

    /**
     * Forces the indicator true before {@code s} is fully resolved -- {@code s}'s own construction
     * already has element {@code 1} in its lower bound, so {@link
     * SetMembershipConstraint#isNecessarilySatisfied} fires immediately, letting {@code indicator}
     * resolve without waiting for {@code s} to become a full singleton.
     */
    @Test void solvesEndToEnd_reifiedIndicatorResolvesFromNecessarySatisfaction() {
        Variable<Set<Integer>> s = F.create("S");
        Variable<Boolean> indicator = F.create("ind");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s, SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2))
                .variableDomain(indicator, BooleanDomain.INSTANCE)
                .reifyConstraint(indicator, SetMembershipConstraint.of(s, 1))
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(indicator)).contains(true);
    }

    /** Exercises {@code CSP.Builder#setMembershipConstraint}, used directly (not reified) as a hard constraint. */
    @Test void solvesEndToEnd_viaBuilderHelperAsHardConstraint() {
        Variable<Set<Integer>> s = F.create("S2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1))
                .setMembershipConstraint(s, 1)
                .build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(s)).contains(Set.of(1));
    }
}
