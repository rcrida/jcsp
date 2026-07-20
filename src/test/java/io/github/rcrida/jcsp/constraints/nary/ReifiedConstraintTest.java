package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryReifiedUnaryConstraint;
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

public class ReifiedConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> B = F.create("b");
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");

    // body: x = 3
    static final UnaryValueConstraint<Integer> BODY =
            UnaryValueConstraint.of(X, 3);

    static Assignment a(boolean b, int x) {
        return Assignment.builder().value(B, b).value(X, x).build();
    }

    @Test
    void satisfiedWhenIndicatorMatchesBody() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.isSatisfiedBy(a(true,  3))).isTrue();   // b=true,  x=3  (body satisfied)
        assertThat(rc.isSatisfiedBy(a(false, 4))).isTrue();   // b=false, x=4  (body not satisfied)
        assertThat(rc.isSatisfiedBy(a(true,  4))).isFalse();  // b=true,  x!=3 (mismatch)
        assertThat(rc.isSatisfiedBy(a(false, 3))).isFalse();  // b=false, x=3  (mismatch)
    }

    @Test
    void optimisticallyTrueForPartialAssignment() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.isSatisfiedBy(Assignment.builder().value(B, true).build())).isTrue();
        assertThat(rc.isSatisfiedBy(Assignment.builder().value(B, false).build())).isTrue();
        assertThat(rc.isSatisfiedBy(Assignment.builder().build())).isTrue();
    }

    @Test
    void variablesContainsIndicatorAndBodyVariables() {
        assertThat(ReifiedConstraint.of(B, BODY).getVariables()).containsExactlyInAnyOrder(B, X);
    }

    @Test
    void unaryBody_decomposesToBinaryReifiedConstraint() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.getAsBinaryConstraints()).hasSize(1);
        assertThat(rc.getAsBinaryConstraints().iterator().next())
                .isInstanceOf(BinaryReifiedUnaryConstraint.class);
    }

    @Test
    void nonUnaryBody_noDecomposition() {
        val body = BinaryNotEqualsConstraint.<Integer>builder().left(X).right(Y).build();
        assertThat(ReifiedConstraint.of(B, body).getAsBinaryConstraints()).isEmpty();
    }

    @Test
    void getRelationDescribesEquivalence() {
        assertThat(ReifiedConstraint.of(B, BODY).getRelation()).contains("<->");
    }

    @Test
    void forcedIndicator_propagatesToBodyVariable() {
        // b <-> (x = 3), b forced true => x must be 3
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .reifyConstraint(B, BODY)
                .equalsConstraint(B, true)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(X)).hasValue(3);
    }

    @Test
    void testToString() {
        assertThat(ReifiedConstraint.of(B, BODY).toString()).isEqualTo("<(b, x), b <-> (x == 3)>");
    }

    // --- propagate() ---

    static final Domain<Boolean> BOTH = BooleanDomain.INSTANCE;
    static final Domain<Boolean> TRUE = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.FALSE).build();
    static final Domain<Boolean> FALSE_DOM = BooleanDomain.INSTANCE.toBuilder().delete(Boolean.TRUE).build();

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_fullyDeterminedUnsatisfied_infeasible() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(4, 4));
        assertThat(rc.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_fullyDeterminedSatisfied_noChange() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(3, 3));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorTrue_nonPropagatableBody_notFullyDetermined_noChange() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(1, 5));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorFalse_bodySatisfied_infeasible() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, FALSE_DOM, X, IntRangeDomain.of(3, 3));
        assertThat(rc.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_indicatorFalse_bodyUnsatisfied_noChange() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, FALSE_DOM, X, IntRangeDomain.of(4, 4));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_indicatorOpen_bodyFullyDeterminedSatisfied_forcesIndicatorTrue() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(3, 3));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(B)).isEqualTo(TRUE);
    }

    @Test
    void propagate_indicatorOpen_bodyFullyDeterminedUnsatisfied_forcesIndicatorFalse() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(4, 4));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(B)).isEqualTo(FALSE_DOM);
    }

    @Test
    void propagate_indicatorOpen_bodyNotDetermined_nonPropagatableBody_noChange() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(1, 5));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // Propagatable body: UnaryComparatorConstraint over an IntervalDomain that can never hold.
    static final Variable<Double> Z = F.create("z");
    static final UnaryComparatorConstraint<Double> IMPOSSIBLE_BODY =
            UnaryComparatorConstraint.of(Z, Operator.GEQ, 10.0);

    @Test
    void propagate_indicatorTrue_propagatableBody_delegatesInfeasibility() {
        val rc = ReifiedConstraint.of(B, IMPOSSIBLE_BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, Z, IntervalDomain.of(1, 5));
        assertThat(rc.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_indicatorOpen_propagatableBodyInfeasible_forcesIndicatorFalse() {
        val rc = ReifiedConstraint.of(B, IMPOSSIBLE_BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, Z, IntervalDomain.of(1, 5));
        val result = rc.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(B)).isEqualTo(FALSE_DOM);
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_indicatorNotSingleton_empty() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, BOTH, X, IntRangeDomain.of(4, 4));
        assertThat(rc.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_bodyNotFullyDetermined_empty() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(1, 5));
        assertThat(rc.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_indicatorTrueBodyFalse_citesBoth() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, TRUE, X, IntRangeDomain.of(4, 4));
        assertThat(rc.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(B, true, X, 4)));
    }

    @Test
    void explainInfeasible_indicatorFalseBodyTrue_citesBoth() {
        val rc = ReifiedConstraint.of(B, BODY);
        Map<Variable<?>, Domain<?>> domains = Map.of(B, FALSE_DOM, X, IntRangeDomain.of(3, 3));
        assertThat(rc.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(B, false, X, 3)));
    }
}
