package io.github.rcrida.jcsp.solver.tree.decomposition;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TreeDecompositionSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // Original problem: 5 variables, domain 1-10 → search space = 10^5 = 100,000
    // With targetTreewidth=7: maxDomainSize = min(10^7, 1_000_000) = 1,000,000
    static final Variable<Integer> V1 = F.create("v1");
    static final Variable<Integer> V2 = F.create("v2");
    static final Variable<Integer> V3 = F.create("v3");
    static final Variable<Integer> V4 = F.create("v4");
    static final Variable<Integer> V5 = F.create("v5");
    static final ConstraintSatisfactionProblem ORIGINAL_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(V1, IntRangeDomain.of(1, 10))
            .variableDomain(V2, IntRangeDomain.of(1, 10))
            .variableDomain(V3, IntRangeDomain.of(1, 10))
            .variableDomain(V4, IntRangeDomain.of(1, 10))
            .variableDomain(V5, IntRangeDomain.of(1, 10))
            .build();

    // Tree CSP: 3 clique variables, domain size 5 → k=3, w=5, k×w²=75 < 100,000
    static final Variable<Integer> C1 = F.create("c1");
    static final Variable<Integer> C2 = F.create("c2");
    static final Variable<Integer> C3 = F.create("c3");
    static final ConstraintSatisfactionProblem TREE_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(C1, IntRangeDomain.of(1, 5))
            .variableDomain(C2, IntRangeDomain.of(1, 5))
            .variableDomain(C3, IntRangeDomain.of(1, 5))
            .build();

    // Small original: 3 variables, domain 1-3 → search space = 27
    static final Variable<Integer> S1 = F.create("s1");
    static final Variable<Integer> S2 = F.create("s2");
    static final Variable<Integer> S3 = F.create("s3");
    static final ConstraintSatisfactionProblem SMALL_ORIGINAL_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(S1, IntRangeDomain.of(1, 3))
            .variableDomain(S2, IntRangeDomain.of(1, 3))
            .variableDomain(S3, IntRangeDomain.of(1, 3))
            .build();

    // Expensive tree CSP: 1 clique variable, domain size 28 → k=1, w=28, k×w²=784 > 27
    static final Variable<Integer> BIG_CLIQUE = F.create("bigClique");
    static final ConstraintSatisfactionProblem EXPENSIVE_TREE_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(BIG_CLIQUE, IntRangeDomain.of(1, 28))
            .build();

    @Mock TreeDecomposer treeDecomposer;
    @Mock Solver treeSolver;
    @Mock Solver defaultSolver;
    TreeDecompositionSolver solver;

    @BeforeEach
    void setUp() {
        solver = TreeDecompositionSolver.builder().inner(defaultSolver).treeDecomposer(treeDecomposer).treeSolver(treeSolver).targetTreewidth(7).build();
    }

    @Test
    void getSolutions_decompositionApplied() {
        val originalAssignment = Assignment.of(Map.of(V1, 1, V2, 2));
        val treeSolution = Assignment.builder().value(C1, originalAssignment).build();
        when(treeDecomposer.decompose(eq(ORIGINAL_CSP), anyInt())).thenReturn(Optional.of(TREE_CSP));
        when(treeSolver.getSolutions(TREE_CSP)).thenReturn(Stream.of(treeSolution));

        assertThat(solver.getSolutions(ORIGINAL_CSP)).containsExactly(originalAssignment);
    }

    @Test
    void getSolutions_decompositionSkippedDueToComplexity() {
        val fallbackAssignment = Assignment.of(Map.of(S1, 1));
        when(treeDecomposer.decompose(eq(SMALL_ORIGINAL_CSP), anyInt())).thenReturn(Optional.of(EXPENSIVE_TREE_CSP));
        when(defaultSolver.getSolutions(SMALL_ORIGINAL_CSP)).thenReturn(Stream.of(fallbackAssignment));

        assertThat(solver.getSolutions(SMALL_ORIGINAL_CSP)).containsExactly(fallbackAssignment);
    }

    @Test
    void getSolutions_noDecomposition() {
        val fallbackAssignment = Assignment.of(Map.of(V1, 1));
        when(treeDecomposer.decompose(eq(ORIGINAL_CSP), anyInt())).thenReturn(Optional.empty());
        when(defaultSolver.getSolutions(ORIGINAL_CSP)).thenReturn(Stream.of(fallbackAssignment));

        assertThat(solver.getSolutions(ORIGINAL_CSP)).containsExactly(fallbackAssignment);
    }

    @Test
    void maxDomainSizeScalesWithDomainSize() {
        // d=2, tw=7: min(2^7, 1_000_000) = 128
        // d=3, tw=7: min(3^7, 1_000_000) = 2187
        // d=10, tw=7: min(10^7, 1_000_000) = 1_000_000 (capped)
        val binaryDomainCsp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 2))
                .build();
        val ternaryDomainCsp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 3))
                .build();
        val largeDomainCsp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 10))
                .build();
        when(treeDecomposer.decompose(eq(binaryDomainCsp), eq(128))).thenReturn(Optional.empty());
        when(treeDecomposer.decompose(eq(ternaryDomainCsp), eq(2187))).thenReturn(Optional.empty());
        when(treeDecomposer.decompose(eq(largeDomainCsp), eq(1_000_000))).thenReturn(Optional.empty());
        when(defaultSolver.getSolutions(binaryDomainCsp)).thenReturn(Stream.empty());
        when(defaultSolver.getSolutions(ternaryDomainCsp)).thenReturn(Stream.empty());
        when(defaultSolver.getSolutions(largeDomainCsp)).thenReturn(Stream.empty());

        solver.getSolutions(binaryDomainCsp).toList();
        solver.getSolutions(ternaryDomainCsp).toList();
        solver.getSolutions(largeDomainCsp).toList();
        // Mockito verifies that decompose was called with the expected maxDomainSize values
    }

    @Test
    void shouldApplyDecomposition_true() {
        assertThat(TreeDecompositionSolver.shouldApplyDecomposition(TREE_CSP, ORIGINAL_CSP)).isTrue();
    }

    @Test
    void shouldApplyDecomposition_false() {
        assertThat(TreeDecompositionSolver.shouldApplyDecomposition(EXPENSIVE_TREE_CSP, SMALL_ORIGINAL_CSP)).isFalse();
    }
}
