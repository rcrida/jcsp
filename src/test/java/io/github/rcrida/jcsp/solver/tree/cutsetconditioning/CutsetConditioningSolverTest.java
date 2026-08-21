package io.github.rcrida.jcsp.solver.tree.cutsetconditioning;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.EmptyTest;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverCancelledException;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.rcrida.jcsp.solver.tree.TreeSolverTest.AUSTRALIA_WITHOUT_SA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CutsetConditioningSolverTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static final Domain DOMAIN = IntRangeDomain.of(1, 9);
    static final Domain CONSTRAINED_DOMAIN = IntRangeDomain.of(2, 9);
    static final Assignment ASSIGNMENT = Assignment.empty();
    static final Variable<Integer> T1 = VARIABLE_FACTORY.create("T1"); // t variables form a tree
    static final Variable<Integer> T2 = VARIABLE_FACTORY.create("T2");
    static final Variable<Integer> T3 = VARIABLE_FACTORY.create("T3");
    static final Variable<Integer> T4 = VARIABLE_FACTORY.create("T4");
    static final Variable<Integer> C = VARIABLE_FACTORY.create("C"); // c variable should be cutset
    static final ConstraintSatisfactionProblem CUTSET_CONDITIONING_PROBLEM = ConstraintSatisfactionProblem.builder()
            .variableDomain(T1, DOMAIN)
            .variableDomain(T2, DOMAIN)
            .variableDomain(T3, DOMAIN)
            .variableDomain(T4, DOMAIN)
            .variableDomain(C, DOMAIN)
            .notEqualsConstraint(C, T1)
            .notEqualsConstraint(C, T2)
            .notEqualsConstraint(C, T3)
            .notEqualsConstraint(C, T4)
            .notEqualsConstraint(T1, T2)
            .notEqualsConstraint(T2, T3)
            .notEqualsConstraint(T3, T4)
            .build();

    @Mock
    Solver cycleCutsetSolver;
    @Mock
    Solver treeSolver;
    CutsetConditioningSolver cutsetConditioningSolver;

    @BeforeEach
    void setUp() {
        cutsetConditioningSolver = CutsetConditioningSolver.builder().inner(cycleCutsetSolver).treeSolver(treeSolver).build();
    }

    @Test
    void getSolutions_empty() {
        val emptyCsp = EmptyTest.problem();
        when(cycleCutsetSolver.getSolutions(emptyCsp)).thenReturn(Stream.of(Assignment.empty()));
        assertThat(cutsetConditioningSolver.getSolutions(emptyCsp)).containsExactly(Assignment.empty());
    }

    @Test
    void getSolutions_treeProblem() {
        val treeCsp = AUSTRALIA_WITHOUT_SA;
        when(treeSolver.getSolutions(treeCsp)).thenReturn(Stream.of(ASSIGNMENT));
        assertThat(cutsetConditioningSolver.getSolutions(treeCsp)).containsExactly(ASSIGNMENT);
    }

    @Test
    void getSolutions_noComplexityImprovement() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, DOMAIN)
                .variableDomain(b, DOMAIN)
                .variableDomain(c, DOMAIN)
                .allDiffConstraint(Set.of(a, b, c))
                .build();
        val assignment = Assignment.builder().value(a, 1).build();
        when(cycleCutsetSolver.getSolutions(csp)).thenReturn(Stream.of(assignment));
        assertThat(cutsetConditioningSolver.getSolutions(csp)).containsExactly(assignment);
    }

    @Test
    void getSolutions_cutsetTooLargeToEnumerate_declinesDecomposition() {
        // A complete graph (every pair directly constrained) grows its tree component to just 2
        // nodes regardless of size: decomposeCsp's BFS adds the first two visited nodes (each has
        // fewer than 2 tree-neighbors so far), then every subsequent node sees both of those as
        // tree-neighbors simultaneously and is excluded. 10 variables, domain size 10: cutset size
        // 8, so 10^8 = 100,000,000 cutset assignments -- comfortably over MAX_CUTSET_ASSIGNMENTS
        // (1,000,000), exercising the new absolute cap specifically (a treeSize-of-2 decomposition
        // would already fail the pre-existing relative complexity check regardless of cutset size,
        // so this alone wouldn't prove the new cap is doing anything without the cap in place).
        List<Variable<Integer>> vars = new ArrayList<>();
        for (int i = 0; i < 10; i++) vars.add(VARIABLE_FACTORY.create("V" + i));
        Domain tenValues = IntRangeDomain.of(0, 9);
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> v : vars) builder = builder.variableDomain(v, tenValues);
        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                builder = builder.notEqualsConstraint(vars.get(i), vars.get(j));
            }
        }
        val csp = builder.build();
        val assignment = Assignment.builder().value(vars.get(0), 1).build();
        when(cycleCutsetSolver.getSolutions(csp)).thenReturn(Stream.of(assignment));
        assertThat(cutsetConditioningSolver.getSolutions(csp)).containsExactly(assignment);
    }

    @Test
    void getSolutions_complexityImprovement() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        when(treeSolver.getSolutions(cutset)).thenReturn(Stream.of(cutsetAssignment));

        val tree = ConstraintSatisfactionProblem.builder()
                .variableDomain(T1, CONSTRAINED_DOMAIN)
                .variableDomain(T2, CONSTRAINED_DOMAIN)
                .variableDomain(T3, CONSTRAINED_DOMAIN)
                .variableDomain(T4, CONSTRAINED_DOMAIN)
                .notEqualsConstraint(T1, T2)
                .notEqualsConstraint(T2, T3)
                .notEqualsConstraint(T3, T4)
                .build();
        val treeAssignment = Assignment.of(Map.of(T1, 2, T2, 3, T3, 4, T4, 5));
        when(treeSolver.getSolutions(tree)).thenReturn(Stream.of(treeAssignment));
        assertThat(cutsetConditioningSolver.getSolutions(CUTSET_CONDITIONING_PROBLEM)).containsExactly(cutsetAssignment.merge(treeAssignment));
    }

    @Test
    void getSolutions_domainBecomesEmpty() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        when(treeSolver.getSolutions(cutset)).thenReturn(Stream.of(cutsetAssignment));

        val smallDomain = IntRangeDomain.of(1, 1);
        // when 1 is removed from T1 domain there will be nothing left, hence no solution
        assertThat(cutsetConditioningSolver.getSolutions(CUTSET_CONDITIONING_PROBLEM.toBuilder().variableDomain(T1, smallDomain).build())).isEmpty();
    }

    @Test
    void getSolutions_noTreeAtAll() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        Variable<Integer> d = VARIABLE_FACTORY.create("D");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, DOMAIN)
                .variableDomain(b, DOMAIN)
                .variableDomain(c, DOMAIN)
                .variableDomain(d, DOMAIN)
                .notEqualsConstraint(c, d)
                .constraint(PredicateConstraint.builder().variables(Set.of(a, b, c)).predicate(assignment -> {
                    val A = (int) assignment.getValue(a).get();
                    val B = (int) assignment.getValue(b).get();
                    val C = (int) assignment.getValue(c).get();
                    return A + B == C;
                }).build())
                .build();
        val assignment = Assignment.of(Map.of(a, 1, b, 2, c, 3));
        when(cycleCutsetSolver.getSolutions(csp)).thenReturn(Stream.of(assignment));
        assertThat(cutsetConditioningSolver.getSolutions(csp)).containsExactly(assignment);
    }

    @Test
    void getSolution_treeProblem() {
        val treeCsp = AUSTRALIA_WITHOUT_SA;
        when(treeSolver.getSolution(treeCsp)).thenReturn(Optional.of(ASSIGNMENT));
        assertThat(cutsetConditioningSolver.getSolution(treeCsp)).contains(ASSIGNMENT);
    }

    @Test
    void getSolution_complexityImprovement() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        // cutset is a tree so CutsetConditioningSolver.getSolutions() routes to treeSolver.getSolutions()
        when(treeSolver.getSolutions(cutset)).thenReturn(Stream.of(cutsetAssignment));

        val tree = ConstraintSatisfactionProblem.builder()
                .variableDomain(T1, CONSTRAINED_DOMAIN)
                .variableDomain(T2, CONSTRAINED_DOMAIN)
                .variableDomain(T3, CONSTRAINED_DOMAIN)
                .variableDomain(T4, CONSTRAINED_DOMAIN)
                .notEqualsConstraint(T1, T2)
                .notEqualsConstraint(T2, T3)
                .notEqualsConstraint(T3, T4)
                .build();
        val treeAssignment = Assignment.of(Map.of(T1, 2, T2, 3, T3, 4, T4, 5));
        when(treeSolver.getSolutions(tree)).thenReturn(Stream.of(treeAssignment));
        assertThat(cutsetConditioningSolver.getSolution(CUTSET_CONDITIONING_PROBLEM))
                .contains(cutsetAssignment.merge(treeAssignment));
    }

    @Test
    void getSolution_cutsetAssignmentCountExceedsBatchSize_processesInBoundedBatches() {
        // One more than CUTSET_BATCH_SIZE (10,000): the first batch fills to capacity (exercising
        // "batch.size() < CUTSET_BATCH_SIZE" going false), then the second batch's single element
        // exhausts the source (exercising "hasNext()" going false) -- both deterministically, no
        // cancellation/threading needed. Real memory safety at this scale isn't something a unit
        // test can assert directly, but is worth recording: two earlier, rejected designs (a
        // Stream#takeWhile gate before .parallel(), and a per-element cancellation check inside
        // flatMap) both crashed with a real OutOfMemoryError under this exact shape when the source
        // ran into the millions, confirming batching genuinely differs from a re-labeled version of
        // the same unsafe pipeline, not just moving the same bug elsewhere.
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        when(treeSolver.getSolutions(cutset)).thenAnswer(invocation ->
                IntStream.rangeClosed(1, 10_001).mapToObj(i -> Assignment.builder().value(C, 1).build()));

        val tree = ConstraintSatisfactionProblem.builder()
                .variableDomain(T1, CONSTRAINED_DOMAIN)
                .variableDomain(T2, CONSTRAINED_DOMAIN)
                .variableDomain(T3, CONSTRAINED_DOMAIN)
                .variableDomain(T4, CONSTRAINED_DOMAIN)
                .notEqualsConstraint(T1, T2)
                .notEqualsConstraint(T2, T3)
                .notEqualsConstraint(T3, T4)
                .build();
        // thenAnswer, not thenReturn(Stream.empty()): getSolutions(tree) is invoked once per
        // cutset assignment (10,001 times here), and a Stream can only be traversed once, so a
        // single shared instance would throw on the second reuse.
        when(treeSolver.getSolutions(tree)).thenAnswer(invocation -> Stream.<Assignment>empty());
        assertThat(cutsetConditioningSolver.getSolution(CUTSET_CONDITIONING_PROBLEM)).isEmpty();
    }

    @Test
    void getSolution_domainBecomesEmpty() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        when(treeSolver.getSolutions(cutset)).thenReturn(Stream.of(cutsetAssignment));

        val smallDomain = IntRangeDomain.of(1, 1);
        assertThat(cutsetConditioningSolver.getSolution(
                CUTSET_CONDITIONING_PROBLEM.toBuilder().variableDomain(T1, smallDomain).build())).isEmpty();
    }

    @Test
    void getSolution_noTreeAtAll() {
        Variable<Integer> a = VARIABLE_FACTORY.create("A");
        Variable<Integer> b = VARIABLE_FACTORY.create("B");
        Variable<Integer> c = VARIABLE_FACTORY.create("C");
        Variable<Integer> d = VARIABLE_FACTORY.create("D");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, DOMAIN)
                .variableDomain(b, DOMAIN)
                .variableDomain(c, DOMAIN)
                .variableDomain(d, DOMAIN)
                .notEqualsConstraint(c, d)
                .constraint(PredicateConstraint.builder().variables(Set.of(a, b, c)).predicate(assignment -> {
                    val A = (int) assignment.getValue(a).get();
                    val B = (int) assignment.getValue(b).get();
                    val C = (int) assignment.getValue(c).get();
                    return A + B == C;
                }).build())
                .build();
        val assignment = Assignment.of(Map.of(a, 1, b, 2, c, 3));
        when(cycleCutsetSolver.getSolution(csp)).thenReturn(Optional.of(assignment));
        assertThat(cutsetConditioningSolver.getSolution(csp)).contains(assignment);
    }

    @Test
    void getSolutions_cancelledBeforeCutsetEnumeration_truncatesSilently() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        doReturn(Stream.of(cutsetAssignment)).when(treeSolver).getSolutions(cutset);

        Cancellation cancellation = new Cancellation();
        cancellation.cancel();
        val cancellable = CutsetConditioningSolver.builder()
                .inner(cycleCutsetSolver).treeSolver(treeSolver).cancellation(cancellation).build();
        assertThat(cancellable.getSolutions(CUTSET_CONDITIONING_PROBLEM)).isEmpty();
    }

    @Test
    void getSolution_cancelledWithNoSolutionFound_throwsSolverCancelledException() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        doReturn(Stream.of(cutsetAssignment)).when(treeSolver).getSolutions(cutset);

        Cancellation cancellation = new Cancellation();
        cancellation.cancel();
        val cancellable = CutsetConditioningSolver.builder()
                .inner(cycleCutsetSolver).treeSolver(treeSolver).cancellation(cancellation).build();
        assertThatThrownBy(() -> cancellable.getSolution(CUTSET_CONDITIONING_PROBLEM))
                .isInstanceOf(SolverCancelledException.class);
    }

    @Test
    void getSolution_propagatesRuntimeExceptionFromTreeSolver() {
        val cutset = ConstraintSatisfactionProblem.builder()
                .variableDomain(C, DOMAIN)
                .build();
        val cutsetAssignment = Assignment.builder().value(C, 1).build();
        var boom = new RuntimeException("boom");
        doThrow(boom).when(treeSolver).getSolutions(any());
        doReturn(Stream.of(cutsetAssignment)).when(treeSolver).getSolutions(cutset);
        assertThatThrownBy(() -> cutsetConditioningSolver.getSolution(CUTSET_CONDITIONING_PROBLEM)).isSameAs(boom);
    }
}
