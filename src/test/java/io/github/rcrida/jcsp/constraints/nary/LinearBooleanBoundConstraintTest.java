package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinearBooleanBoundConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Boolean> b1 = F.create("b1");
    Variable<Boolean> b2 = F.create("b2");

    // 2*b1 + 3*b2 == 5
    LinearBooleanBoundConstraint<Integer> eq5;

    @BeforeEach
    void setUp() {
        eq5 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, 5);
    }

    // --- isSatisfiedBy() ---

    @Test
    void weightedSum_satisfied() {
        // 2*true + 3*true = 5
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, true)))).isTrue();
    }

    @Test
    void weightedSum_notSatisfied() {
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of(b1, false, b2, true)))).isFalse(); // 3
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, false)))).isFalse(); // 2
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of(b1, false, b2, false)))).isFalse(); // 0
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq5.isSatisfiedBy(Assignment.of(Map.of(b1, true)))).isTrue();
    }

    @Test
    void leq_satisfied() {
        var leq5 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.LEQ, 5);
        assertThat(leq5.isSatisfiedBy(Assignment.of(Map.of(b1, false, b2, false)))).isTrue();
        assertThat(leq5.isSatisfiedBy(Assignment.of(Map.of(b1, true, b2, true)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(eq5.toString()).isEqualTo("<(b1, b2), 2*b1 + 3*b2 == 5>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, 5)).isEqualTo(eq5);
    }

    @Test
    void weightedSum_byte() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, (byte) 5);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, false, b, true)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, (short) 5);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, false, b, true)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, 5L);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, false, b, true)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, 5.0f);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, false, b, true)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, 5.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, false, b, true)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedBoundType() {
        Variable<Boolean> a = F.create("a"), b = F.create("b");
        var c = LinearBooleanBoundConstraint.<Number>builder()
                .variables(java.util.Set.of(a, b))
                .coefficients(Map.of(a, (Number) 2, b, (Number) 3))
                .bound(new AtomicInteger(5))
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, true, b, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported type");
    }

    @Test
    void solver_infeasibleLinearBooleanBoundConstraint_returnsNoSolutions() {
        // 2*b1 + 3*b2 == 4: unreachable (only 0, 2, 3, 5 are achievable)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(b1, BooleanDomain.INSTANCE)
                .variableDomain(b2, BooleanDomain.INSTANCE)
                .linearBooleanConstraint(Map.of(b1, 2, b2, 3), Operator.EQ, 4)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }

    @Test
    void solver_findsExactSolution() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(b1, BooleanDomain.INSTANCE)
                .variableDomain(b2, BooleanDomain.INSTANCE)
                .linearBooleanConstraint(Map.of(b1, 2, b2, 3), Operator.EQ, 5)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.getFirst().getValue(b1)).hasValue(true);
        assertThat(solutions.getFirst().getValue(b2)).hasValue(true);
    }

    // --- propagate() ---

    @Test
    void propagate_eq_forcesTrue() {
        // 2*b1 + 3*b2 == 5, b2 fixed true (contributes 3): only b1 == true reaches 5
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE,
                b2, BooleanDomain.TRUE_ONLY);
        var result = eq5.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b1)).isEqualTo(BooleanDomain.TRUE_ONLY);
        assertThat(result.get()).doesNotContainKey(b2);
    }

    @Test
    void propagate_eq_forcesFalse() {
        // 2*b1 + 3*b2 == 0, b2 fixed false (contributes 0): only b1 == false reaches 0
        var eq0 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, 0);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE,
                b2, BooleanDomain.FALSE_ONLY);
        var result = eq0.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b1)).isEqualTo(BooleanDomain.FALSE_ONLY);
    }

    @Test
    void propagate_leq_forcesFalse() {
        // 2*b1 + 3*b2 <= 4, b1 fixed true (contributes 2): b2 == true would total 5 > 4
        var leq4 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.LEQ, 4);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.INSTANCE);
        var result = leq4.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b2)).isEqualTo(BooleanDomain.FALSE_ONLY);
    }

    @Test
    void propagate_geq_forcesTrue() {
        // 2*b1 + 3*b2 >= 5, b2 fixed true (contributes 3): b1 == false would total 3 < 5
        var geq5 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.GEQ, 5);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE,
                b2, BooleanDomain.TRUE_ONLY);
        var result = geq5.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b1)).isEqualTo(BooleanDomain.TRUE_ONLY);
    }

    @Test
    void propagate_bothStillPossible_noNarrowing() {
        // b1+b2+b3 == 1 ("exactly one of three"), all open: no single variable can be forced yet
        Variable<Boolean> b3 = F.create("b3");
        var atMostOneOfThree = LinearBooleanBoundConstraint.of(Map.of(b1, 1, b2, 1, b3, 1), Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE, b3, BooleanDomain.INSTANCE);
        var result = atMostOneOfThree.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_alreadyResolved_notReDerivedEveryRound() {
        // Both fixed true; constraint already satisfied (5) -- no re-narrowing reported.
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY);
        var result = eq5.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var neq = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.NEQ, 5);
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        var result = neq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_globalCheck() {
        // 2*b1 + 3*b2 == 10: max achievable is 5
        var eq10 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        assertThat(eq10.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_infeasible() {
        // 2*b1 + 3*b2 <= 1, both fixed true: min achievable once fixed is 5 > 1
        var leq1 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.LEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.TRUE_ONLY,
                b2, BooleanDomain.TRUE_ONLY);
        assertThat(leq1.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_infeasible() {
        // 2*b1 + 3*b2 >= 10, both open: max achievable is 5
        var geq10 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.GEQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        assertThat(geq10.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_perVariableInfeasibility_beyondGlobalCheck() {
        // 10*b1 + 1*b2 == 5, both open: globally 5 is within [0,11], but neither b1=false (max 1)
        // nor b1=true (min 10) can reach exactly 5 -- genuinely unreachable (only 0,1,10,11 exist).
        var c = LinearBooleanBoundConstraint.of(Map.of(b1, 10, b2, 1), Operator.EQ, 5);
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        assertThat(c.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        var result = eq5.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_allSingleton_infeasible_attributesAll() {
        // b1=false, b2=false (both singleton): 0 != 5 -> infeasible; both values are a sound,
        // self-contained explanation.
        var domains = Map.<Variable<?>, Domain<?>>of(
                b1, BooleanDomain.FALSE_ONLY,
                b2, BooleanDomain.FALSE_ONLY);
        var result = eq5.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(b1, false, b2, false)));
    }

    @Test
    void propagateWithReasons_notAllSingleton_returnsEmptyReason() {
        // 2*b1 + 3*b2 == 10, both open: infeasible (max 5), but neither is pinned.
        var eq10 = LinearBooleanBoundConstraint.of(Map.of(b1, 2, b2, 3), Operator.EQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(b1, BooleanDomain.INSTANCE, b2, BooleanDomain.INSTANCE);
        var result = eq10.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
