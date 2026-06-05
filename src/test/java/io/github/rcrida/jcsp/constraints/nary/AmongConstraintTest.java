package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AmongConstraintTest {
    enum Color { RED, GREEN, BLUE }

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Color> v1 = F.create("v1");
    static final Variable<Color> v2 = F.create("v2");
    static final Variable<Color> v3 = F.create("v3");

    // S = {RED, GREEN}
    static final Set<Color> S = Set.of(Color.RED, Color.GREEN);

    static final Domain<Color> IN_S     = DomainObjectSet.<Color>builder().value(Color.RED).value(Color.GREEN).build();
    static final Domain<Color> MIXED    = EnumDomain.allOf(Color.class); // RED, GREEN, BLUE
    static final Domain<Color> OUT_S    = DomainObjectSet.<Color>builder().value(Color.BLUE).build();

    @Test
    void countInS_satisfied() {
        // among({RED,GREEN})==2: v1=RED, v2=GREEN, v3=BLUE → 2 in S ✓
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.EQ, 2);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.BLUE)))).isTrue();
    }

    @Test
    void countInS_notSatisfied() {
        // among({RED,GREEN})==2: v1=RED, v2=BLUE, v3=BLUE → 1 in S ✗
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.EQ, 2);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.BLUE, v3, Color.BLUE)))).isFalse();
    }

    @Test
    void leq_satisfied() {
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.LEQ, 1);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.BLUE)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN)))).isFalse();
    }

    @Test
    void geq_satisfied() {
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.GEQ, 1);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.BLUE)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.BLUE, v2, Color.BLUE)))).isFalse();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.EQ, 2);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
    }

    @Test
    void testToString() {
        var c = AmongConstraint.of(Set.of(v1), S, Operator.EQ, 1);
        assertThat(c.toString()).isEqualTo("<(v1), among(GREEN, RED) == 1>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        var c1 = AmongConstraint.of(Set.of(v1, v2), S, Operator.EQ, 1);
        var c2 = AmongConstraint.of(Set.of(v1, v2), S, Operator.EQ, 1);
        assertThat(c1).isEqualTo(c2);
    }

    // --- propagate() ---

    @Test
    void propagate_eq_upperLimitReached_removesS_fromPossibles() {
        // among(S)==1: v1=definite(IN_S), v2=possible(MIXED) → definite=1==n → remove S from v2
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, MIXED, v3, OUT_S);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(OUT_S);
    }

    @Test
    void propagate_eq_lowerLimitBarelyMet_forcesS_intoPossibles() {
        // among(S)==2: v1=definite, v2=possible, v3=impossible → definite=1, maxCount=2==n → force v2 to S
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.EQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, MIXED, v3, OUT_S);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(IN_S);
    }

    @Test
    void propagate_eq_infeasible_tooManyDefinites() {
        // among(S)==1: v1=definite, v2=definite → definiteCount=2 > n=1 → infeasible
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, IN_S);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_tooFewPossible() {
        // among(S)==2: v1=impossible, v2=impossible → maxCount=0 < n=2 → infeasible
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.EQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, OUT_S, v2, OUT_S);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_upperLimitReached_removesS_fromPossibles() {
        // among(S)<=1: v1=definite, v2=possible → remove S from v2
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.LEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, MIXED);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(OUT_S);
    }

    @Test
    void propagate_leq_infeasible() {
        // among(S)<=1: v1=definite, v2=definite → definiteCount=2 > n=1 → infeasible
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.LEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, IN_S);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_forcesS_intoPossibles() {
        // among(S)>=2: v1=definite, v2=possible, v3=impossible → maxCount=2==n → force v2 to S
        var c = AmongConstraint.of(Set.of(v1, v2, v3), S, Operator.GEQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, MIXED, v3, OUT_S);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(IN_S);
    }

    @Test
    void propagate_geq_infeasible() {
        // among(S)>=2: all impossible → maxCount=0 < n=2 → infeasible
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.GEQ, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, OUT_S, v2, OUT_S);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.NEQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, MIXED, v2, MIXED);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // among(S)==1: v1=definite, v2=impossible → definite=1==n but no possibles → no updates
        var c = AmongConstraint.of(Set.of(v1, v2), S, Operator.EQ, 1);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IN_S, v2, OUT_S);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void solver_infeasibleAmongConstraint_returnsNoSolutions() {
        // among({RED,GREEN})==3 but only 2 variables → infeasible
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, EnumDomain.allOf(Color.class))
                .variableDomain(v2, EnumDomain.allOf(Color.class))
                .amongConstraint(Set.of(v1, v2), S, Operator.EQ, 3)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).isEmpty();
    }

    @Test
    void solver_exactlyOneInS_correctSolutionCount() {
        // among({RED,GREEN})==1 over {v1,v2,v3}, each in {RED,GREEN,BLUE}
        // Exactly 1 in S: C(3,1) positions × 2 S-values × 2^2 non-S choices = 3×2×1 = 6... let me recalculate
        // 3 ways to pick which var is in S × 2 values in S × 1 value not in S (BLUE) × 1 = 3×2×1×1 = 6
        // Wait: the other 2 vars must be BLUE (only non-S value). So: 3 × 2 = 6 solutions.
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, EnumDomain.allOf(Color.class))
                .variableDomain(v2, EnumDomain.allOf(Color.class))
                .variableDomain(v3, EnumDomain.allOf(Color.class))
                .amongConstraint(Set.of(v1, v2, v3), S, Operator.EQ, 1)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp).toList();
        assertThat(solutions).hasSize(6);
        assertThat(solutions).allSatisfy(sol -> {
            long inS = java.util.stream.Stream.of(v1, v2, v3)
                    .map(v -> sol.getValue(v).orElseThrow())
                    .filter(S::contains).count();
            assertThat(inS).isEqualTo(1);
        });
    }
}
