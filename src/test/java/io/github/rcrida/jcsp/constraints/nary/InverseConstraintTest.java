package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InverseConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // f = [f0, f1, f2], invf = [g0, g1, g2], values in {1, 2, 3}
    static final Variable<Integer> f0 = F.create("f0");
    static final Variable<Integer> f1 = F.create("f1");
    static final Variable<Integer> f2 = F.create("f2");
    static final Variable<Integer> g0 = F.create("g0");
    static final Variable<Integer> g1 = F.create("g1");
    static final Variable<Integer> g2 = F.create("g2");

    static final List<Variable<Integer>> fVars    = List.of(f0, f1, f2);
    static final List<Variable<Integer>> invfVars = List.of(g0, g1, g2);
    static final InverseConstraint constraint = InverseConstraint.of(fVars, invfVars);

    static Domain<Integer> domain123() { return IntRangeDomain.of(1, 3); }

    @Test
    void satisfiedByValidPermutation() {
        // f = [2,3,1] → invf = [3,1,2]: f[0]=2→invf[1]=1, f[1]=3→invf[2]=2, f[2]=1→invf[0]=3
        var a = Assignment.builder()
                .value(f0, 2).value(f1, 3).value(f2, 1)
                .value(g0, 3).value(g1, 1).value(g2, 2).build();
        assertThat(constraint.isSatisfiedBy(a)).isTrue();
    }

    @Test
    void notSatisfiedByInconsistentInverse() {
        // f = [1,2,3] but invf = [2,1,3] (wrong: invf[0] should be 1 not 2)
        var a = Assignment.builder()
                .value(f0, 1).value(f1, 2).value(f2, 3)
                .value(g0, 2).value(g1, 1).value(g2, 3).build();
        assertThat(constraint.isSatisfiedBy(a)).isFalse();
    }

    @Test
    void optimisticallyTrueForPartialAssignment() {
        assertThat(constraint.isSatisfiedBy(Assignment.builder().value(f0, 1).build())).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.builder().build())).isTrue();
    }

    @Test
    void propagate_f_singleton_prunes_invf_for_unavailable_positions() {
        // f0={1}: value 1 can only be reached by f[0], not f[1] or f[2].
        // invf[1] (j=1): value 1 would mean f[0]=2 — impossible since f[0]={1}. Remove 1.
        // invf[2] (j=2): value 1 would mean f[0]=3 — impossible. Remove 1.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(1).build(),
                f1, domain123(), f2, domain123(),
                g0, domain123(), g1, domain123(), g2, domain123());
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(g1)).isEqualTo(DomainObjectSet.<Integer>builder().value(2).value(3).build());
        assertThat(result.get().get(g2)).isEqualTo(DomainObjectSet.<Integer>builder().value(2).value(3).build());
    }

    @Test
    void propagate_removes_value_from_invf_when_absent_from_f() {
        // f0 excludes 2: invf[1] can't be 1 (that would mean f[0]=2, impossible)
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(1).value(3).build(),
                f1, domain123(), f2, domain123(),
                g0, domain123(), g1, domain123(), g2, domain123());
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(g1)).isEqualTo(DomainObjectSet.<Integer>builder().value(2).value(3).build());
    }

    @Test
    void propagate_invf_singleton_prunes_f_for_other_positions() {
        // g0={2}: invf[0]=2 means f[1]=1. Position 0 (f[0]) and 2 (f[2]) cannot map to j+1=1
        // f[0] domain: remove j=1 because 1 ∉ dom(g0={2}) → f0 becomes {2,3}
        // f[2] domain: remove j=1 because 3 ∉ dom(g0={2}) → f2 becomes {2,3}
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, domain123(), f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).build(),
                g1, domain123(), g2, domain123());
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(f0)).isEqualTo(DomainObjectSet.<Integer>builder().value(2).value(3).build());
        assertThat(result.get().get(f2)).isEqualTo(DomainObjectSet.<Integer>builder().value(2).value(3).build());
    }

    @Test
    void propagate_infeasible_emptyDomain() {
        // f0={1}, g0 forced empty: f[0]=1 requires invf[0]=1, but g0={2,3} → g0 would become empty
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(1).build(),
                f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void of_rejectsUnequalLengthArrays() {
        assertThatThrownBy(() -> InverseConstraint.of(List.of(f0), List.of(g0, g1)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void propagate_pass1_multipleRemovalsFromSameInvf() {
        // f[0]={2,3}, f[1]={2,3}, f[2]={1,2,3}: invf[0] values 1 and 2 both require f's to contain 1,
        // which neither f[0] nor f[1] has → both removed, hitting the builder non-null branch.
        // invf[0] prunes from {1,2,3} to {3}.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f1, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f2, domain123(),
                g0, domain123(), g1, domain123(), g2, domain123());
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(g0)).isEqualTo(DomainObjectSet.<Integer>builder().value(3).build());
    }

    @Test
    void propagate_pass1_infeasible_invfBecomesEmpty() {
        // f[0]={2,3}, f[1]={2,3}, f[2]={2,3}: no f can be 1, so invf[0]={1,2} exhausted → infeasible.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f1, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f2, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g0, DomainObjectSet.<Integer>builder().value(1).value(2).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_pass2_multipleRemovalsFromSameF() {
        // g0={2,3} (no 1), g1={2,3} (no 1): for f[0] (i=0), both j=1 and j=2 require 1∈dom(invf[j-1]),
        // which neither g0 nor g1 has → both removed from f[0], hitting the builder non-null branch.
        // f[0] prunes from {1,2,3} to {3}.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, domain123(), f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g1, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g2, domain123());
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(f0)).isEqualTo(DomainObjectSet.<Integer>builder().value(3).build());
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_pass1_allSingleton_returnsFullReason() {
        // g0={1}: only val=1 is a candidate, requiring f[0] to contain 1. f0={2} excludes it,
        // so g0's only value is removed → g0 empty. f0 is singleton, so the reason is sound.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(2).build(),
                f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(1).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(f0, 2)));
    }

    @Test
    void explainInfeasible_pass1_notAllSingleton_returnsEmpty() {
        // Same setup as propagate_pass1_infeasible_invfBecomesEmpty: f0 and f1 (the culprits
        // excluding invf[0]'s two candidate values) are not singleton, so no reason is sound.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f1, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                f2, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g0, DomainObjectSet.<Integer>builder().value(1).value(2).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_pass2_allSingleton_returnsFullReason() {
        // f0={1}: pass 1 leaves g0 untouched (no support issue), but pass 2 finds f0's only
        // value (1) unsupported by g0={2} → f0 emptied. g0 is singleton, so the reason is sound.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(1).build(),
                f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(g0, 2)));
    }

    @Test
    void explainInfeasible_pass2_notAllSingleton_returnsEmpty() {
        // Same setup as propagate_infeasible_emptyDomain: g0 (the culprit excluding f0's only
        // value) is not singleton, so no reason is sound.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, DomainObjectSet.<Integer>builder().value(1).build(),
                f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g1, domain123(), g2, domain123());
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        // Same setup as propagate_pass2_multipleRemovalsFromSameF: propagation prunes domains
        // (in both passes) without ever emptying one, so explainInfeasible finds nothing to report.
        var domains = Map.<Variable<?>, Domain<?>>of(
                f0, domain123(), f1, domain123(), f2, domain123(),
                g0, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g1, DomainObjectSet.<Integer>builder().value(2).value(3).build(),
                g2, domain123());
        assertThat(constraint.propagate(domains)).isPresent();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(f0, f1, f2, g0, g1, g2), inverse(f=[f0, f1, f2], invf=[g0, g1, g2])>");
    }

    @Test
    void of_createsConstraintWithAllVariables() {
        assertThat(constraint.getVariables()).containsExactlyInAnyOrder(f0, f1, f2, g0, g1, g2);
    }

    @Test
    void solver_findsInversePermutation() {
        // f and invf over {1,2,3}: exactly the 6 valid inverse-permutation pairs
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(f0, domain123()).variableDomain(f1, domain123()).variableDomain(f2, domain123())
                .variableDomain(g0, domain123()).variableDomain(g1, domain123()).variableDomain(g2, domain123())
                .inverseConstraint(fVars, invfVars)
                .allDiffConstraint(java.util.Set.of(f0, f1, f2))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(6); // 3! permutations, each with a unique inverse
        assertThat(solutions).allSatisfy(sol -> {
            int fi0 = sol.getValue(f0).orElseThrow();
            int fi1 = sol.getValue(f1).orElseThrow();
            int fi2 = sol.getValue(f2).orElseThrow();
            assertThat(sol.getValue(invfVars.get(fi0 - 1))).hasValue(1);
            assertThat(sol.getValue(invfVars.get(fi1 - 1))).hasValue(2);
            assertThat(sol.getValue(invfVars.get(fi2 - 1))).hasValue(3);
        });
    }

    @Test
    void solver_infeasible_detectsViaInverseConsistency() {
        // f0 forced to 1 but g0 forced to 2 → f[0]=1 requires invf[0]=1, contradiction
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(f0, DomainObjectSet.<Integer>builder().value(1).build())
                .variableDomain(f1, domain123()).variableDomain(f2, domain123())
                .variableDomain(g0, DomainObjectSet.<Integer>builder().value(2).build())
                .variableDomain(g1, domain123()).variableDomain(g2, domain123())
                .inverseConstraint(fVars, invfVars)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).isEmpty();
    }
}
