package org.jcsp.solver.tree.cutsetconditioning;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.constraints.nary.AllDiffConstraint;
import org.jcsp.constraints.nary.ExpressionConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.solver.EmptyTest;
import org.jcsp.solver.Solver;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jcsp.solver.tree.TreeSolverTest.AUSTRALIA_WITHOUT_SA;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CutsetConditioningSolverTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static final Domain DOMAIN = new IntRangeDomain(1, 9);
    static final Domain CONSTRAINED_DOMAIN = new IntRangeDomain(2, 9);
    static final Assignment ASSIGNMENT = Assignment.EMPTY;
    static final Variable T1 = VARIABLE_FACTORY.create("T1"); // t variables form a tree
    static final Variable T2 = VARIABLE_FACTORY.create("T2");
    static final Variable T3 = VARIABLE_FACTORY.create("T3");
    static final Variable T4 = VARIABLE_FACTORY.create("T4");
    static final Variable C = VARIABLE_FACTORY.create("C"); // c variable should be cutset
    static final ConstraintSatisfactionProblem CUTSET_CONDITIONING_PROBLEM = ConstraintSatisfactionProblem.builder()
            .variableDomain(T1, DOMAIN)
            .variableDomain(T2, DOMAIN)
            .variableDomain(T3, DOMAIN)
            .variableDomain(T4, DOMAIN)
            .variableDomain(C, DOMAIN)
            .constraint(BinaryNotEqualsConstraint.builder().left(C).right(T1).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(C).right(T2).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(C).right(T3).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(C).right(T4).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(T1).right(T2).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(T2).right(T3).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(T3).right(T4).build())
            .build();

    @Mock
    Solver cycleCutsetSolver;
    @Mock
    Solver treeSolver;
    CutsetConditioningSolver cutsetConditioningSolver;

    @BeforeEach
    void setUp() {
        cutsetConditioningSolver = new CutsetConditioningSolver(cycleCutsetSolver, treeSolver);
    }

    @Test
    void getSolutions_empty() {
        val emptyCsp = EmptyTest.problem();
        when(cycleCutsetSolver.getSolutions(emptyCsp)).thenReturn(Stream.of(Assignment.EMPTY));
        assertThat(cutsetConditioningSolver.getSolutions(emptyCsp)).containsExactly(Assignment.EMPTY);
    }

    @Test
    void getSolutions_treeProblem() {
        val treeCsp = AUSTRALIA_WITHOUT_SA;
        when(treeSolver.getSolutions(treeCsp)).thenReturn(Stream.of(ASSIGNMENT));
        assertThat(cutsetConditioningSolver.getSolutions(treeCsp)).containsExactly(ASSIGNMENT);
    }

    @Test
    void getSolutions_noComplexityImprovement() {
        val a = VARIABLE_FACTORY.create("A");
        val b = VARIABLE_FACTORY.create("B");
        val c = VARIABLE_FACTORY.create("C");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, DOMAIN)
                .variableDomain(b, DOMAIN)
                .variableDomain(c, DOMAIN)
                .constraint(AllDiffConstraint.builder().variables(Set.of(a, b, c)).build())
                .build();
        val assignment = Assignment.builder().value(a, 1).build();
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
                .constraint(BinaryNotEqualsConstraint.builder().left(T1).right(T2).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(T2).right(T3).build())
                .constraint(BinaryNotEqualsConstraint.builder().left(T3).right(T4).build())
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

        val smallDomain = new IntRangeDomain(1, 1);
        // when 1 is removed from T1 domain there will be nothing left, hence no solution
        assertThat(cutsetConditioningSolver.getSolutions(CUTSET_CONDITIONING_PROBLEM.toBuilder().variableDomain(T1, smallDomain).build())).isEmpty();
    }

    @Test
    void getSolutions_noTreeAtAll() {
        val a = VARIABLE_FACTORY.create("A");
        val b = VARIABLE_FACTORY.create("B");
        val c = VARIABLE_FACTORY.create("C");
        val d = VARIABLE_FACTORY.create("D");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, DOMAIN)
                .variableDomain(b, DOMAIN)
                .variableDomain(c, DOMAIN)
                .variableDomain(d, DOMAIN)
                .constraint(BinaryNotEqualsConstraint.builder().left(c).right(d).build())
                .constraint(ExpressionConstraint.builder().variables(Set.of(a, b, c)).expression(assignment -> {
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
}
