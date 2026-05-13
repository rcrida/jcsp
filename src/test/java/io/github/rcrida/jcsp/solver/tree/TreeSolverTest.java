package io.github.rcrida.jcsp.solver.tree;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.tree.sorter.BFSTopologicalSorter;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.Colour.GREEN;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.Colour.RED;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.NSW;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.NT;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.Q;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.V;
import static io.github.rcrida.jcsp.solver.AustraliaMapColouringTest.WA;

public class TreeSolverTest {

    public static Domain DOMAIN = new EnumDomain(EnumSet.of(RED, GREEN));
    public static Domain DOMAIN_RED_ONLY = new EnumDomain(EnumSet.of(RED));
    public static ConstraintSatisfactionProblem AUSTRALIA_WITHOUT_SA = ConstraintSatisfactionProblem.builder()
            .variableDomain(WA, DOMAIN)
            .variableDomain(NT, DOMAIN)
            .variableDomain(Q, DOMAIN)
            .variableDomain(NSW, DOMAIN)
            .variableDomain(V, DOMAIN)
            .notEqualsConstraint(WA, NT)
            .notEqualsConstraint(NT, Q)
            .notEqualsConstraint(Q, NSW)
            .notEqualsConstraint(NSW, V)
            .build();
    TreeSolver treeSolver = new TreeSolver(BFSTopologicalSorter.INSTANCE, DefaultValueOrderer.INSTANCE, TreeUnassignedVariableSelector.Factory.INSTANCE);

    @Test
    void getSolution() {
        val optionalSolution = treeSolver.getSolution(AUSTRALIA_WITHOUT_SA);
        assertThat(optionalSolution).hasValueSatisfying(solution -> {
            assertThat(solution).isIn(
                    Assignment.of(Map.of(WA, RED, NT, GREEN, Q, RED, NSW, GREEN, V, RED)),
                    Assignment.of(Map.of(WA, GREEN, NT, RED, Q, GREEN, NSW, RED, V, GREEN))
            );
        });
    }

    @Test
    void getSolutions() {
        assertThat(treeSolver.getSolutions(AUSTRALIA_WITHOUT_SA)).hasSize(2);
    }

    @Test
    void getSolution_inconsistent() {
        val australiaWithoutSAAndInsufficientDomains = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, DOMAIN_RED_ONLY)
                .variableDomain(NT, DOMAIN_RED_ONLY)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(treeSolver.getSolution(australiaWithoutSAAndInsufficientDomains)).isEmpty();
    }

    @Test
    void makeArcConsistent_revises() {
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, DOMAIN)
                .variableDomain(NT, DOMAIN_RED_ONLY)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(treeSolver.makeArcConsistent(problem, WA, NT).get().getDomain(WA).toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void getSolutions_assertIsTree() {
        val emptyCsp = ConstraintSatisfactionProblem.builder().build();
        assertThatThrownBy(() -> treeSolver.getSolutions(emptyCsp))
                .isInstanceOf(AssertionError.class);
    }
}
