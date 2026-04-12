package org.jcsp.solver.tree;

import lombok.val;
import org.jcsp.TreeConstraintSatisfactionProblem;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
    TreeConstraintSatisfactionProblem australiaWithoutSA = new TreeConstraintSatisfactionProblem(
            Map.of(
                    WA, DOMAIN,
                    NT, DOMAIN,
                    Q, DOMAIN,
                    NSW, DOMAIN,
                    V, DOMAIN
            ),
            Set.of(
                    BinaryNotEqualsConstraint.builder().left(WA).right(NT).build(),
                    BinaryNotEqualsConstraint.builder().left(NT).right(Q).build(),
                    BinaryNotEqualsConstraint.builder().left(Q).right(NSW).build(),
                    BinaryNotEqualsConstraint.builder().left(NSW).right(V).build()));
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
        TreeConstraintSatisfactionProblem australiaWithoutSAAndInsufficientDomains = new TreeConstraintSatisfactionProblem(
                Map.of(
                        WA, DOMAIN_RED_ONLY,
                        NT, DOMAIN_RED_ONLY
                ),
                Set.of(
                        BinaryNotEqualsConstraint.builder().left(WA).right(NT).build()));
        assertThat(treeSolver.getSolution(australiaWithoutSAAndInsufficientDomains)).isEmpty();
    }

    @Test
    void makeArcConsistent_revises() {
        TreeConstraintSatisfactionProblem problem = new TreeConstraintSatisfactionProblem(
                Map.of(
                        WA, DOMAIN,
                        NT, DOMAIN_RED_ONLY
                ),
                Set.of(
                        BinaryNotEqualsConstraint.builder().left(WA).right(NT).build()));
        assertThat(treeSolver.makeArcConsistent(problem, WA, NT).get().getDomain(WA).get().stream().toList()).isEqualTo(List.of(GREEN));
    }
}
