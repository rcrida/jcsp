package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ImplicationConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> B = F.create("b");
    static final Variable<Integer> X = F.create("x");

    static final UnaryValueConstraint<Integer> BODY =
            UnaryValueConstraint.of(X, 3);

    static Assignment a(boolean b, int x) {
        return Assignment.builder().value(B, b).value(X, x).build();
    }

    @Test
    void satisfiedWhenIndicatorFalseRegardlessOfBody() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(a(false, 3))).isTrue();
        assertThat(ic.isSatisfiedBy(a(false, 4))).isTrue();
    }

    @Test
    void trueIndicator_enforcesBody() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(a(true, 3))).isTrue();
        assertThat(ic.isSatisfiedBy(a(true, 4))).isFalse();
    }

    @Test
    void optimisticallyTrueForPartialAssignment() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(Assignment.builder().value(B, true).build())).isTrue();
        assertThat(ic.isSatisfiedBy(Assignment.builder().build())).isTrue();
    }

    @Test
    void variablesContainsIndicatorAndBodyVariables() {
        assertThat(ImplicationConstraint.of(B, BODY).getVariables()).containsExactlyInAnyOrder(B, X);
    }

    @Test
    void getRelationDescribesImplication() {
        assertThat(ImplicationConstraint.of(B, BODY).getRelation()).contains("->");
    }

    @Test
    void forcedIndicator_enforcesBodyInSolver() {
        // b -> (x = 3), b forced true => x must be 3
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .impliesConstraint(B, BODY)
                .equalsConstraint(B, true)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(X)).hasValue(3);
    }

    @Test
    void falseIndicator_allBodyValuesAllowed() {
        // b -> (x = 3), b forced false => all x values valid
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .impliesConstraint(B, BODY)
                .equalsConstraint(B, false)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(5);
    }

    @Test
    void testToString() {
        assertThat(ImplicationConstraint.of(B, BODY).toString()).isEqualTo("<(b, x), b -> (x == 3)>");
    }

    // --- propagate() ---

    static final Domain<Boolean> BOTH = BooleanDomain.INSTANCE;
    static final Domain<Boolean> TRUE = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.FALSE).build();
    static final Domain<Boolean> FALSE_DOM = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.TRUE).build();

    @Test
    void propagate_indicatorFalse_noChange() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, FALSE_DOM, X, IntRangeDomain.of(1, 5));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_fullyDeterminedUnsatisfied_infeasible() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(4, 4));
        assertThat(ic.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_fullyDeterminedSatisfied_noChange() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(3, 3));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_notFullyDetermined_noChange() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(1, 5));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorOpen_bodyFullyDeterminedUnsatisfied_forcesIndicatorFalse() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(4, 4));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(B)).isEqualTo(FALSE_DOM);
    }

    @Test
    void propagate_indicatorOpen_bodyFullyDeterminedSatisfied_noChange() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(3, 3));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorOpen_bodyNotDetermined_nonPropagatableBody_noChange() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(1, 5));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // Propagatable body: UnaryComparatorConstraint over an IntervalDomain that can never hold.
    static final Variable<Double> Z = F.create("z");
    static final UnaryComparatorConstraint<Double> IMPOSSIBLE_BODY =
            UnaryComparatorConstraint.of(Z, Operator.GEQ, 10.0);

    @Test
    void propagate_indicatorTrue_propagatableBody_delegatesInfeasibility() {
        val ic = ImplicationConstraint.of(B, IMPOSSIBLE_BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, Z, IntervalDomain.of(1, 5));
        assertThat(ic.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_indicatorOpen_propagatableBodyInfeasible_forcesIndicatorFalse() {
        val ic = ImplicationConstraint.of(B, IMPOSSIBLE_BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, Z, IntervalDomain.of(1, 5));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(B)).isEqualTo(FALSE_DOM);
    }

    @Test
    void propagate_indicatorOpen_propagatableBodyFeasible_noChange() {
        val ic = ImplicationConstraint.of(B, IMPOSSIBLE_BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, Z, IntervalDomain.of(1, 20));
        val result = ic.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_indicatorNotSingleton_empty() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(4, 4));
        assertThat(ic.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_indicatorFalse_empty() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, FALSE_DOM, X, IntRangeDomain.of(4, 4));
        assertThat(ic.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_bodyNotFullyDetermined_empty() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(1, 5));
        assertThat(ic.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_indicatorTrueBodyFalse_citesBoth() {
        val ic = ImplicationConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(4, 4));
        assertThat(ic.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(B, true, X, 4)));
    }
}
