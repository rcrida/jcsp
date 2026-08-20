package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueConjunctionConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static Map<Variable<Integer>, Integer> literals(Variable<Integer> a, int ta, Variable<Integer> b, int tb) {
        Map<Variable<Integer>, Integer> literals = new LinkedHashMap<>();
        literals.put(a, ta);
        literals.put(b, tb);
        return literals;
    }

    @Test
    void of_emptyLiterals_throws() {
        assertThatThrownBy(() -> ValueConjunctionConstraint.of(Map.of(), Operator.EQ))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy() ---

    @Test
    void isSatisfiedByEmptyAssignment() {
        Variable<Integer> a = F.create("s1a"), b = F.create("s1b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByPartialAssignmentMatchingSoFar() {
        Variable<Integer> a = F.create("s2a"), b = F.create("s2b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1)))).isTrue();
    }

    @Test
    void isSatisfiedByPartialAssignmentAlreadyViolated() {
        // Unlike an OR-shaped constraint, a single wrong literal disproves the whole conjunction
        // even though b is not yet assigned at all.
        Variable<Integer> a = F.create("s3a"), b = F.create("s3b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 5)))).isFalse();
    }

    @Test
    void isSatisfiedByCompleteAssignmentAllMatch() {
        Variable<Integer> a = F.create("s4a"), b = F.create("s4b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 2)))).isTrue();
    }

    @Test
    void isSatisfiedByNeqOperator() {
        Variable<Integer> a = F.create("s5a"), b = F.create("s5b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3, b, 3)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 3)))).isFalse();
    }

    @Test
    void notBinaryDecomposable() {
        Variable<Integer> a = F.create("s6a"), b = F.create("s6b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c).isNotInstanceOf(BinaryDecomposable.class);
    }

    @Test
    void getRelation() {
        Variable<Integer> a = F.create("s7a"), b = F.create("s7b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        assertThat(c.getRelation()).isEqualTo("s7a == 1 AND s7b == 2");
    }

    @Test
    void testToString() {
        Variable<Integer> a = F.create("s8a"), b = F.create("s8b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        assertThat(c.toString()).isEqualTo("<(s8a, s8b), s8a != 1 AND s8b != 2>");
    }

    // --- propagate(): EQ ---

    @Test
    void propagate_eq_narrowsBothToSingletons() {
        Variable<Integer> a = F.create("p1a"), b = F.create("p1b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(a, b);
        assertThat(result.get().get(a)).isEqualTo(IntRangeDomain.of(1, 1));
        assertThat(result.get().get(b)).isEqualTo(IntRangeDomain.of(2, 2));
    }

    @Test
    void propagate_eq_alreadySingletonAtTarget_noChangeForThatLiteral() {
        Variable<Integer> a = F.create("p2a"), b = F.create("p2b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1, 1), b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
    }

    @Test
    void propagate_eq_targetNotInDomain_returnsEmpty() {
        Variable<Integer> a = F.create("p3a"), b = F.create("p3b");
        var c = ValueConjunctionConstraint.of(literals(a, 5, b, 2), Operator.EQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagate(): NEQ ---

    @Test
    void propagate_neq_deletesTargetFromBothDomains() {
        Variable<Integer> a = F.create("p4a"), b = F.create("p4b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(a)).isEqualTo(IntRangeDomain.of(0, 3).toBuilder().delete(1).build());
        assertThat(result.get().get(b)).isEqualTo(IntRangeDomain.of(0, 3).toBuilder().delete(2).build());
    }

    @Test
    void propagate_neq_targetAlreadyExcluded_noChangeForThatLiteral() {
        Variable<Integer> a = F.create("p5a"), b = F.create("p5b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        Domain<Integer> aDomain = IntRangeDomain.of(0, 3).toBuilder().delete(1).build();
        var domains = Map.<Variable<?>, Domain<?>>of(a, aDomain, b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b);
    }

    @Test
    void propagate_neq_deletingTargetEmptiesDomain_returnsEmpty() {
        Variable<Integer> a = F.create("p6a"), b = F.create("p6b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1, 1), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagate(): ordering operators are a no-op ---

    @Test
    void propagate_orderingOperator_noOpRegardlessOfDomains() {
        Variable<Integer> a = F.create("p7a"), b = F.create("p7b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.LT);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(5, 5), b, IntRangeDomain.of(0, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_eq_citesOnlyFailingVariable() {
        Variable<Integer> a = F.create("e1a"), b = F.create("e1b");
        var c = ValueConjunctionConstraint.of(literals(a, 5, b, 2), Operator.EQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get().getVariables()).containsExactly(a);
    }

    @Test
    void explainInfeasible_neq_citesOnlyFailingVariable() {
        Variable<Integer> a = F.create("e2a"), b = F.create("e2b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1, 1), b, IntRangeDomain.of(0, 3));
        assertThat(c.propagate(domains)).isEmpty();
        var reason = c.explainInfeasible(domains);
        assertThat(reason).isPresent();
        assertThat(reason.get().getVariables()).containsExactly(a);
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        Variable<Integer> a = F.create("e3a"), b = F.create("e3b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.EQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_orderingOperator_calledStandalone_returnsEmpty() {
        // propagate() never itself reports infeasible for an ordering operator (always a no-op), so
        // this method is only ever reached this way via a direct, standalone call -- must still
        // answer safely rather than throw, per this codebase's "safe to call explainInfeasible even
        // when not preceded by a failing propagate()" convention.
        Variable<Integer> a = F.create("e4a"), b = F.create("e4b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.LT);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 3), b, IntRangeDomain.of(0, 3));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_neq_singletonAtDifferentValue_isNotImpossible() {
        // a's domain is singleton {5}, target is 1: 5 != 1 already satisfies the NEQ requirement,
        // so this literal is fine (not impossible) despite being singleton -- distinguishes
        // literalImpossible's isSingleton() check from its separate contains(target) check.
        Variable<Integer> a = F.create("e5a"), b = F.create("e5b");
        var c = ValueConjunctionConstraint.of(literals(a, 1, b, 2), Operator.NEQ);
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(5, 5), b, IntRangeDomain.of(0, 3));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }
}
