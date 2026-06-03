package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class CountConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Color> v4;

    CountConstraint<Color> eq2;
    CountConstraint<Color> leq1;
    CountConstraint<Color> geq2;

    @BeforeEach
    void setUp() {
        eq2  = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.EQ,  2);
        leq1 = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.LEQ, 1);
        geq2 = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.GEQ, 2);
    }

    @Test
    void countEqualsN_satisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void countBelowN_notSatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void countAboveN_notSatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.RED, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void leq_countAtBound_satisfied() {
        assertThat(leq1.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void leq_countAboveBound_notSatisfied() {
        assertThat(leq1.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void geq_countAtBound_satisfied() {
        assertThat(geq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void geq_countBelowBound_notSatisfied() {
        assertThat(geq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(eq2.toString()).isEqualTo("<(v1, v2, v3, v4), count(RED) == 2>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.EQ, 2)).isEqualTo(eq2);
    }

    // --- propagate() ---

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Color> a = F.create("a");
    static final Variable<Color> b = F.create("b");
    static final Variable<Color> c = F.create("c");

    static final Domain<Color> RED_ONLY   = DomainObjectSet.<Color>builder().value(Color.RED).build();
    static final Domain<Color> NOT_RED    = DomainObjectSet.<Color>builder().value(Color.GREEN).value(Color.BLUE).build();
    static final Domain<Color> ALL_COLORS = EnumDomain.allOf(Color.class);

    @Test
    void propagate_eq_upperLimitReached_excludesValueFromPossibles() {
        // count(RED)==1, a={RED}, b={R,G,B}, c={R,G,B} → definite=1==n → remove RED from b,c
        var constraint = CountConstraint.of(Set.of(a, b, c), Color.RED, Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, ALL_COLORS, c, ALL_COLORS);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(NOT_RED);
        assertThat(result.get().get(c)).isEqualTo(NOT_RED);
    }

    @Test
    void propagate_eq_lowerLimitBarelyMet_forcesValueIntoPossibles() {
        // count(RED)==2, a={RED}, b={R,G,B}, c={G,B} → definite=1, possible=[b], maxCount=2==n → force b=RED
        var constraint = CountConstraint.of(Set.of(a, b, c), Color.RED, Operator.EQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, ALL_COLORS, c, NOT_RED);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(RED_ONLY);
    }

    @Test
    void propagate_eq_infeasible_tooManyDefinites() {
        // count(RED)==1, a={RED}, b={RED} → definiteCount=2 > n=1 → infeasible
        var constraint = CountConstraint.of(Set.of(a, b), Color.RED, Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, RED_ONLY);
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_tooFewPossible() {
        // count(RED)==3, a={G,B}, b={G,B}, c={G,B} → maxCount=0 < n=3 → infeasible
        var constraint = CountConstraint.of(Set.of(a, b, c), Color.RED, Operator.EQ, 3);
        var domains = Map.<Variable<?>, Domain<?>>of(a, NOT_RED, b, NOT_RED, c, NOT_RED);
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_upperLimitReached_excludesValueFromPossibles() {
        // count(RED)<=1, a={RED}, b={R,G,B} → definite=1==n → remove RED from b
        var constraint = CountConstraint.of(Set.of(a, b), Color.RED, Operator.LEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, ALL_COLORS);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(NOT_RED);
    }

    @Test
    void propagate_leq_infeasible() {
        // count(RED)<=1, a={RED}, b={RED} → definiteCount=2 > n=1 → infeasible
        var constraint = CountConstraint.of(Set.of(a, b), Color.RED, Operator.LEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, RED_ONLY);
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_forcesValueIntoPossibles() {
        // count(RED)>=2, a={RED}, b={R,G,B}, c={G,B} → definite=1, possible=[b], maxCount=2==n → force b=RED
        var constraint = CountConstraint.of(Set.of(a, b, c), Color.RED, Operator.GEQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, ALL_COLORS, c, NOT_RED);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(b)).isEqualTo(RED_ONLY);
    }

    @Test
    void propagate_geq_infeasible() {
        // count(RED)>=2, a={G,B}, b={G,B} → maxCount=0 < n=2 → infeasible
        var constraint = CountConstraint.of(Set.of(a, b), Color.RED, Operator.GEQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(a, NOT_RED, b, NOT_RED);
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var constraint = CountConstraint.of(Set.of(a, b), Color.RED, Operator.NEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(a, ALL_COLORS, b, ALL_COLORS);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // count(RED)==2, a={RED}, b={RED}, c={G,B} → definite=2==n but no possibles → no updates
        var constraint = CountConstraint.of(Set.of(a, b, c), Color.RED, Operator.EQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(a, RED_ONLY, b, RED_ONLY, c, NOT_RED);
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void solver_infeasibleCountConstraint_returnsNoSolutions() {
        // count(RED)==3 but only 2 variables → infeasible detected by propagation
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, EnumDomain.allOf(Color.class))
                .variableDomain(b, EnumDomain.allOf(Color.class))
                .countConstraint(Set.of(a, b), Color.RED, Operator.EQ, 3)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).isEmpty();
    }

    @Test
    void solver_countConstraint_correctSolutionCount() {
        // 4 variables over {RED, GREEN, BLUE}, exactly 2 must be RED.
        // Solutions: C(4,2) positions for RED × 2^2 choices for remaining = 6 × 4 = 24.
        Variable.Factory F = Variable.Factory.INSTANCE;
        Variable<Color> x1 = F.create("x1");
        Variable<Color> x2 = F.create("x2");
        Variable<Color> x3 = F.create("x3");
        Variable<Color> x4 = F.create("x4");
        var domain = EnumDomain.allOf(Color.class);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain)
                .variableDomain(x3, domain).variableDomain(x4, domain)
                .countConstraint(Set.of(x1, x2, x3, x4), Color.RED, Operator.EQ, 2)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(24);
    }
}
