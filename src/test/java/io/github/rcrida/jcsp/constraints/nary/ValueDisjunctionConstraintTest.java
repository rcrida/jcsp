package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueDisjunctionConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final DiscreteDomain<Integer> ZERO_ONE = IntRangeDomain.of(0, 1);
    static final Domain<Integer> ZERO_ONLY = ZERO_ONE.toBuilder().delete(1).build();
    static final Domain<Integer> ONE_ONLY = ZERO_ONE.toBuilder().delete(0).build();
    static final DiscreteDomain<Integer> ONE_TO_THREE = IntRangeDomain.of(1, 3);

    static Map<Variable<Integer>, Integer> literals(Variable<Integer> a, int ta, Variable<Integer> b, int tb) {
        Map<Variable<Integer>, Integer> literals = new LinkedHashMap<>();
        literals.put(a, ta);
        literals.put(b, tb);
        return literals;
    }

    @Test
    void of_emptyLiterals_throws() {
        assertThatThrownBy(() -> ValueDisjunctionConstraint.of(Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy() ---

    @Test
    void isSatisfiedByEmptyAssignment() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByPartialAssignmentNoMatchYet() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0)))).isTrue();
    }

    @Test
    void isSatisfiedByOneLiteralMatches() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 1)))).isTrue();
    }

    @Test
    void isSatisfiedByCompleteAssignmentNoMatch() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0, b, 1)))).isFalse();
    }

    @Test
    void notBinaryDecomposable() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint).isNotInstanceOf(BinaryDecomposable.class);
    }

    @Test
    void getRelation() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.getRelation()).isEqualTo("a == 1 OR b == 0");
    }

    @Test
    void testToString() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        assertThat(constraint.toString()).isEqualTo("<(a, b), a == 1 OR b == 0>");
    }

    // --- propagate() ---

    @Test
    void propagate_exactlyOnePossible_forcesSingleton() {
        // a's target (0) already excluded, b's domain still open {0,1} → force b to its target 1.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 0, b, 1));
        var result = constraint.propagate(Map.of(a, ONE_ONLY, b, ZERO_ONE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ONE_ONLY);
    }

    @Test
    void propagate_infeasible_everyLiteralExcluded() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 0, b, 1));
        assertThat(constraint.propagate(Map.of(a, ONE_ONLY, b, ZERO_ONLY))).isEmpty();
    }

    @Test
    void propagate_noChange_moreThanOnePossible() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 0, b, 1));
        var result = constraint.propagate(Map.of(a, ZERO_ONE, b, ZERO_ONE));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_definitelySatisfied_shortCircuitsRegardlessOfOtherLiterals() {
        // a is already singleton at its own target (0); b already excludes its own target (1) --
        // would look infeasible if only b were inspected, but a's definite match satisfies the
        // whole disjunction first.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 0, b, 1));
        var result = constraint.propagate(Map.of(a, ZERO_ONLY, b, ZERO_ONLY));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_nonBooleanDomain_forcesSingletonToNonTrivialTarget() {
        // a's target (1) already excluded (domain is {2,3}), b's domain {1,2,3} still open →
        // force b to its target 2, deleting both 1 and 3.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        Domain<Integer> aDomain = ONE_TO_THREE.toBuilder().delete(1).build();
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 2));
        var result = constraint.propagate(Map.of(a, aDomain, b, ONE_TO_THREE));
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
        assertThat(result.get().get(b)).isEqualTo(ONE_TO_THREE.toBuilder().delete(1).delete(3).build());
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_allSingleton_producesGroundNogood() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        var constraint = ValueDisjunctionConstraint.of(literals(a, 0, b, 1));
        var result = constraint.explainInfeasible(Map.of(a, ONE_ONLY, b, ZERO_ONLY));
        assertThat(result).contains(GroundNogoodConstraint.of(Map.of(a, 1, b, 0)));
    }

    @Test
    void explainInfeasible_nonSingletonDomain_producesValueSetNogood() {
        Variable<Integer> a = F.create("a"), b = F.create("b");
        Domain<Integer> aDomain = ONE_TO_THREE.toBuilder().delete(1).build(); // {2,3}, target 1 excluded, not singleton
        var constraint = ValueDisjunctionConstraint.of(literals(a, 1, b, 0));
        var result = constraint.explainInfeasible(Map.of(a, aDomain, b, ONE_ONLY));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(ValueSetNogoodConstraint.class);
    }
}
