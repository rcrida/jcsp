package org.jcsp.solver.tree;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.EnumDomain;
import org.jcsp.search.order.DefaultValueOrderer;
import org.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import org.jcsp.solver.tree.sorter.BFSTopologicalSorter;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jcsp.solver.AustraliaMapColouringTest.Colour.GREEN;
import static org.jcsp.solver.AustraliaMapColouringTest.Colour.RED;
import static org.jcsp.solver.AustraliaMapColouringTest.NSW;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.Q;
import static org.jcsp.solver.AustraliaMapColouringTest.V;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;

public class TreeSolverImplTest {

    public static Domain DOMAIN = new EnumDomain(EnumSet.of(RED, GREEN));
    public static Domain DOMAIN_RED_ONLY = new EnumDomain(EnumSet.of(RED));
    ConstraintSatisfactionProblem australiaWithoutSA = ConstraintSatisfactionProblem.builder()
            .variableDomain(WA, DOMAIN)
            .variableDomain(NT, DOMAIN)
            .variableDomain(Q, DOMAIN)
            .variableDomain(NSW, DOMAIN)
            .variableDomain(V, DOMAIN)
            .constraint(BinaryNotEqualsConstraint.builder().left(WA).right(NT).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(NT).right(Q).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(Q).right(NSW).build())
            .constraint(BinaryNotEqualsConstraint.builder().left(NSW).right(V).build())
            .build();
    TreeSolverImpl treeSolver = new TreeSolverImpl(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);

    @Test
    void getSolution() {
        val optionalSolution = treeSolver.getSolution(australiaWithoutSA);
        assertThat(optionalSolution).hasValueSatisfying(solution -> {
            assertThat(solution).isIn(
                    Assignment.of(Map.of(WA, RED, NT, GREEN, Q, RED, NSW, GREEN, V, RED)),
                    Assignment.of(Map.of(WA, GREEN, NT, RED, Q, GREEN, NSW, RED, V, GREEN))
            );
        });
    }

    @Test
    void getSolutions() {
        assertThat(treeSolver.getSolutions(australiaWithoutSA)).hasSize(2);
    }

    @Test
    void getSolution_inconsistent() {
        val australiaWithoutSAAndInsufficientDomains = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, DOMAIN_RED_ONLY)
                .variableDomain(NT, DOMAIN_RED_ONLY)
                .constraint(BinaryNotEqualsConstraint.builder().left(WA).right(NT).build())
                .build();
        assertThat(treeSolver.getSolution(australiaWithoutSAAndInsufficientDomains)).isEmpty();
    }

    @Test
    void makeArcConsistent_revises() {
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, DOMAIN)
                .variableDomain(NT, DOMAIN_RED_ONLY)
                .constraint(BinaryNotEqualsConstraint.builder().left(WA).right(NT).build())
                .build();
        assertThat(treeSolver.makeArcConsistent(problem, WA, NT).get().getDomain(WA).get().stream().toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void getSolutions_assertIsTree() {
        val emptyCsp = ConstraintSatisfactionProblem.builder().build();
        assertThatThrownBy(() -> treeSolver.getSolutions(emptyCsp))
                .isInstanceOf(AssertionError.class);
    }
}
