package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.rcrida.jcsp.domains.IntervalDomain;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
                RESULT, DiscreteDomain.of("beta", "gamma"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // index=1 pruned (A={alpha} has no overlap with result={beta,gamma})
        assertThat(result.get()).containsKey(INDEX);
        var newIndex = (DiscreteDomain<Integer>) result.get().get(INDEX);
        assertThat(newIndex.toList()).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void propagate_prunesResultToUnionOfLiveVarDomains() {
        // index can be 1 or 2; A={alpha}, B={beta}; result initially has extra value "gamma"
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 2),
                RESULT, DiscreteDomain.of("alpha", "beta", "gamma"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // result pruned to {alpha, beta} (union of A and B domains for live indices 1 and 2)
        assertThat(result.get()).containsKey(RESULT);
        var newResult = (DiscreteDomain<String>) result.get().get(RESULT);
        assertThat(newResult.toList()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void propagate_prunesSelectedVarWhenIndexSingleton() {
        // index={2} singleton; B has extra values not in result; result={beta}
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DiscreteDomain.of(2),
                RESULT, DiscreteDomain.of("beta"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta", "extra"),
                C, DiscreteDomain.of("gamma")
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        // B should be pruned to {beta}
        assertThat(result.get()).containsKey(B);
        var newB = (DiscreteDomain<String>) result.get().get(B);
        assertThat(newB.toList()).containsExactly("beta");
    }

    @Test
    void propagate_infeasibleWhenNoLiveIndices() {
        // result={x}, all var domains have no overlap with {x}
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DiscreteDomain.of("x"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasibleWhenSelectedVarBecomesEmpty() {
        // index={1} singleton; A={alpha}; result={beta} — A ∩ result = ∅
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DiscreteDomain.of(1),
                RESULT, DiscreteDomain.of("beta"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void solver_elementOverVariables_correctSolutions() {
        // A={10}, B={20}, C={30}; index ∈ {1,2,3}; result ∈ {10,20,30}
        // Each index maps uniquely: 3 solutions (one per index value)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(INDEX, IntRangeDomain.of(1, 3))
                .variableDomain(RESULT, DiscreteDomain.of("10", "20", "30"))
                .variableDomain(A, DiscreteDomain.of("10"))
                .variableDomain(B, DiscreteDomain.of("20"))
                .variableDomain(C, DiscreteDomain.of("30"))
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
                .variableDomain(res, DiscreteDomain.of(1, 3))
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
                RESULT, DiscreteDomain.of("alpha"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_noOpWhenResultIsBoundedDomain() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, IntervalDomain.of(0.0, 10.0),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_noOpWhenVarIsBoundedDomain() {
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DiscreteDomain.of("alpha"),
                A, IntervalDomain.of(0.0, 1.0),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isPresent().hasValueSatisfying(m -> assertThat(m).isEmpty());
    }

    @Test
    void propagate_prunesOutOfBoundsIndices() {
        // index ∈ {0,1,2,3,4,5} but only 3 vars — 0 (i<1) and 4,5 (i>size) are out of bounds
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DiscreteDomain.of(0, 1, 2, 3, 4, 5),
                RESULT, DiscreteDomain.of("alpha", "beta", "gamma"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(INDEX);
        var newIndex = (DiscreteDomain<Integer>) result.get().get(INDEX);
        assertThat(newIndex.toList()).containsExactlyInAnyOrder(1, 2, 3);
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_allSingleton_citesIndexTooAsValueSet() {
        // Same setup as propagate_infeasibleWhenNoLiveIndices: all three in-bounds candidates
        // excluded for lack of overlap with result. index itself is now also cited (its current
        // 3-value candidate set), so even though every OTHER cited variable is singleton, index
        // isn't, so the tighter GroundNogoodConstraint tier doesn't apply -- ValueSetNogoodConstraint
        // does.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DiscreteDomain.of("x"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(ValueSetNogoodConstraint.of(Map.of(
                A, Set.of("alpha"), B, Set.of("beta"), C, Set.of("gamma"), RESULT, Set.of("x"), INDEX, Set.of(1, 2, 3))));
    }

    @Test
    void explainInfeasible_notAllSingleton_stillCitesExactValueSets() {
        // A is not singleton -- an earlier version of this method declined to explain here at all,
        // losing propagation strength. ValueSetNogoodConstraint#fromCurrentState instead cites A's
        // exact current 2-value set, staying sound without requiring singletons.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3),
                RESULT, DiscreteDomain.of("x"),
                A, DiscreteDomain.of("alpha", "delta"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(ValueSetNogoodConstraint.of(Map.of(
                A, Set.of("alpha", "delta"), B, Set.of("beta"), C, Set.of("gamma"),
                RESULT, Set.of("x"), INDEX, Set.of(1, 2, 3))));
    }

    @Test
    void explainInfeasible_allOutOfBounds_returnsEmpty() {
        // Every candidate is out of bounds — nothing to cite, no variable is ever consulted (index
        // and result are never added to the citation set at all in this case).
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DiscreteDomain.of(0, 4),
                RESULT, DiscreteDomain.of("alpha"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_mixedBoundsAndSupport_citesOnlyInBoundsVarsPlusIndexAndResult() {
        // 0 and 4 are out of bounds (uncited); 1 and 2 are in-bounds but unsupported (cited via
        // A and B); index 3 (C) never appears in the domain at all, so C must not be cited. index
        // and result are now cited too.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, DiscreteDomain.of(0, 1, 2, 4),
                RESULT, DiscreteDomain.of("x"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("beta"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        assertThat(constraint.explainInfeasible(domains)).contains(ValueSetNogoodConstraint.of(Map.of(
                A, Set.of("alpha"), B, Set.of("beta"), RESULT, Set.of("x"), INDEX, Set.of(0, 1, 2, 4))));
    }

    @Test
    void explainInfeasible_indexNarrowedByAnotherConstraint_doesNotForbidEscapeViaExcludedIndexValue() {
        // Regression test for a real unsoundness bug (found via QuasiGroup-7-09.xml.lzma
        // intermittently reporting a false UNSATISFIABLE under CDCL search): index's domain here is
        // {1, 3} -- as if some OTHER constraint (e.g. an AllDiffConstraint) already excluded 2 --
        // and pass 1 empties it because neither in-bounds candidate (A via 1, C via 3) overlaps
        // result. But index=2 (B) WOULD have provided support in the full, un-narrowed problem. An
        // earlier version of this method omitted index from its citation entirely, producing a
        // nogood that wrongly forbade every valid solution using index=2 too. The fixed citation
        // includes index's own current value set {1, 3}, so a solution using index=2 (outside that
        // set) correctly escapes it.
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                INDEX, IntRangeDomain.of(1, 3).toBuilder().delete(2).build(),
                RESULT, DiscreteDomain.of("zzz"),
                A, DiscreteDomain.of("alpha"),
                B, DiscreteDomain.of("zzz"),
                C, DiscreteDomain.of("gamma")
        );
        assertThat(constraint.propagate(domains)).isEmpty();
        var reason = constraint.explainInfeasible(domains);
        assertThat(reason).isPresent();
        var validSolutionUsingExcludedIndex = Assignment.of(
                Map.of(INDEX, 2, RESULT, "zzz", A, "alpha", B, "zzz", C, "gamma"));
        assertThat(reason.get().isSatisfiedBy(validSolutionUsingExcludedIndex)).isTrue();
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
