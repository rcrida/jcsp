package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.rcrida.jcsp.domains.IntervalDomain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NaryElementConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> INDEX = F.create("index");
    static final Variable<String> RESULT = F.create("result");
    static final Variable<String> A = F.create("a");
    static final Variable<String> B = F.create("b");
    static final Variable<String> C = F.create("c");
    static final List<Variable<String>> VARS = List.of(A, B, C);

    NaryElementConstraint<String> constraint;

    @BeforeEach
    void setUp() {
        constraint = NaryElementConstraint.of(INDEX, RESULT, VARS);
    }

    @Test
    void allAssigned_matching_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 1, RESULT, "alpha", A, "alpha", B, "beta", C, "gamma")))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 2, RESULT, "beta", A, "alpha", B, "beta", C, "gamma")))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 3, RESULT, "gamma", A, "alpha", B, "beta", C, "gamma")))).isTrue();
    }

    @Test
    void allAssigned_notMatching_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 1, RESULT, "beta", A, "alpha", B, "beta", C, "gamma")))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 2, RESULT, "gamma", A, "alpha", B, "beta", C, "gamma")))).isFalse();
    }

    @Test
    void outOfBoundsIndex_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 0, RESULT, "alpha", A, "alpha", B, "beta", C, "gamma")))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 4, RESULT, "alpha", A, "alpha", B, "beta", C, "gamma")))).isFalse();
    }

    @Test
    void indexUnassigned_optimisticallyTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(RESULT, "alpha", A, "alpha", B, "beta", C, "gamma")))).isTrue();
    }

    @Test
    void resultUnassigned_optimisticallyTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 1, A, "alpha", B, "beta", C, "gamma")))).isTrue();
    }

    @Test
    void selectedVarUnassigned_optimisticallyTrue() {
        // INDEX=2 selects B, but B is not assigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 2, RESULT, "beta", A, "alpha", C, "gamma")))).isTrue();
    }

    @Test
    void propagate_prunesIndexWhenNoOverlapWithResult() {
        // A domain = {x}, result domain = {y} — index=1 has no support, should be pruned
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DomainObjectSet.<String>builder().value("beta").value("gamma").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // index=1 pruned (A={alpha} has no overlap with result={beta,gamma})
        assertThat(result.get()).containsKey(INDEX);
        var newIndex = (io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(INDEX);
        assertThat(newIndex.toList()).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void propagate_prunesResultToUnionOfLiveVarDomains() {
        // index can be 1 or 2; A={alpha}, B={beta}; result initially has extra value "gamma"
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 2),
                RESULT, DomainObjectSet.<String>builder().value("alpha").value("beta").value("gamma").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // result pruned to {alpha, beta} (union of A and B domains for live indices 1 and 2)
        assertThat(result.get()).containsKey(RESULT);
        var newResult = (io.github.rcrida.jcsp.domains.DiscreteDomain<String>) result.get().get(RESULT);
        assertThat(newResult.toList()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void propagate_prunesSelectedVarWhenIndexSingleton() {
        // index={2} singleton; B has extra values not in result; result={beta}
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DomainObjectSet.<Integer>builder().value(2).build(),
                RESULT, DomainObjectSet.<String>builder().value("beta").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").value("extra").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // B should be pruned to {beta}
        assertThat(result.get()).containsKey(B);
        var newB = (io.github.rcrida.jcsp.domains.DiscreteDomain<String>) result.get().get(B);
        assertThat(newB.toList()).containsExactly("beta");
    }

    @Test
    void propagate_infeasibleWhenNoLiveIndices() {
        // result={x}, all var domains have no overlap with {x}
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DomainObjectSet.<String>builder().value("x").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasibleWhenSelectedVarBecomesEmpty() {
        // index={1} singleton; A={alpha}; result={beta} — A ∩ result = ∅
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DomainObjectSet.<Integer>builder().value(1).build(),
                RESULT, DomainObjectSet.<String>builder().value("beta").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void solver_elementOverVariables_correctSolutions() {
        // A={10}, B={20}, C={30}; index ∈ {1,2,3}; result ∈ {10,20,30}
        // Each index maps uniquely: 3 solutions (one per index value)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(INDEX, IntRangeDomain.of(1, 3))
                .variableDomain(RESULT, DomainObjectSet.<String>builder().value("10").value("20").value("30").build())
                .variableDomain(A, DomainObjectSet.<String>builder().value("10").build())
                .variableDomain(B, DomainObjectSet.<String>builder().value("20").build())
                .variableDomain(C, DomainObjectSet.<String>builder().value("30").build())
                .elementVariableConstraint(INDEX, RESULT, VARS)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(3);
    }

    @Test
    void solver_elementOverVariables_withSharedDomains() {
        // A={1,2}, B={3,4}; index ∈ {1,2}; result ∈ {1,3}
        // index=1: result=A ∈ {1,2}∩{1,3}={1}, so A=1, B free → 2 solutions (B=3 or B=4)
        // index=2: result=B ∈ {3,4}∩{1,3}={3}, so B=3, A free → 2 solutions (A=1 or A=2)
        // Total: 4 solutions
        Variable<Integer> varA = F.create("varA");
        Variable<Integer> varB = F.create("varB");
        Variable<Integer> idx = F.create("idx");
        Variable<Integer> res = F.create("res");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(idx, IntRangeDomain.of(1, 2))
                .variableDomain(res, DomainObjectSet.<Integer>builder().value(1).value(3).build())
                .variableDomain(varA, IntRangeDomain.of(1, 2))
                .variableDomain(varB, IntRangeDomain.of(3, 4))
                .elementVariableConstraint(idx, res, List.of(varA, varB))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(4);
    }

    @Test
    void propagate_noOpWhenIndexIsBoundedDomain() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntervalDomain.of(1.0, 3.0),
                RESULT, DomainObjectSet.<String>builder().value("alpha").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_noOpWhenResultIsBoundedDomain() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, IntervalDomain.of(0.0, 10.0),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_noOpWhenVarIsBoundedDomain() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DomainObjectSet.<String>builder().value("alpha").build(),
                A, IntervalDomain.of(0.0, 1.0),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_prunesOutOfBoundsIndices() {
        // index ∈ {0,1,2,3,4,5} but only 3 vars — 0 (i<1) and 4,5 (i>size) are out of bounds
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DomainObjectSet.<Integer>builder().value(0).value(1).value(2).value(3).value(4).value(5).build(),
                RESULT, DomainObjectSet.<String>builder().value("alpha").value("beta").value("gamma").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(INDEX);
        var newIndex = (io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(INDEX);
        assertThat(newIndex.toList()).containsExactlyInAnyOrder(1, 2, 3);
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_allSingleton_returnsFullReason() {
        // Same setup as propagate_infeasibleWhenNoLiveIndices: all three in-bounds candidates
        // excluded for lack of overlap with result, and every cited variable is singleton.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DomainObjectSet.<String>builder().value("x").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains))
                .isEqualTo(Map.of(A, "alpha", B, "beta", C, "gamma", RESULT, "x"));
    }

    @Test
    void explainInfeasible_notAllSingleton_returnsEmpty() {
        // A is not singleton (still no overlap with result), so no reason is sound.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DomainObjectSet.<String>builder().value("x").build(),
                A, DomainObjectSet.<String>builder().value("alpha").value("delta").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_allOutOfBounds_returnsEmpty() {
        // Every candidate is out of bounds — nothing to cite, no variable is ever consulted.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DomainObjectSet.<Integer>builder().value(0).value(4).build(),
                RESULT, DomainObjectSet.<String>builder().value("alpha").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_mixedBoundsAndSupport_citesOnlyInBoundsVars() {
        // 0 and 4 are out of bounds (uncited); 1 and 2 are in-bounds but unsupported (cited via
        // A and B); index 3 (C) never appears in the domain at all, so C must not be cited.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DomainObjectSet.<Integer>builder().value(0).value(1).value(2).value(4).build(),
                RESULT, DomainObjectSet.<String>builder().value("x").build(),
                A, DomainObjectSet.<String>builder().value("alpha").build(),
                B, DomainObjectSet.<String>builder().value("beta").build(),
                C, DomainObjectSet.<String>builder().value("gamma").build()
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains))
                .isEqualTo(Map.of(A, "alpha", B, "beta", RESULT, "x"));
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(a, b, c, index, result), result = [a, b, c][index]>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(NaryElementConstraint.of(INDEX, RESULT, VARS)).isEqualTo(constraint);
    }
}
